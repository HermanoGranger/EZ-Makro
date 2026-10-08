# ADR-008: Vorkonfigurierte BLS-Lebensmittel-Daten (Seed)

## Status

Angenommen — Umsetzung in `tools/seed/`, `:core:data` (`food/seed`) und
`:core:database` (`FoodItemDao.upsertSeed`)

## Kontext

Die App ist offline-first, aber bei Erstinstallation ohne Internet leer: die
Nutzer:in braucht mindestens ein Lebensmittel, um Tagebucheinträge anzulegen.
OpenFoodFacts (ODbL, Share-Alike-Pflicht) und USDA (nur Englisch) sind als
quelloffene Grunddatenquelle ungeeignet. Gefragt ist eine kuratierte,
vegane Grundauswahl (~250 häufige Lebensmittel), die mit der App ausgeliefert
wird — ohne Nutzerdaten zu überschreiben und ohne zweite Datensource in der
Runtime.

## Entscheidung

- **Quelle:** Bundeslebensmittelschlüssel (BLS) 4.0 des Max Rubner-Instituts,
  Lizenz CC BY 4.0 (Namensnennung + DOI). Rohdaten werden **nicht** in das
  Repository eingebunden; ein Dev-seitiges Python-Tool (`tools/seed/`,
  stdlib-XLSX-Parser) liest die BLS-Excel und erzeugt ein kuratiertes,
  versioniertes JSON-Asset (`core/data/src/main/assets/seed/food_seed_v1.json`,
  251 vegane Lebensmittel). Auswahl und Benennungen sind im Tool explizit
  reviewbar (`selection.py` mit Handpicks + Auto-Kategorien + `NAME_OVERRIDES`).
- **Seeding zur Laufzeit:** `BlsFoodSeeder` wendet das Asset bei jedem
  App-Start an — idempotent über einen Marker
  (`seed_food_version` im `SettingsDataStore`). Gleiche Version = No-Op;
  neue Version (App-Update) = Re-Seed.
- **Nutzerdaten-Schutz (Typsystem/SQL, nicht Konvention):**
  `FoodItemDao.upsertSeed` inserted per `id` und updatet in der
  `ON CONFLICT`-Klausel **nur** Zeilen mit `cached_status = 'ORIGINAL' AND
  is_user_created = 0` (ADR-004-Status wird damit auch für Seeds erzwungen).
  Edits (`USER_MODIFIED`), Favoriten-Flag, `last_used_at`, Barcode und
  Serving-Spalten werden nie angerührt; `orig_*`-Snapshot = eigene Werte,
  damit „Zurücksetzen auf Original" funktioniert.
- **Backup-Kopplung:** Der Marker ist ein normaler String-Setting und wird
  über den generischen Snapshot (A10) exportiert. Nach einem
  **REPLACE**-Import setzt `BackupImporter` den Marker zurück (best effort,
  Warnung statt Abbruch), damit Seeds in die wiederhergestellte DB
  zurückkehren.
- **Attribution in der UI:** Settings-Hub zeigt „Datenquellen" mit
  BLS-Nennung (CC BY 4.0 + DOI) und OpenFoodFacts (ODbL).
- **Ausfallverhalten:** Asset-/Schreibfehler werden geloggt und als
  `DataError.Local` zurückgegeben; die App startet ohne Seeds statt zu
  crashen. Marker wird erst nach vollständigem Apply geschrieben — ein
  abgebrochener Lauf.retryet beim nächsten Start.

## Alternativen

- **OpenFoodFacts als Seed-Quelle:** ODbL erzwingt Share-Alike auf Derivate —
  abgelehnt.
- **USDA FoodData Central:** nur englische Bezeichnungen — abgelehnt.
- **Seeds als normale `is_user_created`-Rows:** Would break
  „eigene Lebensmittel"-Trennung und FTS/Filter.
- **Nur Marker ohne SQL-Guard:** Re-Seed würde Nutzer-Edits überschreiben.

## Trade-offs

- Ein gelöschter Seed kehrt beim nächsten Versions-Bump zurück (akzeptiert,
  dokumentiert).
- Kuratierte Auswahl statt Vollständigkeit; BLS-Namensjargon wird per
  Override-Tabelle normalisiert (Pflege beim nächsten Bump).
- REPLACE-Import braucht Neustart-Logik für Seeds (Marker-Reset).
