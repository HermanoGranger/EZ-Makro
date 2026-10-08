# tools/seed — BLS-Seed-Pipeline

Dev-Tooling, das das versionierte Seed-Asset
`core/data/src/main/assets/seed/food_seed_v1.json` erzeugt: 251 vegane
Basis-Lebensmittel aus dem Bundeslebensmittelschlüssel (BLS 4.0, CC BY 4.0,
ADR-008). Es läuft ausschließlich auf dem Entwicklerrechner — **nie auf dem
Gerät**. Die App liest nur die fertige JSON-Datei aus den Assets.

Ziel: eine kuratierte, reproduzierbare und reviewbare Auswahl statt der
kompletten (vegan ungefilterten) BLS-Datenbank.

## Überblick

```
fetch_bls.py ──▶ cache/BLS_4_0_.../BLS_4_0_Daten_2025_DE.xlsx   (14 MB, gitignored)
                        │
bls_extract.py ─────────┴──▶ cache/bls_foods.csv                  (Kopie, gitignored)
                                          │
selection.py (Kurationsregeln) ───────────┤
                                          ▼
                              build_seed.py ──▶ assets/seed/food_seed_v1.json   (committed)
```

| Datei | Aufgabe |
| --- | --- |
| `fetch_bls.py` | Lädt das offizielle BLS-4.0-ZIP und entpackt die XLSX nach `cache/`. |
| `bls_extract.py` | Liest die XLSX mit stdlib (`zipfile` + `xml.etree`, **kein `openpyxl`**) und schreibt `cache/bls_foods.csv`. Spalten werden über Header-Präfixe gefunden, nicht über feste Indizes. |
| `selection.py` | Die **Kurationsregeln**: welche Codes, welche Keywords, welche Namen, Vegan-Filter. Einzige Datei, die beim Pflegen geändert wird. |
| `build_seed.py` | Wendet die Regeln an, validiert und schreibt das Asset. |

Alle Skripte laufen mit einem blanken Python 3 (getestet mit 3.12) und brauchen
außer der Standardbibliothek nichts. `cache/` und `__pycache__/` sind
gitignored — Rohdaten werden **nie** committet.

## Pipeline

```bash
# 1. BLS-Excel laden → tools/seed/cache/ (nur einmal, braucht Internet)
python3 tools/seed/fetch_bls.py

# 2. XLSX → CSV (stdlib-Parser)
python3 tools/seed/bls_extract.py

# 3. Auswahl prüfen, ohne zu schreiben (Report + Warnungen/Fehler)
python3 tools/seed/build_seed.py --check

# Vorschau: jede Auswahl mit Code, Anzeigename und Nährwerten
python3 tools/seed/build_seed.py --list

# 4. Asset schreiben (Default ohne --check)
python3 tools/seed/build_seed.py
```

`--check` und `--list` verändern nichts. `build_seed.py` gibt außerdem ein Diff
gegen das aktuelle Asset aus (`+n / -n` mit den betroffenen IDs), damit man
sieht, was sich gegenüber dem committeten Stand ändert.

## Auswahl-Logik (`build_seed.py:select`)

Zwei Mechanismen, in fester Reihenfolge:

1. **`HANDPICKED`** (`selection.py`) — explizite BLS-Codes pro Kategorie,
   werden **zuerst** beansprucht und nie überschrieben. Für alles, wo
   Keyword-Matching unzuverlässig ist (Brot, Getreide, Nudeln, Säfte, Öle,
   Getränke, Gewürze). Jeder Code wird geprüft:
   - existiert in BLS 4.0, sonst Fehler;
   - ist vegan, sonst Fehler;
   - nicht doppelt vergeben, sonst Fehler.
   Fehler brechen den Build ab (`exit 1`).
2. **`CATEGORIES`** (`selection.py`) — Keyword-Matching mit **Budget pro
   Keyword** für den langen Schwanz Obst/Gemüse. `("paprika", 3)` nimmt bis zu
   3 Treffer, `("apfel", 1)` den besten. Kandidaten werden gerankt nach:
   kürzester Name → ohne Preparation-Marker → alphabetisch. Das ergibt
   typischerweise die „roh"-Variante.

### Filter und Normalisierung

- **Vegan** (`is_vegan`): `NON_VEGAN_PATTERN` schließt Milch/Butter/Käse/
  Honig/Fleisch/Fisch/Ei usw. per Regex aus; `VEGAN_ALLOW_PATTERN` rettet
  Ausnahmen **zuerst** (Kokosmilch, Erdnussbutter, Butterpilz …).
- **`PREP_MARKERS`**: verarbeitete Varianten (Saft, Mus, Kuchen, gegart,
  Konserve …) werden beim Keyword-Ranking nach hinten sortiert und nur
  genommen, wenn es nichts Besseres gibt.
- **`display_name()`**: BLS-Jargon → App-Name. `NAME_OVERRIDES` ist die
  explizite Tabelle; fehlt ein Code, wird bei frischem Obst/Gemüse nur
  `", roh"` abgeschnitten.
- **`validate()`**: doppelte IDs, doppelte Namen, fehlende kcal, gesetzter
  Barcode, negative Werte → Fehler.

## Maintainen

Alle inhaltlichen Anpassungen passieren in **`selection.py`**. Danach immer
Schritt 3 (`--check`) und zum Schluss Schritt 4 laufen lassen.

| Ziel | Vorgehen |
| --- | --- |
| Lebensmittel hinzufügen | Code in `HANDPICKED` unter die passende Kategorie setzen (mit Kommentar-Namen wie in den bestehenden Zeilen). |
| Handpicked-Eintrag entfernen | Die Code-Zeile löschen. |
| Auto-Treffer (Keyword) entfernen | Keyword oder Budget in `CATEGORIES` anpassen. Es gibt aktuell **keine `EXCLUDED`-Liste** — einen einzelnen Keyword-Treffer kann man nicht direkt blocken (siehe „Bekannte Lücken"). |
| Anzeigenamen ändern | Code in `NAME_OVERRIDES` eintragen/ändern. |
| Kategorie umhängen | Code in den gewünschten `HANDPICKED`-Block verschieben. |

### BLS-Code nachschlagen

```bash
python3 tools/seed/bls_extract.py              # erzeugt cache/bls_foods.csv
# dann in tools/seed/cache/bls_foods.csv nach dem Namen suchen,
# oder alle aktuellen Auswahlen mit Code anzeigen:
python3 tools/seed/build_seed.py --list
```

### Neue BLS-Version einspielen

Die Download-URL (`ZIP_URL`) und der Zielpfad (`XLSX`) stehen in
`bls_extract.py`. `fetch_bls.py` lädt nur, wenn die Dateien im `cache/` noch
nicht existieren — für ein Update also das alte ZIP/XLSX aus
`tools/seed/cache/` löschen oder die Pfade anpassen.

1. `ZIP_URL`/`XLSX` in `bls_extract.py` auf die neue Version setzen, dann
   `fetch_bls.py` und `bls_extract.py` ausführen. Header-Abweichungen fallen
   beim Parsen als Fehler auf.
2. `build_seed.py --check` — fehlende/umbenannte Codes erscheinen als Fehler
   in der Handpicked-Prüfung und müssen in `selection.py` korrigiert werden.
3. Namen/Keywords bei Bedarf nachziehen, dann Asset neu schreiben.

## Ausliefern eines geänderten Assets

Eine geänderte JSON allein löst **kein** Re-Seed auf Installierten Geräten aus.
`BlsFoodSeeder` vergleicht die `seedVersion` im Asset mit dem Marker
`seed_food_version` im `SettingsDataStore` und seedet nur bei Abweichung
(bzw. beim ersten Start).

Zum Ausrollen einer inhaltlichen Änderung:

1. In `build_seed.py` **`SEED_VERSION` erhöhen**. Dateiname
   (`food_seed_v1.json`) und Pfad in `AssetSeedFoodDataSource` können bleiben.
2. Asset neu schreiben (Schritt 4).
3. Tests nachziehen: `BlsFoodSeederTest` prüft feste Werte (Food-Anzahl,
   `seedVersion`, erster Code) und muss auf die neue Version angepasst werden.
4. Gates: `./gradlew build detekt ktlintCheck`.

Der `upsertSeed`-Schutz in `FoodItemDao` stellt dabei sicher, dass
`UserModified`-Einträge und selbst angelegte Lebensmittel **nie** automatisch
überschrieben werden.

## Tests

- `BlsFoodSeederTest` (in `core:data`): prüft u. a. das geparste
  Bundle-Asset (Anzahl, `seedVersion`, Lizenz, erster Code).
- `python3 tools/seed/build_seed.py --check`: schnellster Regressions-Check
  der Auswahlregeln (Warnungen = fehlende Keywords, Fehler = ungültige Codes).

## Bekannte Lücken / mögliche Erweiterungen

- Keine `EXCLUDED`-Liste: einzelne automatisch per Keyword gewählte Einträge
  lassen sich nicht gezielt ausschließen (Workaround: Keyword/Budget ändern).
- `display_name()` schneidet nur bei Obst/Gemüse `", roh"` ab; alles andere
  braucht einen `NAME_OVERRIDES`-Eintrag.
- `fetch_bls.py` lädt nur, wenn im `cache/` noch nichts liegt (siehe „Neue
  BLS-Version einspielen"); die Version-URL steht fest in `bls_extract.py`.

## Lizenz-Regel

BLS-Daten stehen unter **CC BY 4.0**. Nur das kuratierte Derivat als Asset
committen und dabei die Nennung (Max Rubner-Institut + DOI
`10.25826/Data20251217-134202-0`) beibehalten — die Attribution ist im Asset
(`source`-Block) und in den App-Einstellungen sichtbar. **Niemals** die
Rohdaten committen (`tools/seed/cache/` ist gitignored).
