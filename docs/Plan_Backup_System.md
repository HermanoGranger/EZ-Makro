# Plan — Backup-System (JSON Export/Import)

**Status:** Planung abgeschlossen (Revision 2), Freigabe für WP1 durch den Nutzer.
**Stand:** 2026-10-06 (Revision 2; Code-Basis weiterhin Commit `0916d21`).
**Bezug:** `docs/architecture.md`, `docs/coding-style.md`, `docs/domain-model.md`,
`docs/definition-of-done.md`, `docs/roadmap.md`, `docs/Scope_und_Features.md`,
`docs/Bugfix_Plan.md`, `docs/adr/ADR-002-offline-first-room.md`,
`docs/adr/ADR-004-off-eintragsstatus.md`.

**Änderungen in Revision 2 (Nutzerentscheidungen + Review-Korrekturen):**

- **A5:** Favoriten (`is_favorite = 1`) werden **immer** exportiert.
- **A9:** **Ein** Zahnrad reicht — das vorhandene Ziele-Zahnrad der
  Diary-TopAppBar öffnet künftig die Settings-Seite (Hub mit „Tagesziele" und
  „Backup"); kein zweites Icon, kein neues Drawable.
- **A18:** `ImportReport` zählt Duplikat-Übersprünge separat.
- **A19:** Known-IDs-Regeln für Validierung/Referenzbereinigung + DB-freie
  Rezeptvalidierung (Review-Befund 🔴), MERGE-Idempotenz für
  `recipe_ingredients` (Review-Befund 🔴), `BackupLocalDataSource` lebt in
  `:core:data` (Review-Befund 🔴), explizite JUnit-Engines für WP3,
  `app/build.gradle.kts` in WP4, Doku-Zitate korrigiert, DTOs nach
  `<Model>Dto`-Konvention.
- **Kein Passwort** wurde bestätigt — die „optional passwortverschlüsselt"-
  Zusage in `docs/scope.md:44`, `docs/Scope_und_Features.md:72` und
  `docs/roadmap.md:22` wird in WP5 gestrichen (siehe §7).

Dieser Plan ist in fünf Arbeitspakete (WP1–WP5) zerlegt. **Jedes WP ist ein
eigenständig prüfbares, bearbeitbares und committbares Paket.** Reihenfolge
einhalten: WP2 baut auf WP1 auf, WP3 auf WP2, WP4 auf WP3, WP5 auf WP4.

Phase 12 des Phasenplans (`docs/Vollstaendiger_Implementierungsplan.md:152`).
Vom Phasenplan abweichend: **keine Verschlüsselung** (Entscheidung des Nutzers).

---

## 0. Ablauf je WP (verbindlich)

Dieses Muster gilt für **alle** fünf WPs und ist nicht verhandelbar:

1. Der Agent-Prompt aus [§8](#8-agent-prompts-pro-wp) wird in ein **neues
   Agent-Fenster** kopiert und dort ausgeführt.
2. Der Agent arbeitet **ausschließlich das eine WP** ab, verifiziert mit dem
   lokalen Gate (`./gradlew build`, `./gradlew detekt`, `./gradlew ktlintCheck`
   plus die WP-spezifischen Tests) und meldet: geänderte/angelegte Dateien,
   Testergebnis, Abweichungen vom Plan, offene Punkte.
3. Der Agent **committet nicht** (AGENTS.md: „Niemals von dir aus committen").
4. Der **Nutzer nimmt ab** (Abnahme, ggf. mit Korrektur-Schleife).
5. Erst nach der Abnahme committet der Agent auf expliziten Befehl
   `Commit WP x` — mit dem im Plan hinterlegten **Commit-Vorschlag** und dem
   Nachtrag der Statuszeile in [§4](#4-wp-uebersicht).
6. **Erst danach** wird das nächste WP in einem neuen Fenster gestartet.

Ein WP gilt erst mit bestandenem Gate **und** Abnahme als abgeschlossen; erst
dann startet das nächste.

---

## 1. Kontext und Befund

### 1.1 Was heute schon existiert

| Fähigkeit | Ort |
|---|---|
| Room-Datenbank, 6 Tabellen, Schema-Version 9 | `core/database/.../EZDatabase.kt` |
| Manuelle Migrationen 1→9, `fallbackToDestructiveMigration()` | `core/database/.../migration/Migrations.kt`, `di/DatabaseModule.kt:26` |
| FTS4-Volltextsuche mit Content-Sync-Triggern auf `food_items` | `core/database/.../entity/FoodItemEntity.kt` (`FoodFtsEntity`), Schema-JSON `schemas/…/9.json` |
| Settings (5 Goal-Keys) in DataStore | `core/data/.../datastore/SettingsDataStore.kt`, `goal/DataStoreGoalRepository.kt` |
| Settings-Screen als Vorbild für MVI inkl. `UiText` | `feature/diary/presentation/.../goals/` (`GoalsScreen/State/Action/Event/ViewModel.kt`, `navigation/Goals.kt`) |
| Navigation: Bottom-Bar (3 Tabs), Goal-Route aus der Diary-TopAppBar | `app/.../MainActivity.kt`, `feature/diary/.../navigation/DiaryRoutes.kt`, `.../day/DiaryDayScreen.kt:259-280` |

### 1.2 Der Schlüsselbefund

**Es existiert kein Export, kein Import, kein SAF-Zugriff und kein
`FileProvider`** — im gesamten Repo weder `ACTION_CREATE_DOCUMENT`/
`ACTION_OPEN_DOCUMENT`/`ZipOutputStream`/`VACUUM INTO`/`FileProvider` noch
**ungefilterte** Volltabellen-Lesungen für `recipe_ingredients`,
`diary_entries` und `weight_logs` (diese drei DAOs haben keine `SELECT *`-
Abfrage; `food_items`/`recipes` besitzen dagegen bewusst `observeAll()`-Flows
und WHERE-`SELECT *`-Varianten — das Backup braucht davon keines, sondern die
eigenen `BackupDao`-Queries). Kein DAO hat `count()` oder `deleteAll()`.

Damit ist die Aufgabe zweigeteilt:

1. **Datenlayer-Grundlage** (fehlt komplett): Alle-Zeilen-Lesen, Bulk-Insert,
   Löschen, Zählen der fünf persistenten Tabellen.
2. **Format + Prozess**: ein versioniertes, **vom Room-Schema entkoppeltes**
   JSON-Format, Validierung, Import-Modi und eine Oberfläche.

Der zweite Punkt ist der eigentliche Kern: Der Export darf **nicht** die
Room-Spalten 1:1 spiegeln, sonst bricht jeder spätere Spaltenumbau alte
Backups. Und die in der DB eingebetteten Snapshots
(`diary_entries.food_snapshot`/`recipe_snapshot`,
`recipe_ingredients.recipe_snapshot_json`) sind bereits **Domain-Modell-JSON** —
sie müssen beim Export/Import als **opake Strings** durchgereicht werden, damit
sie von den bestehenden `JsonConverters`
(`core/database/.../converter/JsonConverters.kt`, `ignoreUnknownKeys = true`)
beim Anzeigen dekodiert werden und nicht am Backup-Format hängen.

### 1.3 Warum kein Export der SQLite-Datei

Eine Datei-Kopie (`VACUUM INTO`) wäre erheblich weniger Code, scheitert aber an
fünf konkreten Punkten:

| Problem | Konsequenz |
|---|---|
| `room_master_table.identity_hash` und `user_version` stecken in der Datei | Ein Restore braucht exakt passende Room-Migrationen; ein Downgrade (Backup einer neueren App in eine ältere) ist grundsätzlich unmöglich |
| Keine Validierung, keine Vorschau, kein Merge | Ein defektes Backup überschreibt die lokalen Daten ungeprüft und unumkehrbar |
| WAL (`ez_makro.db-wal`/`-shm`) | Eine rohe Dateikopie ist inkonsistent; braucht `VACUUM INTO` oder Checkpoint |
| Schließen + Neubauen der Room-Instanz nötig | Alle Koin-Singletons (`FoodRepository`, `DiaryRepository`, DAOs) halten Referenzen auf die alte Instanz — invasiver Eingriff in die gesamte DI |
| Kein Ort für Settings | Die DataStore-Datei liegt außerhalb der DB |

Ein logischer JSON-Export umgeht all das, weil er durch die vorhandenen DAOs
läuft und das Format nicht an Room bindet. Kostenpunkt: `weight_logs` wird
mitexportiert, obwohl die Funktion (Phase 10) noch nicht existiert — der
`WeightLogDao` existiert bereits, das ist ein Vorteil.

### 1.4 Was fehlt

- Datenbank-Backup (Export, Import, Validierung, UI).
- Ein Settings-Einstiegspunkt im App-Navigationsmodell (es gibt keinen
  Settings-Screen; die Backlogs in `docs/scope.md:45` und
  `docs/Scope_und_Features.md:73` nennen Dark Mode als künftigen Settings-
  Eintrag — und beide Dokumente versprechen bei `scope.md:44`/
  `Scope_und_Features.md:72` noch „optional passwortverschlüsselt", was WP5
  entsprechend A3 streicht).
- Key-Enumeration in `SettingsDataStore` (aktuell nur `getString`/`setString`/
  `remove`/`clear`).

---

## 2. Festgelegte Entscheidungen

Vom Nutzer bestätigt und in den WPs **nicht** neu zu verhandeln.

| # | Entscheidung |
|---|---|
| **A1** | **Logischer JSON-Export statt DB-Datei-Kopie** (Begründung §1.3). Der Export läuft über die DAOs; das Format ist nicht an Room-Spalten gebunden. |
| **A2** | **Eigenes, vom DB-Schema entkoppeltes Formatversionierungssystem.** `formatVersion` im Dokument steuert den Import. `dbSchemaVersion` ist **rein informativ** — der Import verzweigt **nie** darauf. |
| **A3** | **Keine Verschlüsselung.** Der Export ist unverschlüsselt. Der Mechanismus bleibt offen-lernerbar: `formatVersion` erlaubt später einen verschlüsselten Container ohne Bruch. |
| **A4** | **Reines JSON**, Dateiendung `.json`, kein ZIP-Container. Lesbar, debugbar, entspricht dem Phasenplan („JSON Export/Import"). |
| **A5** | **Export-Umfang:** eigene Lebensmittel (`is_user_created = 1`) **plus Favoriten** (`is_favorite = 1` — werden laut Nutzerentscheidung **immer** mitgespeichert, auch ohne Referenz) **plus** alle Lebensmittel, die von Rezepten oder Tagebucheinträgen referenziert werden. Der OFF-Cache-Nichtnutzer-Zugriff ist ein **Schalter, Default aus**. Rezepte (+ Zutaten), Tagebucheinträge, Gewichtslogs und **alle** Settings werden immer exportiert. |
| **A6** | **Zwei Import-Modi, im Preview-Dialog wählbar:** *Ergänzen* (Default — fügt nur Fehlendes hinzu, überschreibt nie lokale Daten) und *Ersetzen* (leert die fünf Tabellen FK-sicher, spielt das Backup 1:1 ein, mit deutlicher Warnung und Bestätigung). |
| **A7** | **Merge = `@Insert(onConflict = OnConflictStrategy.IGNORE)`** auf den natürlichen Schlüsseln (`food_items.id`, `recipes.id`, `diary_entries.id`, `weight_logs.date`). `row_id` wird beim Import auf `0` gesetzt (autoGenerate), damit kein PK-Konflikt entsteht und die FTS-Doc-IDs gültig bleiben. Damit braucht es **keine** Upsert-Query mit 20+ Parametern — Detekt `LongParameterList` (Schwelle 8) würde das verbieten, und `FoodItemDao.upsert` (`:22`, `@Suppress("LongParameterList")`) setzt `is_favorite`/`last_used_at` beim Insert bewusst auf `0`/`NULL` — unbrauchbar für einen Import. **MERGE-Idempotenz:** `recipe_ingredients` hat keinen natürlichen Schlüssel (`row_id` ist AUTOINCREMENT!) — Zutatenzeilen werden deshalb **nur für neu eingefügte Rezepte** geschrieben, sonst entstehen beim zweiten Import doppelte Zutaten (siehe A19 und WP1). |
| **A8** | **Snapshots bleiben opake Strings.** `food_snapshot`, `recipe_snapshot`, `recipe_snapshot_json` werden als `String?` im Format geführt und unverändert geschrieben/gelesen. Die Dekodierbarkeit wird beim Import **einmal** geprüft (A11), damit keine unlesbaren Zeilen in die DB gelangen. |
| **A9** | **UI in einem neuen Modul `:feature:settings:presentation`** (nicht `:feature:backup`) — Backup ist der erste, aber nicht der einzige Settings-Bereich (Dark Mode, Einheiten stehen im Backlog). **Einstieg: genau ein Zahnrad** — das **vorhandene** Ziele-Zahnrad in der Diary-TopAppBar (heute `onNavigateToGoals`) öffnet künftig die `SettingsScreen`-Seite; **kein zweites Icon, kein neues Drawable.** `SettingsScreen` ist die Sammel-Seite mit den Einträgen **„Tagesziele"** (führt weiter zum bestehenden Goals-Screen) und **„Backup"** (führt zum `BackupScreen`). Der Navigations-Callback zu den Zielen wird wie alle Cross-Feature-Routen über `:app` verdrahtet (das Settings-Modul kennt das Diary-Modul nicht). |
| **A10** | **Settings generisch sichern** über neue `snapshot()`/`restore()`-Funktionen in `SettingsDataStore` — **nicht** die 5 Goal-Keys hartkodieren. Damit sind spätere Settings (Einheiten, Theme) automatisch im Backup. |
| **A11** | **Validierung in fünf Stufen** (siehe [§3.4](#34-import-validierung); die Preview deckt Stufe 1–4 ab, Stufe 5 ist der Schreibvorbereitung vorbehalten), Preview (Dry-Run) vor dem eigentlichen Import, gesamter **Datenbank**-Import in **einer** Room-Transaktion. Die Settings-Wiederherstellung läuft danach erst nach erfolgreichem Commit (DataStore ist kein Teil von Room, siehe §3.4). Zeilen, die den Domain-Invarianten widersprechen, werden **übersprungen und im Report gelistet** — nie hart abgebrochen. |
| **A12** | **Neue Projektregel:** Jedes neue **optionale** Feld in einem `@Serializable`-Modell (Domain **und** Backup-DTOs) braucht einen **Default-Wert**. Sonst bricht das Dekodieren alter Backups. Die **Pflichtfelder des Dokumentrahmens** (`format`, `formatVersion`, `appVersionName`, `appVersionCode`, `exportedAtEpochMillis`, `dbSchemaVersion`, `content`) bleiben bewusst default-frei — ihr Fehlen endet als `Corrupted`, bei `format` als `NotAZBackup` (§3.4). Wird in `docs/coding-style.md` und `ADR-007` festgeschrieben. |
| **A13** | **`food_items_fts` wird nicht exportiert.** Der Index wird von den Room-Trigger `room_fts_content_sync_food_items_fts_*` beim Import automatisch mitgepflegt — der WP1-Test weist das nach. `row_id` wird nicht exportiert (kein fachlicher Inhalt). |
| **A14** | **Prüfsumme:** SHA-256 über das kanonisch re-encodete `content`-Objekt, geschrieben als `"sha256:<hex>"`. Für Determinismus ist `settings` eine **sortierte Liste** (nicht `Map`). Ein Mismatch ist eine **Warnung** im Report (kein Abbruch) — eine abgeschnittene Datei fällt ohnehin schon beim JSON-Parser auf und ist dann ein harter Fehler. Fehlt `checksum` in einer Datei (DTO-Default `""`), greift dieselbe Regel: Warnung, kein Abbruch. |
| **A15** | **`weight_logs` gehört ins Backup**, obwohl die Funktion erst in Phase 10 kommt (`WeightLogDao` + Entity existieren bereits, `:core:database`). |
| **A16** | **Kein App-Neustart nach dem Import.** Der Import läuft über die DAOs, bestehende Flows emittieren von selbst. Kein Eingriff in die Room-DB-Instanz, kein Schließen der Datenbank. |
| **A17** | **Android Auto Backup bleibt unangetastet** (`android:allowBackup="true"` in `app/src/main/AndroidManifest.xml`, `backup_rules.xml` leer). Ob der OFF-Cache künftig ausgeschlossen wird, ist eine separate Entscheidung und **nicht** Teil dieses Plans. |
| **A18** | **`ImportReport` zählt Duplikat-Übersprünge separat:** `skippedDuplicates: TableCounts` (per `IGNORE` übersprungene Zeilen aus `BackupInsertReport`) steht neben `skippedRows` (ungültige Zeilen) und `droppedReferences` (verworfene Referenzen). Der Report-Dialog zeigt alle drei Zahlen getrennt — der Smoke-Test weist Idempotenz darüber nach. |
| **A19** | **Auflösungs- und Referenzregeln (bindend für WP1/WP3):** Bekannte Mengen je Modus: **MERGE = bestehende IDs ∪ gültige Backup-IDs, REPLACE = nur gültige Backup-IDs** — Validierung und Referenzbereinigung nutzen **nie** den rohen DB-Stand. Rezept-Zutaten werden ausschließlich **backup-intern** validiert (nie gegen die Ziel-DB — der Bestands-Mapper `RecipeEntity.toRecipe` ist an `FoodLocalDataSource` gekoppelt und würde auf einer leeren Ziel-DB jedes Rezept verwerfen). Zutatenzeilen werden nur für neu eingefügte Rezepte geschrieben (MERGE-Idempotenz, s. A7). |

---

## 3. Zielbild

### 3.1 Backup-Datei (`formatVersion = 1`)

Datei: `ezmakro-backup-<yyyy-MM-dd-HHmm>.json`, MIME `application/json`.

```json
{
  "format": "ezmakro.backup",
  "formatVersion": 1,
  "appVersionName": "1.0",
  "appVersionCode": 1,
  "exportedAtEpochMillis": 1750000000000,
  "dbSchemaVersion": 9,
  "checksum": "sha256:9f2c…",
  "content": {
    "settings": [ { "key": "goal_kcal", "value": "2000.0" } ],
    "foodItems": [
      {
        "id": "off|12345", "name": "Haferflocken", "barcode": null,
        "nutriKcal": 379.0, "nutriProteinG": 13.2, "nutriFatG": 6.5,
        "nutriCarbsG": 67.7, "nutriFiberG": 10.1,
        "servingLabel": null, "servingGramsEquivalent": null,
        "isUserCreated": true, "cachedStatus": "ORIGINAL",
        "origName": null, "origBarcode": null, "origNutriKcal": null,
        "origNutriProteinG": null, "origNutriFatG": null, "origNutriCarbsG": null,
        "origNutriFiberG": null, "origServingLabel": null,
        "origServingGramsEquivalent": null, "origIsUserCreated": null,
        "isFavorite": true, "lastUsedAt": 1749000000000
      }
    ],
    "recipes": [
      {
        "id": "…", "name": "Porridge", "finalWeightGrams": 500.0,
        "portions": 4, "category": "Frühstück", "lastModifiedAt": 1749000000000,
        "isFavorite": false, "lastUsedAt": null,
        "ingredients": [
          {
            "foodItemId": "off|12345", "rawWeightGrams": 80.0,
            "servingCount": null, "recipeSnapshotJson": null
          }
        ]
      }
    ],
    "diaryEntries": [
      {
        "id": "…", "slot": "BREAKFAST", "date": "2026-10-05",
        "foodItemId": null, "recipeId": "…", "amountGrams": null,
        "portionCount": 1.0, "recipeOverridden": false,
        "foodSnapshot": null, "recipeSnapshot": "<opakes Domain-JSON>"
      }
    ],
    "weightLogs": [ { "date": "2026-10-05", "kilograms": 80.5 } ]
  }
}
```

**Kompatibilitätsregeln** (A2, A8, A12, A14 — werden in `ADR-007` festgeschrieben):

| Regel | Umsetzung |
|---|---|
| Formatversion unabhängig vom DB-Schema | `formatVersion` steuert den Import; `dbSchemaVersion` ist nur Info für den Nutzer |
| Vorwärtskompatibilität | Alle **optionalen** Felder mit Default, `Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }` |
| Pflichtfelder | `format`, `formatVersion`, `appVersion*`, `exportedAtEpochMillis`, `dbSchemaVersion`, `content` bleiben **default-frei** (A12); `checksum` ist optional (`Default ""` → Warnung, A14) |
| Versionsbereich | `MIN_SUPPORTED_FORMAT_VERSION = 1 <= formatVersion <= FORMAT_VERSION`; darüber → harter Fehler „Backup stammt aus einer neueren App-Version" |
| Snapshots opak | siehe A8 |
| Determinismus der Prüfsumme | `settings` als sortierte Liste; alle übrigen Sektionen sind `List` |
| Neue Domain-Felder brauchen Defaults | A12, `docs/coding-style.md` |

### 3.2 `BackupScreen`

```
┌─ Backup ─────────────────────────────── [←] ─┐
│                                            │
│  EXPORTIEREN                                │
│  1.234 Lebensmittel · 12 Rezepte · 480 Tage │
│  [ ] Gecachte OFF-Lebensmittel einschließen │
│  [ Backup erstellen ]                       │
│                                            │
│  IMPORTIEREN                                │
│  [ Backup einspielen ]                      │
└────────────────────────────────────────────┘
```

**Ablauf Export:** Button → Event `RequestCreateDocument(fileName)` →
`ActivityResultContracts.CreateDocument("application/json")` in der Composable →
bei Abbruch: `null` → nichts tun; bei Uri: Action `OnDocumentCreated(uri)` →
`BackupRepository.exportTo(uri, options)` → Event `ShowMessage(erfolgreich)`.

**Ablauf Import:** Button → `OpenDocument(arrayOf("application/json", "text/plain", "application/octet-stream"))` → Action `OnBackupSelected(uri)` → `BackupRepository.preview(uri)` → Preview-Dialog:

```
┌─ Backup einspielen ─────────────────────┐
│  Exportiert: 05.10.2026, 14:30          │
│  App-Version: 1.0 · Format-Version 1    │
│  12 Lebensmittel · 3 Rezepte · 480 Tage │
│  Zeitraum: 01.03.2026 – 05.10.2026      │
│                                            │
│  ( ) Ergänzen — nur Fehlendes hinzufügen │
│  ( ) Ersetzen — lokale Daten werden      │
│      vollständig gelöscht                │
│                                            │
│  [Abbrechen]              [Importieren]  │
└────────────────────────────────────────────┘
```

*Ersetzen* öffnet einen **Bestätigungsdialog** mit der Warnung, dass alle
lokal gespeicherten Lebensmittel, Rezepte, Tagebucheinträge und Gewichtsdaten
gelöscht werden — erst danach wird geschrieben. Nach dem Import folgt der
Report-Dialog: eingefügt / Duplikate übersprungen / ungültige Zeilen /
verworfene Referenzen / Warnungen (A18).

Fehler erscheinen als Snackbar über `UiText` (`BackupEvent.ShowMessage`).

### 3.3 Modul- und Schichtzuordnung

```
:feature:settings:presentation   SettingsScreen, BackupScreen, ViewModel, Nav
        │
        ▼ (nur Domain-Interface)
:core:domain                      BackupRepository, BackupOptions, ImportMode,
                                 BackupPreview, ImportReport, BackupWarning,
                                 AppVersionProvider
        ▲
:core:data                        BackupArchive (DTOs), BackupCodec,
                                 BackupMapper, BackupValidator,
                                 BackupExporter, BackupImporter,
                                 BackupLocalDataSource (Interface) + BackupRows,
                                 RoomBackupRepository, ContentResolverBackupStorage,
                                 RoomBackupLocalDataSource, backupDataModule
        ▲
:core:database                    BackupDao (Roh-Lesen/Bulk-Insert/Löschen/Zählen)
```

`:feature:settings:presentation` hängt an `:core:domain`, `:core:presentation`,
`:core:design-system` — **nicht** an `:core:data` oder `:core:database`.

### 3.4 Import-Validierung

| Stufe | Prüfung | Ergebnis bei Verstoß |
|---|---|---|
| 1 | JSON parsebar; `format`-Feld im rohen `JsonObject` == `"ezmakro.backup"` (Vorabprüfung **vor** dem strikten Dekodieren) | Parse-Fehler → `Corrupted`; `format` fehlend/fremd → `DataError.Local.INVALID_BACKUP` |
| 2 | `formatVersion` im rohen Objekt vorhanden und `MIN_SUPPORTED_FORMAT_VERSION <= formatVersion <= FORMAT_VERSION`; danach striktes Dekodieren des Dokuments | fehlend/ungültig **oder** fehlende Pflichtfelder/Serialisierungsfehler → `Corrupted`; Wert außerhalb Bereich → `DataError.Local.UNSUPPORTED_BACKUP_VERSION` |
| 3 | Prüfsumme über `content` | `BackupWarning.ChecksumMismatch` (auch bei fehlendem `checksum`, A14), Import läuft weiter |
| 4 | **Zeilenvalidierung — ohne jeden Zugriff auf die Ziel-DB, in fester Reihenfolge:** (a) Foods über `FoodItemEntity.toFoodItem()` in `try`/`catch` (greift `name.isNotBlank()`, Nährwerte ≥ 0) → erfolgreiche Ergebnisse werden als `validFoods: Map<String, FoodItem>` gesammelt; (b) Rezepte über eine **eigene, backup-interne Prüfung** gegen `validFoods` **plus** (nur bei MERGE) die bestehenden Ziel-DB-Food-IDs — **nie** über `RecipeEntity.toRecipe(..., foodLocalDataSource)` (dieser Mapper würde auf einer leeren Ziel-DB jedes Rezept verwerfen, A19); (c) Tagebucheinträge über `DiaryEntryEntity.toDiaryEntry()` (`MealSlot.valueOf`, `LocalDate.parse`, Snapshot-Dekodierung, food/recipe-require); (d) WeightLogs: `kilograms > 0`, parsebares Datum | Zeile wird **übersprungen** und mit Grund in `ImportReport.skippedRows` gelistet |
| 5 | **Referenzielle Prüfung (nur Tagebucheinträge):** effektive Mengen je Modus — MERGE = bestehende IDs ∪ gültige Backup-IDs, REPLACE = nur gültige Backup-IDs (A19). `foodItemId`/`recipeId` außerhalb der Menge → auf `null` setzen, **sofern der zugehörige Snapshot dekodierbar ist** — sonst wird die Zeile stattdessen übersprungen. Rezept-Zutaten brauchen hier keine Bereinigung mehr: Stufe 4b hat sie bereits nur mit auflösbaren Referenzen akzeptiert | Zähler `droppedReferences` im Report |

Gesamter **Datenbank**-Schreibvorgang in **einer** Room-Transaktion
(`@Transaction`-Methode in `BackupDao`) — die fünf Tabellen sind entweder vollständig
geschrieben oder unverändert. Erst **nach** erfolgreichem Commit werden die Settings
über `restore()` geschrieben (DataStore liegt außerhalb von Room; „alles oder nichts"
gilt für die Tabellen — ein Rollback der DB lässt die Settings unangetastet).
Daraus folgt die Reihenfolge im Importer: dekodieren → Stufe 4 → Stufe 5 →
Transaktion (`insertAll`/`replaceAll`) → Settings → Report.

---

## 4. WP-Übersicht

| WP | Thema | Hängt ab von | Art | Status |
|---|---|---|---|---|
| **WP1** | `BackupDao`: Roh-Zugriff auf alle fünf Tabellen + Transaktions-Import | — | Feature (Daten) | 🔜 |
| **WP2** | Format + Codec: `BackupArchive`, `BackupCodec`, Prüfsumme, Fixtures, ADR-007 | WP1 | Feature (Daten) | 🔜 |
| **WP3** | Domain-Vertrag, Settings-Snapshot, SAF-Storage, Export/Import-Orchestrierung | WP2 | Feature (Daten) | 🔜 |
| **WP4** | UI-Modul `:feature:settings:presentation` + Navigation + Diary-Einstieg | WP3 | Feature (Presentation) | 🔜 |
| **WP5** | Integrationstests, Härtung, Dokumentation | WP4 | Härtung + Doku | 🔜 |

Jede Statuszelle wird nach dem Commit auf ✅ + Commit-Hash aktualisiert.

---

## WP1 — `BackupDao`: Roh-Zugriff auf alle Tabellen

**Ziel:** Der Datenlayer bekommt den fehlenden Roh-Zugriff auf die fünf
persistenten Tabellen: alle Zeilen lesen, per Bulk-Insert schreiben, zählen,
leeren, FK-sicher löschen und den Gesamtimport transaktional kapseln. **Kein
JSON, kein Formatwissen in diesem WP** — `:core:database` bleibt eine reine
Room-Schicht und weiß nichts über Backup-Dateien.

**Abhängigkeit:** keine.

### Zu lesen

- `core/database/.../EZDatabase.kt` (Entities, Version 9)
- `core/database/.../dao/FoodItemDao.kt`, `RecipeDao.kt`, `DiaryEntryDao.kt`,
  `WeightLogDao.kt` — Stil, `@Upsert`/`@Query`, Suppressions
- `core/database/.../entity/*.kt` — **alle** Spalten und Typen
- `core/database/.../migration/Migrations.kt` (nur für das Verständnis von
  `row_id`-Herkunft und FK-Verhalten)
- `core/database/.../di/DatabaseModule.kt` (Koin-Bindings-Muster)
- `core/database/schemas/com.example.ezmakro.core.database.EZDatabase/9.json`
  (Spaltennamen exakt: `recipes.is_favorite` ist **INTEGER**, `recipes.portions`
  ist **INTEGER**, `recipe_ingredients.row_id` ist **INTEGER/LONG**)
- `core/database/src/test/.../DatabaseTest.kt` (Robolectric-Harness),
  `.../testutil/FoodItemFixtures.kt`, `.../testutil/RecipeFixtures.kt`
- `config/detekt/detekt.yml` (Schwellen: `LongParameterList` 8/10,
  `TooManyFunctions` 15 in Interfaces, `MagicNumber` aktiv)

### Festgelegte Vorgaben

- **Alles über Listen-Parameter**, keine Query mit 20+ Einzelparametern
  (Detekt `LongParameterList`, A7).
- **`insertAll` filtert Zutatenzeilen auf neu eingefügte Rezepte**
  (`recipe_ingredients.row_id` ist AUTOINCREMENT, kein natürlicher Schlüssel —
  sonst Duplikate beim zweiten Import, A7/A19); die gefilterten Zeilen zählen
  als `skipped`.
- Keine foreign keys im DAO deaktivieren. Die Referenzbereinigung ist Aufgabe
  von WP3.
- `row_id` wird beim Insert **nicht** mitgeschrieben — die Entities werden mit
  `rowId = 0` übergeben, Room vergibt die IDs neu. Damit kollidiert nichts und
  die FTS-Doc-IDs bleiben gültig (A7, A13).
- Die DAO kennt **keine** Backup-Begriffe außer dem Transaktions-Wrapper und
  den Zählklassen. Kein `@Serializable`, kein JSON, kein `ContentResolver`.

### Schritte

1. **`BackupDao` anlegen** —
   `core/database/src/main/java/com/example/ezmakro/core/database/dao/BackupDao.kt`:

   ```kotlin
   @Dao
   @Suppress("TooManyFunctions")
   interface BackupDao {
       // Lesen — Reihenfolge: food_items, recipes, recipe_ingredients,
       // diary_entries, weight_logs
       @Query("SELECT * FROM food_items") suspend fun readFoodItems(): List<FoodItemEntity>
       @Query("SELECT * FROM recipes") suspend fun readRecipes(): List<RecipeEntity>
       @Query("SELECT * FROM recipe_ingredients") suspend fun readRecipeIngredients(): List<RecipeIngredientEntity>
       @Query("SELECT * FROM diary_entries") suspend fun readDiaryEntries(): List<DiaryEntryEntity>
       @Query("SELECT * FROM weight_logs") suspend fun readWeightLogs(): List<WeightLogEntity>

       @Query("SELECT id FROM food_items") suspend fun readFoodItemIds(): List<String>
       @Query("SELECT id FROM recipes") suspend fun readRecipeIds(): List<String>

       // Schreiben — IGNORE auf den natürlichen Schlüsseln (A7)
       @Insert(onConflict = OnConflictStrategy.IGNORE)
       suspend fun insertFoodItems(items: List<FoodItemEntity>): List<Long>
       // … analog insertRecipes / insertRecipeIngredients / insertDiaryEntries /
       // insertWeightLogs, alle mit OnConflictStrategy.IGNORE

       // Zählen
       @Query("SELECT COUNT(*) FROM food_items") suspend fun countFoodItems(): Int
       // … analog für recipes, recipe_ingredients, diary_entries, weight_logs

       // Leeren — FK-sichere Reihenfolge: erst Kinder, dann Eltern
       @Query("DELETE FROM recipe_ingredients") suspend fun deleteAllRecipeIngredients()
       @Query("DELETE FROM diary_entries") suspend fun deleteAllDiaryEntries()
       @Query("DELETE FROM recipes") suspend fun deleteAllRecipes()
       @Query("DELETE FROM food_items") suspend fun deleteAllFoodItems()
       @Query("DELETE FROM weight_logs") suspend fun deleteAllWeightLogs()

       @Transaction
       suspend fun replaceAll(
           foods: List<FoodItemEntity>,
           recipes: List<RecipeEntity>,
           ingredients: List<RecipeIngredientEntity>,
           entries: List<DiaryEntryEntity>,
           weightLogs: List<WeightLogEntity>,
       ): BackupInsertReport {
           deleteAllRecipeIngredients(); deleteAllDiaryEntries()
           deleteAllRecipes(); deleteAllFoodItems(); deleteAllWeightLogs()
           return insertAll(foods, recipes, ingredients, entries, weightLogs)
       }

        @Transaction
        suspend fun insertAll(
            foods: List<FoodItemEntity>,
            recipes: List<RecipeEntity>,
            ingredients: List<RecipeIngredientEntity>,
            entries: List<DiaryEntryEntity>,
            weightLogs: List<WeightLogEntity>,
        ): BackupInsertReport {
            val foodIds = insertFoodItems(foods)
            val recipeIds = insertRecipes(recipes)
            // Zutaten nur für neu eingefügte Rezepte: recipe_ingredients.row_id ist
            // AUTOINCREMENT (kein natürlicher Schlüssel) — sonst entstehen beim
            // zweiten Import doppelte Zutaten (A7/A19).
            val insertedRecipeIds =
                recipes.filterIndexed { index, _ -> recipeIds[index] >= 0L }.map { it.id }.toSet()
            val keptIngredients = ingredients.filter { it.recipeId in insertedRecipeIds }
            val ingredientIds = insertRecipeIngredients(keptIngredients)
            val entryIds = insertDiaryEntries(entries)
            val weightIds = insertWeightLogs(weightLogs)
            return BackupInsertReport(
                foodItems = TableInsertCounts.from(foodIds),
                recipes = TableInsertCounts.from(recipeIds),
                recipeIngredients = TableInsertCounts.from(
                    ingredientIds,
                    preSkipped = ingredients.size - keptIngredients.size,
                ),
                diaryEntries = TableInsertCounts.from(entryIds),
                weightLogs = TableInsertCounts.from(weightIds),
            )
        }
    }
   ```

   Reihenfolge der Inserts: parents zuerst, damit die FK-Prüfung der Kinder
   (`recipe_ingredients.recipe_id` → `recipes.id`,
   `recipe_ingredients.food_item_id` → `food_items.id`) erfüllt ist.

2. **Zählklassen** — gleiche Datei oder separate
   `core/database/.../dao/BackupInsertCounts.kt`:

   ```kotlin
   /** Room meldet für per IGNORE übersprungene Zeilen `-1`.
    *  [preSkipped] zählt Zeilen, die gar nicht erst versucht werden — Zutaten
    *  bereits bestehender Rezepte (A7/A19). */
   data class TableInsertCounts(
       val inserted: Int,
       val skipped: Int,
   ) {
       companion object {
           fun from(rowIds: List<Long>, preSkipped: Int = 0): TableInsertCounts =
               TableInsertCounts(
                   inserted = rowIds.count { it >= 0L },
                   skipped = rowIds.count { it == -1L } + preSkipped,
               )
       }
   }

   data class BackupInsertReport(
       val foodItems: TableInsertCounts,
       val recipes: TableInsertCounts,
       val recipeIngredients: TableInsertCounts,
       val diaryEntries: TableInsertCounts,
       val weightLogs: TableInsertCounts,
   )
   ```

3. **Koin** — `core/database/.../di/DatabaseModule.kt`: ein Binding nach dem
   bestehenden Muster ergänzen:
   `single { get<EZDatabase>().backupDao() }`.
   Der Kommentar im Modul bleibt unverändert; keine neue Modul-Datei.

4. **Typkonvertierung nicht vergessen:** `RecipesEntity.isFavorite` ist `Int`
   (kein `Boolean`), `portions` ist `Int`, `RecipeIngredientEntity.rowId` ist
   `Long`, `FoodItemEntity.rowId` ist `Int`. Der DAO nimmt die Entities
   unverändert — die Abbildung Entity↔DTO ist Aufgabe von WP3.

### Dateien

**Neu**
- `core/database/src/main/java/com/example/ezmakro/core/database/dao/BackupDao.kt`
- `core/database/src/main/java/com/example/ezmakro/core/database/dao/BackupInsertCounts.kt`
- `core/database/src/test/java/com/example/ezmakro/core/database/dao/BackupDaoTest.kt`

**Geändert**
- `core/database/.../di/DatabaseModule.kt`

### Tests

`BackupDaoTest` (Robolectric, `DatabaseTest`-Harness,
`@RunWith(RobolectricTestRunner::class)`):

- `readFoodItems/readRecipes/readRecipeIngredients/readDiaryEntries/readWeightLogs`
  liefern nach dem Befüllen exakt die eingefügten Zeilen (inkl. Spalten wie
  `orig_*`, `is_favorite`, `last_used_at`, `recipe_snapshot_json`,
  `recipe_overridden`).
- `insertFoodItems` mit zwei Zeilen gleicher `id`: genau eine wird eingefügt,
  `skipped == 1` — **beide** Zeilen behalten ihre `is_favorite`-/`last_used_at`-Werte
  (der Nachweis, warum `FoodItemDao.upsert` unbrauchbar wäre, A7).
- `insertFoodItems` mit `rowId = 0` vergibt neue IDs; zwei Runden Insert mit
  denselben Daten sind idempotent (kein Duplikat, kein Fehler).
- **`insertAll` ist MERGE-idempotent (A7/A19):** zwei identische Durchläufe —
  die zweite Runde fügt **keine** zusätzlichen `recipe_ingredients`-Zeilen ein
  (gefiltert), `recipes.skipped` entspricht der Rezeptanzahl,
  `recipeIngredients.skipped` enthält die gefilterten Zutaten,
  Food-/Diary-/Weight-Counts bleiben konstant.
- `insertDiaryEntries`/`insertWeightLogs` analog (PK `id` bzw. `date`).
- **FTS-Nachweis (A13):** Food über `FoodItemDao.upsert` anlegen, Import per
  `BackupDao.insertAll` mit einem weiteren Food → `FoodItemDao.searchByName` findet
  **beide** (die Content-Sync-Trigger haben den Index gepflegt).
- `countX()` stimmen mit den eingefügten Mengen überein.
- `replaceAll` löscht alle fünf Tabellen und insertet den neuen Bestand;
  `countX()` entspricht danach exakt dem neuen Bestand.
- `deleteAllRecipes`/`deleteAllFoodItems` leeren auch den FTS-Index
  (`searchByName` liefert leer).

**Regressionspflichtig:** `SchemaValidationTest`, `MigrationTest`,
`FoodItemDaoTest`, `RecipeDaoTest`, `DiaryEntryDaoTest`, `WeightLogDaoTest`
müssen **unverändert** grün sein.

### Verifikation

```
./gradlew :core:database:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] `BackupDao` hat keine Entity-Listen-Queries mit mehr als einem Parameter;
      keine neue `@Suppress("LongParameterList")` in diesem WP
- [ ] Der Merge-Pfad überschreibt **keine** bestehende Zeile und setzt
      `is_favorite`/`last_used_at` **nicht** zurück (Test)
- [ ] Zweimal identisches `insertAll` erzeugt **keine** doppelten
      `recipe_ingredients`-Zeilen; gefilterte Zutaten zählen als `skipped`
      (A7/A19, Test)
- [ ] Nach `insertAll` ist der FTS-Index vollständig (`searchByName`-Test)
- [ ] `replaceAll` ist eine einzige Transaktion und FK-sicher (Reihenfolge
      Kinder vor Eltern)
- [ ] `DatabaseModule.kt` folgt dem bestehenden Binding-Muster
- [ ] `core:database` enthält keinerlei JSON-/Format-/`@Serializable`-Code
- [ ] Bestehende `:core:database`-Tests unverändert grün

**Commit-Vorschlag:** `feat: BackupDao - Roh-Zugriff und transaktionaler Import auf alle Tabellen`

---

## WP2 — Backup-Format und Codec

**Ziel:** Das versionierte, vom Room-Schema entkoppelte Dateiformat als reine
Kotlin-DTOs plus Codec (Encode/Decode/Prüfsumme/Versionsprüfung) in `:core:data`.
**Noch kein Repository, noch keine IO, noch kein UI** — der Codec ist eine reine
Funktion `BackupContentDto` ↔ `String` und vollständig ohne Android testbar.

**Abhängigkeit:** WP1 (der Codec kennt die Tabellen, aber nicht den DAO).

### Zu lesen

- `core/domain/.../model/FoodItem.kt`, `Recipe.kt`, `RecipeIngredient.kt`,
  `RecipeSnapshot.kt`, `CachedFoodItemStatus.kt`, `WeightLog.kt`, `Ids.kt`
- `core/database/.../entity/*.kt` — **Spaltennamen sind die Namensquelle** für
  die DTOs
- `core/database/.../converter/JsonConverters.kt` (Json-Konfiguration, Muster)
- `core/data/.../network/HttpClientFactory.kt` (`Json`-Instanz, `encodeDefaults`)
- `core/data/build.gradle.kts` (bisher nur `ezmakro.ktor` → Serialization-Plugin
  kommt indirekt; wird jetzt explizit)
- `core/domain/.../result/DataError.kt`
- `docs/coding-style.md`

### Festgelegte Vorgaben

- **Jedes optionale Feld mit Default-Wert** (A12), damit
  `formatVersion`-1-Dokumente auch nach Ergänzungen dekodieren. Die
  Pflichtfelder des Rahmens (`format`, `formatVersion`, `appVersion*`,
  `exportedAtEpochMillis`, `dbSchemaVersion`, `content`) bleiben bewusst
  default-frei; `checksum` ist mit `""` optional (A14-Warnung, kein Abbruch).
- `settings` ist eine **sortierte Liste** `List<BackupSettingDto>`, keine `Map` (A14).
- DTO-Feldnamen sind **CamelCase-Pendant** zu den DB-Spalten, aber das Format
  ist **bewusst nicht** an die Spalten gebunden: bei einem späteren Spaltenumbau
  wird das DTO **nicht** angefasst (A1/A2).
- `FoodItemEntity.row_id`, `RecipeIngredientEntity.row_id` werden **nicht**
  exportiert (A13). `food_items_fts` existiert im Format nicht (A13).
- `cachedStatus` bleibt `String` im Format — der Import mappt über die bekannte
  Werteliste und fällt bei unbekannten Werten auf `ORIGINAL` zurück (gleiches
  Verhalten wie `ScalarConverters.textToCachedStatus`). So bricht ein Backup mit
  künftig neuem Status nicht den Import.
- Keine Verschlüsselung, keine Kompression (A3/A4).

### Schritte

1. **`core/data/build.gradle.kts`:** Plugin `alias(libs.plugins.ezmakro.kotlinx.serialization)`
   ergänzen. `kotlinx-serialization-json` kommt dann explizit über die
   Convention; keine neue Dependency-Zeile nötig.

2. **DTOs anlegen** — `core/data/src/main/java/com/example/ezmakro/core/data/backup/BackupArchive.kt`:

   ```kotlin
    @Serializable
    data class BackupDocumentDto(
        val format: String,
        val formatVersion: Int,
        val appVersionName: String,
        val appVersionCode: Int,
        val exportedAtEpochMillis: Long,
        val dbSchemaVersion: Int,
        val checksum: String = "",
        val content: BackupContentDto,
    )

   @Serializable
   data class BackupContentDto(
       val settings: List<BackupSettingDto> = emptyList(),
       val foodItems: List<BackupFoodItemDto> = emptyList(),
       val recipes: List<BackupRecipeDto> = emptyList(),
       val diaryEntries: List<BackupDiaryEntryDto> = emptyList(),
       val weightLogs: List<BackupWeightLogDto> = emptyList(),
   )

   @Serializable
   data class BackupSettingDto(val key: String, val value: String)

   @Serializable
   data class BackupFoodItemDto(
       val id: String,
       val name: String,
       val barcode: String? = null,
       val nutriKcal: Double? = null,
       val nutriProteinG: Double? = null,
       val nutriFatG: Double? = null,
       val nutriCarbsG: Double? = null,
       val nutriFiberG: Double? = null,
       val servingLabel: String? = null,
       val servingGramsEquivalent: Double? = null,
       val isUserCreated: Boolean = false,
       val cachedStatus: String = "ORIGINAL",
       val origName: String? = null,
       val origBarcode: String? = null,
       val origNutriKcal: Double? = null,
       val origNutriProteinG: Double? = null,
       val origNutriFatG: Double? = null,
       val origNutriCarbsG: Double? = null,
       val origNutriFiberG: Double? = null,
       val origServingLabel: String? = null,
       val origServingGramsEquivalent: Double? = null,
       val origIsUserCreated: Boolean? = null,
       val isFavorite: Boolean = false,
       val lastUsedAt: Long? = null,
   )

   @Serializable
   data class BackupRecipeDto(
       val id: String,
       val name: String,
       val finalWeightGrams: Double? = null,
       val portions: Int = 1,
       val category: String? = null,
       val lastModifiedAt: Long = 0L,
       val isFavorite: Boolean = false,
       val lastUsedAt: Long? = null,
       val ingredients: List<BackupIngredientDto> = emptyList(),
   )

   @Serializable
   data class BackupIngredientDto(
       val foodItemId: String? = null,
       val rawWeightGrams: Double? = null,
       val servingCount: Double? = null,
       val recipeSnapshotJson: String? = null,
   )

   @Serializable
   data class BackupDiaryEntryDto(
       val id: String,
       val slot: String,
       val date: String,
       val foodItemId: String? = null,
       val recipeId: String? = null,
       val amountGrams: Double? = null,
       val portionCount: Double? = null,
       val recipeOverridden: Boolean = false,
       val foodSnapshot: String? = null,
       val recipeSnapshot: String? = null,
   )

   @Serializable
   data class BackupWeightLogDto(
       val date: String,
       val kilograms: Double,
   )
   ```

   `BackupRecipeDto.ingredients` enthält die Zeilen aus `recipe_ingredients`
   **einer** Rezept-ID; der Export gruppiert sie, der Import schreibt sie zurück.
   Das ist eine bewusste Normalisierung: `row_id` der Zutat-Zeile ist nicht
   fachlich und wird nicht geführt (A13).

3. **Codec** — `core/data/.../backup/BackupCodec.kt`:

   ```kotlin
   object BackupCodec {
       const val FORMAT_ID = "ezmakro.backup"
       const val FORMAT_VERSION = 1
       const val MIN_SUPPORTED_FORMAT_VERSION = 1

       private val json = Json {
           ignoreUnknownKeys = true
           isLenient = true
           encodeDefaults = true
       }

       sealed interface DecodeResult {
           data class Success(val document: BackupDocumentDto, val checksumValid: Boolean) : DecodeResult
           data class Failure(val reason: DecodeError) : DecodeResult
       }

       sealed interface DecodeError {
           data object NotAZBackup : DecodeError
           data class UnsupportedVersion(val found: Int, val supportedMax: Int) : DecodeError
           data object Corrupted : DecodeError
       }

       fun encode(document: BackupDocumentDto): String =
           json.encodeToString(BackupDocumentDto.serializer(), document)

       fun checksum(content: BackupContentDto): String = // "sha256:" + Hex(sha256(canonicalBytes))
       fun canonicalContent(content: BackupContentDto): String =
           json.encodeToString(BackupContentDto.serializer(), content)

        fun decode(text: String): DecodeResult { /* Schritte 1–3, s. §3.4 */ }
    }
    ```

    `decode` arbeitet in drei Schritten: (1) `Json.parseToJsonElement(text)` —
    Parse-Fehler → `Corrupted`; (2) **Vorabprüfung auf dem rohen `JsonObject`**,
    **vor** dem strikten Dekodieren: `format` fehlend oder ≠ `FORMAT_ID` →
    `NotAZBackup`; `formatVersion` fehlend/kein `Int` → `Corrupted`; außerhalb
    `[MIN_SUPPORTED_FORMAT_VERSION, FORMAT_VERSION]` →
    `UnsupportedVersion(found, supportedMax)`; (3) striktes
    `decodeFromString<BackupDocumentDto>` — `MissingFieldException`/
    `SerializationException` → `Corrupted`. Der Prüfsummenvergleich liefert
    **kein** Fehlerresultat, sondern ein Flag `checksumValid` (A14; fehlendes
    `checksum` ⇒ `false`) — der Aufrufer macht daraus eine Warnung.

4. **Tests + Fixtures** — `core/data/src/test/resources/backup/`:
   - `backup-v1-minimal.json` — kleines, vollständig valides Backup
   - `backup-v1-evolved.json` — **entscheidender Test**: enthält
     *unbekannte* Felder (z. B. `futureField`, `nutritionPerServing`) und
     **fehlende** optionale Felder (kein `lastUsedAt`, kein `category`, keine
     `orig_*`), eine zusätzliche unbekannte Sektion `metadata` und eine
     unbekannte `cachedStatus`-Angabe. Der Codec muss das dekodieren
     (`checksumValid == false` ist zulässig).
   - `backup-corrupt.json` — abgeschnittenes JSON.

5. **`ADR-007` anlegen** — `docs/adr/ADR-007-backup-format.md` mit Status
   „accepted", Kontext, Entscheidung (JSON-Format, `formatVersion`, Prüfsumme,
   Merge/Ersetzen), und den **Forward-Kompatibilitätsregeln** aus §3.1. Format
   wie die bestehenden ADRs (siehe `docs/adr/ADR-004-off-eintragsstatus.md`).

### Dateien

**Neu**
- `core/data/src/main/java/com/example/ezmakro/core/data/backup/BackupArchive.kt`
- `core/data/src/main/java/com/example/ezmakro/core/data/backup/BackupCodec.kt`
- `core/data/src/test/java/com/example/ezmakro/core/data/backup/BackupCodecTest.kt`
- `core/data/src/test/resources/backup/backup-v1-minimal.json`
- `core/data/src/test/resources/backup/backup-v1-evolved.json`
- `core/data/src/test/resources/backup/backup-corrupt.json`
- `docs/adr/ADR-007-backup-format.md`

**Geändert**
- `core/data/build.gradle.kts`

### Tests

`BackupCodecTest` (JUnit5 + AssertK, **ohne** Robolectric — reines Kotlin):

- **Round-Trip:** `BackupContentDto` mit allen Feldern belegt (inkl. verschachtelter
  `ingredients`, Unicode-Namen, `null`-Werten) → `encode` → `decode` → Inhalt
  identisch.
- **Prüfsumme:** `checksum(canonicalContent(x))` ist stabil; Änderung an einem
  verschachtelten Wert ändert sie; `checksumValid` ist für ein frisch
  erzeugtes Dokument `true`.
- **Prüfsummen-Mismatch** (Feld im Test manipuliert) → `Success(…,
  checksumValid = false)` und **kein** Fehler.
- **`backup-v1-minimal.json`** → `Success`, `checksumValid == true`.
- **`backup-v1-evolved.json`** → `Success`: unbekannte Felder und die
  unbekannte Sektion werden ignoriert, fehlende optionale Felder greifen auf ihre
  Defaults, unbekannter `cachedStatus` bleibt als String erhalten.
- **`backup-corrupt.json`** → `Failure(Corrupted)`.
- **Fremddatei** (`{"foo":1}`) → `Failure(NotAZBackup)`.
- **Format-Feld fehlt** (`{"formatVersion":1,"content":{…}}`) →
  `Failure(NotAZBackup)` (Vorabprüfung auf dem rohen Objekt, §3.4).
- **`formatVersion` fehlt** (`{"format":"ezmakro.backup","content":{…}}`) →
  `Failure(Corrupted)`.
- **Pflichtfeld fehlt** (z. B. `content` im sonst validen Dokument) →
  `Failure(Corrupted)` (striktes Dekodieren, kein Default).
- **`checksum` fehlt** → `Success(…, checksumValid = false)` und **kein**
  Fehler (A14-Warnung beim Import).
- **Zukunftsformat** (`formatVersion = 99`) → `Failure(UnsupportedVersion(99, 1))`.
- **Altes Format** (`formatVersion = 0`) → `Failure(UnsupportedVersion(0, 1))`.
- **`settings`-Determinismus:** zwei `BackupContentDto` mit denselben Settings in
  unterschiedlicher Eingangsreihenfolge → identische `canonicalContent()`.

### Verifikation

```
./gradlew :core:data:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] `BackupCodec` ist Android-frei und ohne Room testbar
- [ ] Alle **optionalen** Felder in `BackupArchive.kt` haben Default-Werte;
      die Pflichtfelder (`format`, `formatVersion`, `appVersion*`,
      `exportedAtEpochMillis`, `dbSchemaVersion`, `content`) bleiben
      default-frei und enden als `Corrupted`, bei `format` als `NotAZBackup`
      (A12, Tests)
- [ ] `decode` unterscheidet `NotAZBackup`, `UnsupportedVersion`, `Corrupted`
- [ ] Ein Prüfsummen-Mismatch ist **kein** Abbruch (A14)
- [ ] `backup-v1-evolved.json` wird fehlerfrei dekodiert — der
      Vorwärtskompatibilitäts-Nachweis liegt als Test vor
- [ ] `settings` ist eine sortierte Liste; `canonicalContent` ist bei
      unterschiedlicher Eingangsreihenfolge identisch
- [ ] Kein Feld des Formats heißt `rowId`/`row_id`; `food_items_fts` kommt im
      Format nicht vor (A13)
- [ ] `ADR-007` existiert und nennt die Forward-Kompatibilitätsregeln
- [ ] Bestehende `:core:data`-Tests unverändert grün

**Commit-Vorschlag:** `feat: versioniertes Backup-Format mit Codec, Pruefsumme und Forward-Kompatibilitaets-Fixtures`

---

## WP3 — Domain-Vertrag, Settings-Snapshot, SAF, Export/Import

**Ziel:** Der vollständige Backup-Prozess im Datenlayer: Domain-Interface,
generischer Settings-Snapshot, SAF-Stream-Zugriff, Export/Import-Orchestrierung
mit Validierung und Referenzbereinigung, Fehlertypen, Koin-Verdrahtung sowie der
Integrationstest Export → Import.

**Abhängigkeit:** WP2 (Format + Codec), WP1 (`BackupDao`).

### Zu lesen

- `core/domain/.../repository/{FoodRepository,DiaryRepository,GoalRepository}.kt`
  (Interface-Stil, `Result`-Rückgaben)
- `core/domain/.../datasource/{FoodLocalDataSource,DiaryLocalDataSource}.kt`
- `core/domain/.../result/{Result,DataError}.kt` (Projekt-Result-Wrapper)
- `core/data/.../datastore/SettingsDataStore.kt` + `goal/DataStoreGoalRepository.kt`
  (die 5 Keys: `goal_kcal`, `goal_protein`, `goal_fat`, `goal_carbs`, `goal_fiber`)
- `core/data/.../di/CoreDataModule.kt` (Koin-Muster, `androidContext()`-Nutzung)
- `core/data/.../di/{FoodDataModule,GoalDataModule}.kt`
- `core/data/.../{food,recipe,diary}/mapper/*` — **die Entity→Domain-Mapper, die
  WP3 für die Zeilenvalidierung (A11/Stufe 4) wiederverwendet**
- `core/database/.../converter/{JsonConverters,ScalarConverters}.kt`
- `core/presentation/.../uitext/DataErrorUiText.kt` (exhaustive `when`)
- `core/presentation/src/main/res/values/strings.xml`
- `core/database/src/test/.../DatabaseTest.kt` (Robolectric-Harness als Muster)
- `core/data/src/test/.../datastore/SettingsDataStoreTest.kt` (`@TempDir`-Muster)

### Festgelegte Vorgaben

- Das Domain-Interface arbeitet mit **`uri: String`**, nicht mit `Uri` und nicht
  mit Streams — `:core:domain` bleibt reines Kotlin ohne Android.
- Ein schmales **`BackupLocalDataSource`**-Interface **in `:core:data`**
  (`core/data/.../backup/BackupLocalDataSource.kt`, neben `BackupRows`) kapselt
  den DAO, damit `BackupRepository` mit einem handgeschriebenen Fake testbar
  bleibt; die Room-Implementierung lebt im selben Package. **Nicht** ins
  Domain-Modul: `:core:domain` hängt nur an `kotlinx-coroutines-core` und kennt
  weder die Room-Entities von `BackupRows` noch `BackupInsertReport`
  (Review-Befund). Die Domänengrenze bleibt ausschließlich
  `BackupRepository`; die Fakes liegen in `core/data/src/test`.
- IO und DB-Zugriff in `Dispatchers.IO`; `CancellationException` wird
  durchgereicht (Muster `runCatchingLocal` in `RoomDiaryLocalDataSource.kt:68`).
- **Kein Verschieben bestehender Mapper.** Die Validierung ruft die vorhandenen
  `toFoodItem()`/`toDiaryEntry()`/… auf; neue Mapping-Funktionen kommen als
  `BackupMapper.kt` daneben.
- `INVALID_BACKUP`/`UNSUPPORTED_BACKUP_VERSION` werden **echte
  `DataError.Local`-Cases** (Projektkonvention), nicht eigene Fehler-Sektions.
- Ein Export-/Import-Durchlauf darf nie eine halbe Zustaendigkeit hinterlassen:
  alles oder nichts (A11, A16).

### Schritte

1. **Domain-Modelle** — `core/domain/.../model/backup/`:

   ```kotlin
   enum class ImportMode { MERGE, REPLACE }

   data class BackupOptions(
       /** OFF-Cache mit exportieren (A5). */
       val includeCachedOpenFoodFactsItems: Boolean = false,
   )

   data class BackupExportSummary(
       val foodItemCount: Int,
       val recipeCount: Int,
       val diaryEntryCount: Int,
       val weightLogCount: Int,
       val settingCount: Int,
   )

   data class BackupPreview(
       val formatVersion: Int,
       val appVersionName: String,
       val exportedAtEpochMillis: Long,
       val foodItemCount: Int,
       val recipeCount: Int,
       val diaryEntryCount: Int,
       val weightLogCount: Int,
       val earliestDate: LocalDate?,
       val latestDate: LocalDate?,
       val warnings: List<BackupWarning>,
   )

   data class ImportReport(
       val mode: ImportMode,
       val inserted: TableCounts,
       /** per `IGNORE` übersprungene Duplikate (A18). */
       val skippedDuplicates: TableCounts,
       val skippedRows: List<SkippedRow>,
       val droppedReferences: Int,
       val settingsRestored: Int,
       val warnings: List<BackupWarning>,
   )

   sealed interface BackupWarning {
       data class ChecksumMismatch : BackupWarning
       data class InvalidRow(val table: String, val id: String, val reason: String) : BackupWarning
   }
   ```

   `TableCounts`/`SkippedRow` als kleine Datenklassen im selben Package
   (`TableCounts(foodItems, recipes, recipeIngredients, diaryEntries, weightLogs: Int`,
   `SkippedRow(table: String, id: String, reason: String)`).

2. **Domain-Repository-Interface** —
   `core/domain/.../repository/BackupRepository.kt`:

   ```kotlin
   interface BackupRepository {
       suspend fun exportTo(
           uri: String,
           options: BackupOptions = BackupOptions(),
       ): Result<BackupExportSummary, DataError.Local>

        suspend fun preview(uri: String): Result<BackupPreview, DataError.Local>

        suspend fun import(uri: String, mode: ImportMode): Result<ImportReport, DataError.Local>

        /** Aktuelle Bestandszahlen für die UI (WP4). */
        suspend fun counts(): Result<BackupExportSummary, DataError.Local>
    }
   ```

   Dazu `core/data/.../backup/BackupLocalDataSource.kt` mit
   `readAll(): BackupRows`, `insertAll(...)`, `replaceAll(...)`,
   `readFoodItemIds()`, `readRecipeIds()`, `countAll()` (Interface +
   `BackupRows` dort, Begründung siehe Vorgaben; Registrierung des Fakes in
   `core/data/src/test`). Und
   `core/domain/.../util/AppVersionProvider.kt`:
   `interface AppVersionProvider { val versionName: String; val versionCode: Int }`.

3. **Fehlertypen** — `core/domain/.../result/DataError.kt`: `DataError.Local` um
   `INVALID_BACKUP` und `UNSUPPORTED_BACKUP_VERSION` erweitern. Das exhaustive
   `when` in `core/presentation/.../uitext/DataErrorUiText.kt` ergänzen und in
   `core/presentation/src/main/res/values/strings.xml` zwei Texte anlegen
   (z. B. `error_invalid_backup`, `error_unsupported_backup_version`).

4. **Settings generisch sichern (A10)** — `core/data/.../datastore/SettingsDataStore.kt`:
   ```kotlin
   /** Alle String-Werte; heute sind das die 5 Goal-Keys. Andere Typen werden
    *  bewusst nicht exportiert (derzeit existieren keine). */
   suspend fun snapshot(): Map<String, String> =
       dataStore.data.map { prefs -> prefs.asMap().filterValues { it is String } }
           .mapValues { (_, value) -> value as String }
           .first()

   suspend fun restore(values: Map<String, String>) {
       values.forEach { (key, value) -> setString(key, value) }
   }
   ```
   Keine Löschung vorhandener Keys — „Ergänzen" (A6) darf keine lokalen
   Settings entfernen.

5. **SAF-Storage** — `core/data/.../backup/ContentResolverBackupStorage.kt`:

   ```kotlin
   interface BackupStorage {
       suspend fun openOutput(uri: String): OutputStream
       suspend fun openInput(uri: String): InputStream
   }

   class ContentResolverBackupStorage(private val context: Context) : BackupStorage {
       override suspend fun openOutput(uri: String): OutputStream =
           withContext(Dispatchers.IO) {
               requireNotNull(context.contentResolver.openOutputStream(Uri.parse(uri))) {
                   "Cannot open output stream for uri"
               }
           }

       override suspend fun openInput(uri: String): InputStream = /* analog */
   }
   ```

    `openOutputStream` ist ab API 19 mit `mode` überladbar — bewusst **`"wt"`**
    (`w` + Atomare Truncate), damit eine zweite Einwahl in dieselbe URI nicht
    Reste der alten Datei stehen lässt; `"w"` ohne Truncate kann Altbestand
    konservieren. `FileProvider` wird **nicht** gebraucht (A1/Section
    [§1.3](#13-warum-kein-export-der-sqlite-datei)).

6. **App-Version** — `core/data/.../backup/PackageManagerAppVersionProvider.kt`:
   `context.packageManager.getPackageInfo(context.packageName, 0)` →
   `versionName` bzw. `versionCode` (`longVersionCode` ab API 28, darunter
   `versionCode`; minSdk ist 25 → mit `@Suppress("DEPRECATION")` arbeiten).
   Bewusst **kein** `BuildConfig` — dafür müsste `buildConfig = true` in
   `app/build.gradle.kts` aktiviert werden, und `:core:data` soll die
   App-Metadaten nicht aus dem Gradle-Modul beziehen.

7. **Mapper** — `core/data/.../backup/BackupMapper.kt`:
   - `FoodItemEntity.toBackup(): BackupFoodItemDto`, `BackupFoodItemDto.toEntity(rowId = 0)`
   - `RecipeEntity.toBackup(): BackupRecipeDto` (ohne Zutaten),
     `RecipeIngredientEntity.toBackup(foodItemId)`,
     `BackupRecipeDto.toEntities(): Pair<RecipeEntity, List<RecipeIngredientEntity>>`
     — **`rowId = 0`**, `isFavorite: Boolean → Int` (`1`/`0`),
     `origIsUserCreated: Boolean?` bleibt nullable
   - `DiaryEntryEntity.toBackup()`, `BackupDiaryEntryDto.toEntity()`
   - `WeightLogEntity.toBackup()`, `BackupWeightLogDto.toEntity()`
   - Gruppierung der `recipe_ingredients` nach `recipeId` im Exporter

8. **LocalDataSource** — `core/data/.../backup/RoomBackupLocalDataSource.kt`:
   `BackupDao` + `SettingsDataStore`, Delegation auf `readAll/insertAll/replaceAll`,
   `runCatchingLocal`-Muster (Log via `AppLogger`, `CancellationException`
   durchreichen, Fehler → `DataError.Local.UNKNOWN`).

9. **Validator** — `core/data/.../backup/BackupValidator.kt` — **alles ohne
   Zugriff auf die Ziel-DB** (Begründung Review-Befund 🔴, Regeln A19):
   - `fun validateFood(item: BackupFoodItemDto): FoodItem?` über
     `FoodItemEntity.toFoodItem()` in `runCatching` (greift
     `require(name.isNotBlank())`, Nährwerte ≥ 0); bei Erfolg den
     `FoodItem` zurückgeben — die validen Ergebnisse werden vom Importer als
     `validFoods: Map<String, FoodItem>` gesammelt
   - `fun validateRecipe(recipe: BackupRecipeDto, validFoods: Map<String, FoodItem>,
     knownExistingFoodIds: Set<String>): Boolean` — **eigene, backup-interne
     Prüfung**, spiegelt die Invarianten des Bestands-Mappers
     (`RecipeEntityMapper.resolveIngredient`): pro Zutat **genau eine** Quelle
     (`foodItemId XOR recipeSnapshot`), `rawWeight > 0` bzw.
     `servingCount > 0`, Auflösbarkeit jeder Food-Referenz gegen
     `validFoods` ∪ (nur MERGE) `knownExistingFoodIds`, Zutatenliste nicht
     leer, `portions > 0`. **Niemals** `RecipeEntity.toRecipe(...)` mit
     `foodLocalDataSource` — dieser Mapper hängt an der Ziel-DB und würde auf
     einer leeren DB (`REPLACE`, frischer Start) **jedes** Rezept verwerfen
     (Review-Befund 🔴)
   - `fun validateDiaryEntry(entry: BackupDiaryEntryDto): Boolean` über
     `DiaryEntryEntity.toDiaryEntry()` (greift `MealSlot.valueOf`,
     `LocalDate.parse`, Snapshot-Dekodierung, Food/Recipe-XOR)
   - `fun validateWeightLog(log: BackupWeightLogDto): Boolean` (`kilograms > 0`,
     parsebares Datum)
   - Jede Ablehnung erzeugt `SkippedRow(table, id, reason)` mit konkreter
     Begründung (z. B. `"unbekannter MealSlot: SUPPER"`, `"kcal negativ"`)

10. **Exporter** — `core/data/.../backup/BackupExporter.kt`:
    - Sections lesen, `food_items` gemäß A5 filtern:
      `isUserCreated == true` **oder** `isFavorite == true` (Favoriten werden
      **immer** exportiert, Nutzerentscheidung A5) **oder** ID in
      (Rezept-Zutat-IDs ∪ Tagebuch-`foodItemId`s) — bei
      `includeCachedOpenFoodFactsItems == true` alles
    - `BackupDocumentDto` bauen: `formatVersion = BackupCodec.FORMAT_VERSION`,
      `appVersionName`/`appVersionCode` aus `AppVersionProvider`,
      `exportedAtEpochMillis = clock()`, `dbSchemaVersion` **nur als Info**
      (ein `EZDatabase`-Zugriff nur für `openHelper.readableDatabase.version` —
      oder, falls das unnötig kompliziert wird, als Konstante aus `EZDatabase`
      dokumentiert; der Wert wird beim Import **nicht** ausgewertet),
      `settings` als **nach Key sortierte** Liste, `checksum = BackupCodec.checksum(content)`
    - Schreiben über `BackupStorage.openOutput(uri)` mit `use { }`; UTF-8
    - Ergebnis `BackupExportSummary` mit den tatsächlich geschriebenen Counts

11. **Importer** — `core/data/.../backup/BackupImporter.kt`:
    - `preview(uri)`: lesen → `BackupCodec.decode` → Fehler nach `DataError.Local`
      mappen → Counts + Datumsspanne (aus `diaryEntries.date` und `weightLogs.date`)
      + Warnungen (Prüfsumme, Zeilenprobleme) — **kein** Schreibzugriff
    - `import(uri, mode)`: lesen → dekodieren → **Stufen 4–5 gemäß §3.4**:
      validen Foods sammeln (`validFoods`), Rezepte gegen `validFoods` +
      Known-IDs prüfen, Tagebucheinträge mappen. Known-IDs je Modus nach A19:
      MERGE = bestehende IDs (per `readFoodItemIds()`/`readRecipeIds()`) ∪
      gültige Backup-IDs, REPLACE = nur gültige Backup-IDs. Unbekannte
      `foodItemId`/`recipeId` bei Tagebucheinträgen auf `null` setzen
      (Snapshot bleibt Inhalt) **und** `droppedReferences` zählen; Einträge
      ohne dekodierbaren Snapshot werden als `skippedRows` verworfen →
      `insertAll` bzw. `replaceAll` **in einer** Transaktion → **erst nach
      erfolgreichem Commit** Settings via `restore()` (A11, §3.4) →
      `ImportReport` mit `inserted` **und** `skippedDuplicates` aus
      `BackupInsertReport` (A18)

12. **Repository + Koin** — `core/data/.../backup/RoomBackupRepository.kt` und
    `core/data/.../di/BackupDataModule.kt` (Koin-Module liegen **flach** in
    `core/data/.../di/`, wie `FoodDataModule`/`GoalDataModule`):

    ```kotlin
    val backupDataModule = module {
        single<BackupStorage> { ContentResolverBackupStorage(androidContext()) }
        single<AppVersionProvider> { PackageManagerAppVersionProvider(androidContext()) }
        single<BackupLocalDataSource> { RoomBackupLocalDataSource(get(), get()) }
        single<BackupRepository> { RoomBackupRepository(get(), get(), get(), get(), get()) }
    }
    ```

    Registrieren in `app/.../di/AppModule.kt` (`includes(...)`, alphabetisch
    einordnen). `clock: () -> Long = { System.currentTimeMillis() }` als
    Konstruktor-Parameter **mit Default** wie in `OfflineFirstFoodRepository`
    (testbar).

13. **Test-Infrastruktur** — `core/data/build.gradle.kts`:
    - Test-Dependencies `robolectric`, `room-testing`, `androidx-test-core`,
      `androidx-test-runner` ergänzen und
      `testOptions { unitTests.isIncludeAndroidResources = true }`
      setzen (Muster `core/database/build.gradle.kts:13-21`).
    - **JUnit-Engines explizit deklarieren** (Muster
      `core/database/build.gradle.kts:33-43`):
      `testRuntimeOnly(libs.junit.jupiter.engine)`,
      `testRuntimeOnly(libs.junit.vintage.engine)` (**erst das Vintage-Engine
      läuft Robolectric-`@RunWith`-Tests**, die als JUnit4-Klassen auf dem
      JUnit5-Plattform-Launcher landen) und
      `testRuntimeOnly(libs.junit.platform.launcher)`. Ohne explizite
      Engine-Deklaration ist das Verhalten gradleseitig undefiniert — der
      bisherige Zustand in `:core:data` (acht JUnit5-Testdateien ohne
      Engine-Dependency) ist nur durch die implizite Ebene der AGP erklärbar
      und wird hier nicht weitergeführt.
    - Neu: `core/data/src/test/resources/robolectric.properties` mit `sdk=34`.

### Dateien

**Neu**
- `core/domain/.../model/backup/{ImportMode,BackupOptions,BackupExportSummary,BackupPreview,ImportReport,BackupWarning,TableCounts,SkippedRow}.kt`
- `core/domain/.../repository/BackupRepository.kt`
- `core/domain/.../util/AppVersionProvider.kt`
- `core/data/.../backup/{BackupLocalDataSource,BackupRows,BackupMapper,BackupValidator,BackupExporter,BackupImporter,RoomBackupLocalDataSource,ContentResolverBackupStorage,PackageManagerAppVersionProvider,RoomBackupRepository}.kt`
- `core/data/.../di/BackupDataModule.kt`
- `core/data/src/test/.../backup/BackupExporterTest.kt`
- `core/data/src/test/.../backup/BackupImporterTest.kt`
- `core/data/src/test/.../backup/BackupRoundTripTest.kt`
- `core/data/src/test/resources/robolectric.properties`

**Geändert**
- `core/domain/.../result/DataError.kt`
- `core/data/.../datastore/SettingsDataStore.kt`
- `core/presentation/.../uitext/DataErrorUiText.kt`
- `core/presentation/src/main/res/values/strings.xml`
- `core/data/build.gradle.kts`
- `app/.../di/AppModule.kt`

### Tests

`BackupRoundTripTest` (Robolectric + In-Memory-Room + `@TempDir`-DataStore +
In-Memory-`BackupStorage`) ist der Kern dieses WPs:

- **Round-Trip Merge:** Export → alles löschen → Import `MERGE` → Counts stimmen
  mit dem Export überein; ein Food-Feld (`isFavorite`, `lastUsedAt`, `orig*`)
  und ein Tagebuch-Snapshot-JSON sind **byte-gleich**.
- **Merge überschreibt nicht:** nach einem lokalen `isFavorite`-Toggle bleibt
  dieser Wert beim Import erhalten (A6).
- **Round-Trip Replace:** lokale Daten werden vollständig entfernt, das Backup
  steht danach exakt.
- **Referenzbereinigung:** Backup mit `diaryEntry.foodItemId` ohne zugehöriges
  Food → Import erfolgreich, `foodItemId == null`, `droppedReferences == 1`, und
  der Eintrag rendert weiterhin über seinen `foodSnapshot`.
- **Rezept mit food-Referenz, die weder Backup noch Ziel-DB kennt** → das
  Rezept wird als `skippedRows` verworfen, **nicht** als `droppedReferences`
  gezählt (A19 — Zutaten werden nur mit auflösbaren Referenzen akzeptiert).
- **Favoriten-Export (A5):** ein OFF-Lebensmittel mit
  `isUserCreated == false`, `isFavorite == true` erscheint im Export, auch ohne
  Referenz; ein nicht favorisiertes, nicht eigenes, unreferenziertes nicht.
- **MERGE-Zweitimport (A7/A18/A19):** Import desselben Dokuments zweimal →
  zweite Runde: `inserted == 0` in allen Tabellen,
  `skippedDuplicates` enthält alle Rezepte inkl. deren Zutaten — insbesondere
  **exakt dieselbe** `recipeIngredients`-Anzahl wie vorher (keine doppelten
  Zutatenzeilen).
- **REPLACE-Zweitimport:** nach `replaceAll` bleiben alle Zutaten exakt
  (keine Restbestände, keine Duplikate).
- **Kaputte Zeilen:** Tagebucheintrag mit `slot = "SUPPER"` und ein Food mit
  `nutriKcal = -1` → beide werden übersprungen, stehen mit Begründung in
  `ImportReport.skippedRows`, der Rest wird importiert (A11 — **kein** Abbruch).
- **Unlesbarer Snapshot** (`foodSnapshot = "{kaputt"`) → Zeile übersprungen.
- **`backup-v1-evolved.json`** in die echte DB importieren → erfolgreich
  (Ende-zu-Ende-Nachweis der Forward-Kompatibilität über alle Schichten).
- **Falsche Datei** → `Result.Error(DataError.Local.INVALID_BACKUP)`;
  **Zukunftsformat** → `UNSUPPORTED_BACKUP_VERSION`; **abgeschnitten** →
  `INVALID_BACKUP`.
- **Preview schreibt nicht:** `preview(uri)` verändert keine der fünf Tabellen.
- **Settings:** `goal_*`-Keys nach `restore()` identisch; Snapshot/Restore ist
  bei leerem Store eine No-Op.
- **Settings erst nach Commit:** das Fake-`BackupLocalDataSource` wirft beim
  `insertAll` einen Fehler → `import()` liefert `Result.Error` **und**
  `restore()` wurde nicht aufgerufen (A11).

`BackupExporterTest` / `BackupImporterTest` (mit Fake-`BackupLocalDataSource`):
- Export **ohne** OFF-Cache enthält nur eigene + Favoriten- + referenzierte
  Foods (A5), **mit** Schalter alle.
- `REPLACE` verwendet `replaceAll`, `MERGE` verwendet `insertAll`.
- **REPLACE-Known-IDs (A19):** Tagebuch-Eintrag verweist auf ein Food, das nur
  im Backup existiert → `droppedReferences == 0`, `foodItemId` erhalten.
- `CancellationException` wird nicht in `DataError` umgewandelt.

`SettingsDataStoreTest`: `snapshot()` liefert nur String-Werte, `restore()`
schreibt mehrere Keys, `clear()` danach leert vollständig.

**Regressionspflichtig:** alle bestehenden `:core:data`- und
`:core:domain`-Tests unverändert grün.

### Verifikation

```
./gradlew :core:data:test :core:domain:test :core:presentation:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] `core:domain` bleibt Android-frei; `Uri`/`Context` kommen dort nicht vor.
      Backup-seitig liegen dort nur `BackupRepository`, die Domain-Modelle und
      `AppVersionProvider` — **kein** `BackupLocalDataSource`, keine
      `BackupRows`/`BackupInsertReport`, keine Entities (Vorgaben)
- [ ] `DataError.Local` hat die zwei neuen Cases, `DataErrorUiText` ist vollständig
- [ ] Ein Fehler im Backup führt zu `Result.Error`, nie zu einer Exception in
      den ViewModel
- [ ] Der Datenbank-Import läuft vollständig in **einer** Transaktion;
      `restore()` wird erst **nach** erfolgreichem Commit aufgerufen (A11, A16)
- [ ] Merge überschreibt keine bestehende Zeile und setzt `is_favorite` /
      `last_used_at` nicht zurück (A7)
- [ ] Zweimal gleicher MERGE-Import erzeugt keine doppelten
      `recipe_ingredients`-Zeilen; `ImportReport.skippedDuplicates` zählt
      Duplikate getrennt von `skippedRows` (A7/A18/A19, Test)
- [ ] Favoriten (`is_favorite = 1`) werden immer exportiert (A5, Test)
- [ ] Rezept-Zutaten werden backup-intern gegen `validFoods` ∪ Known-IDs
      geprüft; `RecipeEntity.toRecipe(..., foodLocalDataSource)` wird im
      Backup-Pfad nie aufgerufen (A19, Review-Befund 🔴, Test auf leerer DB)
- [ ] `dbSchemaVersion` wird geschrieben, aber beim Import **nicht** ausgewertet (A2)
- [ ] Settings laufen über `SettingsDataStore.snapshot()/restore()`, ohne
      hartkodierte Goal-Keys (A10)
- [ ] `backup-v1-evolved.json` importiert erfolgreich in eine echte DB (Ende-zu-Ende)
- [ ] Kein `FileProvider`, kein Manifest-Eintrag, kein `Context` in `:core:database`
- [ ] Bestehende `:core:data`-/`:core:domain`-Tests unverändert grün

**Commit-Vorschlag:** `feat: BackupRepository mit SAF-Export, Preview, Merge/Replace-Import und Validierung`

---

## WP4 — UI: Modul `:feature:settings:presentation`

**Ziel:** Der komplette sichtbare und bedienbare Backup-Pfad: neues
Settings-Modul, MVI-Screen mit SAF-Launchern, Navigation in `:app` und der
Einstieg über die Diary-TopAppBar.

**Abhängigkeit:** WP3 (`BackupRepository`, `ImportMode`, `BackupPreview`,
`ImportReport`).

### Zu lesen

- **Skills:** `android-presentation-mvi`, `android-compose-ui`, `android-navigation`,
  `android-di-koin`
- `feature/diary/presentation/.../goals/` — **Mustervorlage für State/Action/
  Event/ViewModel/Screen/Nav/DI in einem eigenen Screen**
- `feature/diary/presentation/.../day/DiaryDayScreen.kt:255-285` (TopAppBar,
  bestehendes `IconButton`-Muster für die Ziele-Navigation — dieses wird
  **umbenannt**, nicht dupliziert, siehe Schritt 8)
- `feature/diary/presentation/.../navigation/DiaryRoutes.kt` (Callback-Parameter
  einer Navigation)
- `feature/diary/presentation/.../di/DiaryPresentationModule.kt`
- `feature/diary/presentation/build.gradle.kts` — Vorlage für das neue Modul
- `feature/foodpicker/presentation/.../picker/barcode/BarcodeTab.kt:48`
  (einzige bestehende `rememberLauncherForActivityResult`-Nutzung)
- `core/design-system/.../component/LoadingIndicator.kt`
- `core/presentation/.../uitext/UiText.kt`
- `core/presentation/src/main/java/.../util/Formatting.kt` (`formatValue`)
- `app/.../MainActivity.kt:130-186` (`AppNavHost`), `app/.../di/AppModule.kt`
- `feature/*/presentation/src/main/res/values/strings.xml` (String-Konvention)

### Festgelegte Vorgaben

- **MVI-Striktheit:** `State`/`Action`/`Event`/`ViewModel`, `UiText` für alle
  nutzersichtbaren Texte, `Channel` + `receiveAsFlow` für Events, `viewModelScope`.
- **Der ViewModel hält keinen SAF-Launcher.** Der Screen startet den Launcher
  über ein Event und meldet das Ergebnis per Action zurück — die einzige
  zulässige Android-Interaktion im Presentation-Layer.
- Kein eigener Navigations-Host im Modul: `SettingsScreen` wird über `onNavigateBack`
  verlassen (Muster `goalsGraph`).
- **Kein zweites Zahnrad, kein neues Drawable (A9).** Die Umbenennung
  `onNavigateToGoals` → `onNavigateToSettings` in `DiaryRoutes.kt` und
  `DiaryDayScreen.kt` ist die **einzige** zulässige Änderung an bestehenden
  Diary-Dateien; sonst kein Refactoring im Diary-Modul.
- Strings liegen in der Modul-eigenen `strings.xml` (Projektkonvention), Fehler-
  texte für `DataError` in `core/presentation` (bereits in WP3 ergänzt).
- Kein `FileProvider`, kein Manifest-Eintrag (Library-Modul mit `namespace`).

### Schritte

1. **Modul anlegen** —
   `settings.gradle.kts`: `include(":feature:settings:presentation")`
   (Namen mit Doppelpunkt-Notation wie alle anderen).
   `feature/settings/presentation/build.gradle.kts` nach Vorlage
   `feature/diary/presentation/build.gradle.kts`: Plugins
   `android.library`, `compose`, `koin`, `kotlinx.serialization`; `namespace`
   `com.example.ezmakro.feature.settings.presentation`; Dependencies
   `:core:domain`, `:core:presentation`, `:core:design-system`, `koin-android`,
   `koin-androidx-compose`, `kotlinx-coroutines-android`,
   `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`,
   `navigation-compose`, Test-Deps wie im Diary-Modul. **Kein** `:feature:*`- oder
   `:core:data`-/`-database`-Dependency.

2. **State/Action/Event/ViewModel** —
   `feature/settings/presentation/.../settings/backup/`:

   ```kotlin
   data class BackupState(
       val foodItemCount: Int = 0,
       val recipeCount: Int = 0,
       val diaryEntryCount: Int = 0,
       val weightLogCount: Int = 0,
       val includeCachedItems: Boolean = false,
       val isExporting: Boolean = false,
       val isImporting: Boolean = false,
       val preview: BackupPreview? = null,
       val importReport: ImportReport? = null,
       val pendingMode: ImportMode = ImportMode.MERGE,
   )

   sealed interface BackupAction {
       data class OnIncludeCachedItemsChange(val include: Boolean) : BackupAction
       data object OnExportClick : BackupAction
       data class OnDocumentCreated(val uri: String?) : BackupAction
       data object OnImportClick : BackupAction
       data class OnBackupSelected(val uri: String?) : BackupAction
       data class OnModeChange(val mode: ImportMode) : BackupAction
       data object OnConfirmImportClick : BackupAction
       data object OnDismissPreviewClick : BackupAction
       data object OnDismissReportClick : BackupAction
       data object OnBackClick : BackupAction
   }

   sealed interface BackupEvent {
       data class RequestCreateDocument(val suggestedFileName: String) : BackupEvent
       data object RequestOpenDocument : BackupEvent
       data class ShowMessage(val message: UiText) : BackupEvent
       data object NavigateBack : BackupEvent
   }
   ```

   `BackupViewModel(repository: BackupRepository, clock: () -> Long = { System.currentTimeMillis() })`:
   - `init`: aktuelle Bestandszahlen über
     `repository.counts(): Result<BackupExportSummary, DataError.Local>`
     laden (in WP3 ergänzter Konto-Zugriff; **kein** Einmal-Read eines Flows,
     der sich später ändert, ohne dass die UI es merkt — Fehlerklasse aus
     `docs/Bugfix_Plan_Stale_Snapshots.md`).
   - `OnExportClick` → Event `RequestCreateDocument("ezmakro-backup-<yyyy-MM-dd-HHmm>.json")`
     (Zeitformat über `java.time`, Datum aus `clock()`)
   - `OnDocumentCreated(null)` → **nichts tun** (Nutzer hat abgebrochen)
   - `OnDocumentCreated(uri)` → `isExporting = true`, `repository.exportTo(uri,
     BackupOptions(includeCachedOpenFoodFactsItems = includeCachedItems))`,
     Erfolg → `ShowMessage(Erfolg)`, Fehler → `ShowMessage(error.toUiText())`;
     `isExporting` immer zurücksetzen
   - `OnImportClick` → Event `RequestOpenDocument`
   - `OnBackupSelected(null)` → nichts tun
   - `OnBackupSelected(uri)` → `repository.preview(uri)` → `state.preview` oder Fehlermeldung
   - `OnConfirmImportClick` → `isImporting = true`, `repository.import(uri,
     pendingMode)` → Erfolg: `state.importReport` (und `preview = null`) **und
     die Counts per `counts()` neu laden** (sonst zeigt der Screen nach dem
     Import alte Zahlen, 🟡-Befund); Fehler → Meldung; `isImporting` immer
     zurücksetzen
   - Abbruch-Dialogs setzen `preview`/`importReport` auf `null`
   - `OnModeChange` verändert **nur** `pendingMode` — die Warnung für `REPLACE`
     ist reine UI-Zustandsableitung (`pendingMode == REPLACE`), keine zweite
     Confirm-Action im ViewModel
   - `ShowMessage` **nicht** verschlucken (P3.4-Lehre: Events konsistent behandeln)

3. **Screen** — `BackupScreen.kt` (Root mit `koinViewModel()`, Scaffold mit
   TopAppBar + `onNavigateBack`) und `BackupScreenContent.kt`:
   - „Exportieren"-Karte: Bestandszahlen (`Int`-Counts direkt rendern —
     `formatValue` ist nur für `Double` (Formatting.kt:11)),
     `Switch` „Gecachte OFF-Lebensmittel einschließen", Button
     „Backup erstellen"
   - „Importieren"-Karte: Button „Backup einspielen"
   - `LinearProgressIndicator`/design-system-`LoadingIndicator` bei
     `isExporting`/`isImporting`; Buttons in diesen Zuständen deaktiviert
   - `AlertDialog` für Preview (Datum, App-Version, Format-Version, Counts,
     Zeitraum, Warnungen, Moduswahl als `RadioButton`-Zeilen) und Bestätigung bei
     `REPLACE` („Alle lokal gespeicherten Lebensmittel, Rezepte,
     Tagebucheinträge und Gewichtsdaten werden gelöscht.")
   - `AlertDialog` für den `ImportReport` (eingefügt, Duplikate übersprungen,
     ungültige Zeilen, verworfene Referenzen, Warnungen — alle drei
     Übersprungs-Zahlen getrennt, A18)
   - `SnackbarHost` für `ShowMessage`
   - Launcher:
     ```kotlin
     val createDocument = rememberLauncherForActivityResult(
         ActivityResultContracts.CreateDocument("application/json")
     ) { uri -> viewModel.onAction(BackupAction.OnDocumentCreated(uri)) }
     val openDocument = rememberLauncherForActivityResult(
         ActivityResultContracts.OpenDocument()
     ) { uri -> viewModel.onAction(BackupAction.OnBackupSelected(uri)) }
     ```
     `OpenDocument` bekommt beim Start
     `arrayOf("application/json", "text/plain", "application/octet-stream")` —
     viele Datei-Manager liefern `.json` als `application/octet-stream` oder
     `text/plain`.
   - Events sammeln (`LaunchedEffect` + `viewModel.events` bzw. das
     `ObserveAsEvents`-Muster aus `core/presentation`), `RequestCreateDocument`
     startet `createDocument.launch(event.suggestedFileName)`

4. **`SettingsScreen`** — `feature/settings/.../settings/SettingsScreen.kt`:
   Listen-Einstieg mit **zwei** Zeilen (A9):
   - „Tagesziele" (Icon + Untertitel „Kalorien und Makros pro Tag") →
     `onNavigateToGoals` — führt zum bestehenden Goals-Screen
   - „Backup" (Icon + Untertitel „Daten sichern und wiederherstellen") →
     `onNavigateToBackup`

   Die Struktur ist bewusst erweiterbar (Dark Mode, Einheiten — Backlog).

5. **Navigation** — `feature/settings/.../settings/navigation/Settings.kt`:

   ```kotlin
   @Serializable
   object Settings

   fun NavGraphBuilder.settingsGraph(
       onNavigateBack: () -> Unit,
       onNavigateToGoals: () -> Unit,
       onNavigateToBackup: () -> Unit,
   ) {
       composable<Settings> {
           SettingsRoot(
               onNavigateBack = onNavigateBack,
               onNavigateToGoals = onNavigateToGoals,
               onNavigateToBackup = onNavigateToBackup,
           )
       }
       composable<Backup> { BackupRoot(onNavigateBack = onNavigateBack) }
   }
   ```

   Hinweis aus `DiaryRoutes.kt:17-19`: Das Diary-Modul nutzt bewusst
   **String-Routen** statt typisierter Routen (Edge Case in Navigation 2.9). Für
   das neue Modul gilt dasselbe Muster (`composable("settings")`,
   `composable("settings/backup")`), damit dieses bekannte Risiko nicht entsteht.

6. **DI** — `feature/settings/.../settings/di/SettingsPresentationModule.kt`:
   `val settingsPresentationModule = module { viewModel { BackupViewModel(get()) } }`.
   In `app/.../di/AppModule.kt` in `includes(...)` aufnehmen.

7. **`:app` verdrahten** — `MainActivity.kt`: in `AppNavHost` ergänzen
   ```kotlin
   settingsGraph(
       onNavigateBack = onNavigateBack,
       onNavigateToGoals = { navController.navigate(Goals) },
       onNavigateToBackup = { navController.navigate("settings/backup") },
   )
   ```
   und `feature.settings.presentation.navigation` importieren (das bestehende
   `Goals`-Objekt kommt aus dem Diary-Import, `MainActivity.kt:40`).
   Zusätzlich `app/.../di/AppModule.kt` (`settingsPresentationModule`) und
   **`app/build.gradle.kts`**: `implementation(project(":feature:settings:presentation"))`
   ergänzen (Projekt-Deps, Zeilen 37–46 — ohne diesen Eintrag baut `:app`
   nicht, Review-Befund 🟠).

8. **Diary-Einstieg (A9)** — das **bestehende** Ziele-Zahnrad wird zur
   Settings-Einstiegsstelle:
   - `feature/diary/.../navigation/DiaryRoutes.kt`: vorhandenen Parameter
     `onNavigateToGoals` in `diaryGraph` **und** `dayComposable` **umbenennen**
     zu `onNavigateToSettings: () -> Unit = {}` (Zeilen 68, 77, 98, 118) und an
     `DiaryDayRoot` durchreichen
   - `DiaryDayScreen.kt`: Callback-Parameter umbenennen (Zeilen 66, 105, 201,
     277); das **bestehende** `IconButton` bleibt, wo es ist — nur sein
     Ziel ändert sich
   - `contentDescription` (Zeile 280) von `R.string.diary_configure_goals` auf
     `R.string.diary_settings` umstellen
   - `feature/diary/presentation/src/main/res/values/strings.xml`:
     `diary_settings` = „Einstellungen" ergänzen,
     `diary_configure_goals` entfernen (kein anderer Nutzer, kein Test-Referenz)
   - **Kein** `ic_settings.xml`, kein zweites Icon.

9. **Strings** — `feature/settings/presentation/src/main/res/values/strings.xml`:
   `settings_title`, `settings_goals_title` = „Tagesziele",
   `settings_goals_subtitle`, `settings_backup_title`,
   `settings_backup_subtitle`,
   `backup_export_title`, `backup_import_title`, `backup_include_cached`,
   `backup_create`, `backup_open`, `backup_preview_title`,
   `backup_exported_at`, `backup_app_version`, `backup_format_version`,
   `backup_date_range`, `backup_mode_merge`, `backup_mode_merge_hint`,
   `backup_mode_replace`, `backup_mode_replace_warning`,
   `backup_confirm_import`, `backup_cancel`, `backup_report_title`,
   `backup_report_inserted`, `backup_report_duplicates`,
   `backup_report_skipped`,
   `backup_report_dropped_references`, `backup_warning_checksum`,
   `backup_warning_invalid_row`, `backup_export_success`, `backup_working`.

### Dateien

**Neu**
- `feature/settings/presentation/build.gradle.kts`
- `feature/settings/presentation/src/main/res/values/strings.xml`
- `feature/settings/presentation/.../settings/SettingsScreen.kt`
- `feature/settings/presentation/.../settings/backup/BackupScreen.kt`
- `feature/settings/presentation/.../settings/backup/BackupScreenContent.kt`
- `feature/settings/presentation/.../settings/backup/BackupState.kt`
- `feature/settings/presentation/.../settings/backup/BackupAction.kt`
- `feature/settings/presentation/.../settings/backup/BackupEvent.kt`
- `feature/settings/presentation/.../settings/backup/BackupViewModel.kt`
- `feature/settings/presentation/.../settings/navigation/Settings.kt`
- `feature/settings/presentation/.../settings/di/SettingsPresentationModule.kt`
- `feature/settings/presentation/src/test/.../settings/backup/BackupViewModelTest.kt`
- `feature/settings/presentation/src/test/.../settings/fake/FakeBackupRepository.kt`

**Geändert**
- `settings.gradle.kts`
- `app/build.gradle.kts` (Projekt-Dependency `:feature:settings:presentation`)
- `app/.../MainActivity.kt`
- `app/.../di/AppModule.kt`
- `feature/diary/.../navigation/DiaryRoutes.kt`
- `feature/diary/.../day/DiaryDayScreen.kt`
- `feature/diary/presentation/src/main/res/values/strings.xml`

### Tests

`BackupViewModelTest` (JUnit5 + Turbine + AssertK + `UnconfinedTestDispatcher`,
Fake-`BackupRepository` mit schaltbarem Fehler):

- Export: `OnExportClick` → Event `RequestCreateDocument` mit einem Dateinamen,
  der `ezmakro-backup-` enthält und mit `clock()` deterministisch das Datum trägt
- `OnDocumentCreated(null)` → **kein** Repository-Aufruf, kein Event
- `OnDocumentCreated(uri)` → Erfolg: `isExporting` wieder `false`, `ShowMessage`
  mit Erfolgstext
- `OnDocumentCreated(uri)` bei `Result.Error(DataError.Local.INVALID_BACKUP)` →
  `ShowMessage` mit `R.string.error_invalid_backup`, kein Abbruch
- Import: `OnBackupSelected(uri)` → `preview` im State; zweiter Klick auf
  `OnConfirmImportClick` → `import(uri, MERGE)` aufgerufen, `report` im State
- Nach erfolgreichem Import werden die Counts **neu geladen** (Fake zählt die
  `counts()`-Aufrufe: `init` + nach `import`, 🟡-Befund)
- `OnModeChange(REPLACE)` → `import(uri, REPLACE)`; Abbruch setzt `preview = null`
- `ShowMessage` wird nicht verschluckt; `NavigateBack` bei `OnBackClick`
- Doppelter Klick während `isExporting`/`isImporting` löst keinen zweiten
  Repository-Aufruf aus
- `BackupOptions.includeCachedOpenFoodFactsItems` wird aus dem Switch-Zustand
  durchgereicht (Namensgleichheit mit WP3, 🟠-Befund)

Optional (nur bei verfügbarem Gerät/Emulator): Compose-Test in
`androidTest` für die sichtbaren Zustände (Leerzustand, Preview-Dialog,
Report-Dialog) — die SAF-Runde tenure selbst ist instrumentiert nicht sinnvoll
testbar.

**Regressionspflichtig:** `DiaryDayViewModelTest` und alle Diary-Compose-Tests
unverändert grün (der neue Callback ist ein Default-Parameter).

### Verifikation

```
./gradlew :feature:settings:presentation:test :feature:diary:presentation:test :app:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] `:feature:settings:presentation` hängt **nicht** an `:core:data` oder
      `:core:database` (Gradle-Abhängigkeiten prüfen)
- [ ] Alle nutzersichtbaren Texte kommen aus `strings.xml` über `UiText`
- [ ] Der ViewModel referenziert weder `Uri` noch `ActivityResultContracts`
- [ ] Ein abgebrochener SAF-Dialog löst **keinen** Import/Export aus
- [ ] Der Preview-Dialog zeigt App-Version, Format-Version, Counts und Zeitraum
- [ ] `REPLACE` ist nur nach einem zusätzlichen Bestätigungsdialog wählbar
- [ ] Der Report-Dialog zeigt eingefügte, duplikat-übersprungene, ungültige
      und referenz-verworfene Zeilen getrennt (A18)
- [ ] Nach einem erfolgreichen Import zeigen die Bestandszahlen den neuen
      Stand (Counts-Refresh, Test)
- [ ] `ShowMessage`-Events werden in Snackbar angezeigt (keine verschluckten
      Events, P3.4)
- [ ] Der Einstieg läuft über das **bestehende** Ziele-Zahnrad →
      `SettingsScreen` mit den Zeilen „Tagesziele" und „Backup"; es existiert
      kein zweites Icon und kein `ic_settings.xml` (A9)
- [ ] `onNavigateToSettings` ist in `DiaryRoutes.kt`, `DiaryDayScreen.kt` und
      `MainActivity.kt` einheitlich verdrahtet; `diary_configure_goals` existiert
      nirgends mehr
- [ ] `app/build.gradle.kts` hängt an `:feature:settings:presentation` (baut)
- [ ] Bestehende Diary- und App-Tests unverändert grün

**Commit-Vorschlag:** `feat: Settings-Modul mit Backup-Screen (Export, Preview, Import)`

---

## WP5 — Integrationstests, Härtung und Dokumentation

**Ziel:** Den gesamten Pfad Ende-zu-Ende absichern, die verbleibenden Risiken
benennen und die Phase 12 dokumentarisch abschließen.

**Abhängigkeit:** WP4.

### Zu lesen

- `docs/definition-of-done.md` (DoD-Checkliste — dieser WP arbeitet sie ab)
- `docs/Vollstaendiger_Implementierungsplan.md:19-46` (Statustabelle),
  `:150-158` (Phase 12)
- `docs/roadmap.md:7-31` (Phasentabelle, Meilensteine M4), `:22`, `:52`
  (Verschlüsselungs-/Passwort-Zitate — WP5 streicht sie)
- `docs/scope.md:44-45`, `docs/Scope_und_Features.md:72-73`
  (Passwort-Zusage `:44`/`:72` streichen; Dark-Mode-Backlog bleibt)
- `docs/architecture.md:21-38` (Modulliste, Abhängigkeitsrichtung)
- `docs/coding-style.md` (Regel A12 ergänzen)
- `docs/Bugfix_Plan.md:77-106` (Backlog-Format)
- `docs/adr/ADR-007-backup-format.md` (aus WP2)
- `core/database/.../converter/JsonConverters.kt` (Härtungs-Punkt Snapshots)
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`
  (A17 — nur prüfen, nicht ändern)

### Festgelegte Vorgaben

- **Keine Änderung am bestehenden Snapshot-Dekodier-Verhalten.** Der
  bestehende Gap (`decodeFoodItem`/`decodeRecipe` werfen bei kaputten
  Snapshots) wird **dokumentiert**, nicht in diesem Plan umgebaut — der Import
  verhindert bereits, dass neue solche Zeilen entstehen (A8/A11). Die
  Umstellung der Anzeige-Pfade wäre ein eigener Bugfix-Bugendurchgang.
- `room_master_table`, WAL, `allowBackup` werden nicht angefasst (A17).
- Keine Änderung der Backup-Semantik mehr — wenn dabei etwas auffällt, ist es
  eine **Rückmeldung an den Nutzer**, keine stille Korrektur.

### Schritte

1. **Ende-zu-Ende-Integrationstest** — `BackupRoundTripTest` (Robolectric, reale
   In-Memory-DB, `@TempDir`-DataStore, In-Memory-Storage) um die folgenden
   Szenarien erweitern:
   - Export → **Schema-Änderung simulieren**: in der Test-Datenbank eine Spalte
     hinzufügen/entfernen (über eine Test-Migration oder ein eigenes
     `Migration`-Objekt im Test) → alter Import funktioniert weiterhin, weil
     das Format nicht an die Spalten gebunden ist. Dieser Test ist der
     **eigentliche** Nachweis für „Import muss auch funktionieren, wenn sich
     die Datenbank ändert" und wird als solcher benannt.
   - Export → Import → Export: das zweite Backup ist bis auf `checksum` und
     `exportedAtEpochMillis` identisch mit dem ersten (Idempotenz).
   - Import eines Backups mit 10 000 Tagebucheinträgen (Performance-Smoke,
     keine feste Zeitgrenze, nur Abbruch-worthy).
   - Abbruch mitten im Import (Coroutine-Cancellation) → keine Teiländerung
     (Transaktions-Garantie).

2. **Sanity-Checks gegen die Spät-Änderungs-Pfade** — durchgehen und im Plan
   als Checkliste festhalten, dass spätere Änderungen das Format nicht
   brechen:
   - Neue Tabelle in einer späteren Phase (z. B. `weight_logs` wird befüllt,
     `MealPrep`-Entity) → braucht eine **neue Sektion mit Default `emptyList()`**
     in `BackupContentDto` (A12) plus `formatVersion`-Erhöhung nur dann, wenn
     bestehende Daten semantisch anders gelesen werden müssen.
   - Spaltenumbau in `food_items`/`recipes` → **keine** Formatänderung; nur
     `BackupMapper` anpassen.
   - Neues **optionales** Feld in `FoodItem`/`Recipe` (Domain) → Default
     Pflicht (A12),
     sonst brechen eingebettete Snapshots alter Backups.
   - `Room`-Version hochzählen → **keine** Auswirkung auf das Format (A2).

3. **`docs/Bugfix_Plan.md`** — Backlog-Zeile ergänzen:
   „`JsonConverters.decodeFoodItem`/`decodeRecipe` werfen bei nicht
   dekodierbaren Snapshots (Display-Zeit-Crash). Der Import verhindert neue
   solcher Zeilen (`BackupValidator`), die Anzeige-Pfade sind aber weiterhin
   ungeschützt."

4. **ADR-007 finalisieren** — Status/Stand pflegen, die in WP2 entstandenen
   Regeln mit dem tatsächlichen Code abgleichen (Feldnamen, `TableInsertCounts`,
   Verhalten bei Prüfsummen-Mismatch).

5. **`docs/coding-style.md`** — neue Regel:
   *Neue **optionalen** Felder in `@Serializable`-Modellen brauchen einen
   Default-Wert, damit versionierte Serialisierungen (Backup-Format,
   Tagebuch-Snapshots) älterer Datenstände lesbar bleiben; Pflichtfelder des
   Dokumentrahmens bleiben default-frei und enden als `Corrupted`.* (A12)

6. **`docs/architecture.md`** — `:feature:settings:presentation` in die
   Modulliste, ein kurzer Abschnitt „Backup" mit dem Hinweis auf logischen
   Export (A1), Formatversion (A2) und die Tatsache, dass `:app` die
   Koin-Assemblierung des neuen Modulbereichs übernimmt.

7. **`docs/Vollstaendiger_Implementierungsplan.md`** — Phase 12 auf ✅, den
   Abschnitt „Phase 12 — Backup" durch eine Kurzfassung ersetzen (JSON
   Export/Import, Validierung, **keine** Verschlüsselung) und den Verweis auf
   dieses Dokument aufnehmen.

8. **Scope-Docs von der Passwort-Zusage befreien** (Nutzerentscheidung A3):
   - `docs/scope.md:44` und `docs/Scope_und_Features.md:72` — die Formulierung
     „optional passwortverschlüsselt" strechen bzw. durch „**ohne**
     Verschlüsselung (explizite Entscheidung, s. A3 in
     `docs/Plan_Backup_System.md`)" ersetzen.
   - `docs/roadmap.md:22` („optional verschlüsselt") und `:52`
     („Export (optional Passwort)") entsprechend korrigieren.
   - Die Dark-Mode-/Einheiten-Backlogs (`docs/scope.md:45`,
     `docs/Scope_und_Features.md:73`) bleiben — sie werden durch die
     Settings-Seite aus WP4 anschlussfähig.

9. **`docs/roadmap.md`** — Phase 12 auf ✅ mit Verweis auf
   `docs/Plan_Backup_System.md`, Meilenstein **M4** („Vollständige,
   backup-fähige App") auf ✅.

10. **Statuspflege** — `§4` in diesem Dokument: alle fünf Zeilen auf ✅ mit
    Commit-Hash; Kopfzeile „**Status:**" auf „abgeschlossen" setzen.

### Dateien

**Geändert (Doku)**
- `docs/adr/ADR-007-backup-format.md`
- `docs/Vollstaendiger_Implementierungsplan.md`
- `docs/roadmap.md`
- `docs/architecture.md`
- `docs/coding-style.md`
- `docs/Bugfix_Plan.md`
- `docs/scope.md`
- `docs/Scope_und_Features.md`
- `docs/Plan_Backup_System.md`

**Geändert (Tests)**
- `core/data/src/test/.../backup/BackupRoundTripTest.kt`

### Tests

Keine neuen Testdateien außer dem erweiterten `BackupRoundTripTest`. Die
Abnahme erfolgt über [§5](#5-gemeinsame-verifikation-am-ende) und die
DoD-Checkliste aus `docs/definition-of-done.md`.

### Verifikation

```
./gradlew clean build detekt ktlintCheck
./gradlew :core:database:test :core:data:test :core:domain:test :feature:settings:presentation:test
```

### Akzeptanzkriterien

- [ ] Der Test „Schema-Änderung → alter Import funktioniert" existiert und ist
      benannt — er ist der Nachweis der Kernanforderung
- [ ] Export → Import → Export ist bis auf Zeitstempel/Prüfsumme identisch
- [ ] Eine abgebrochene Transaktion hinterlässt keine Teiländerung
- [ ] ADR-007 stimmt mit dem implementierten Format überein
- [ ] `coding-style.md` enthält die Default-Pflicht (A12)
- [ ] Phasenplan und Roadmap führen Phase 12 als abgeschlossen, **ohne**
      Verschlüsselungs-Zusage; auch `docs/scope.md:44` und
      `docs/Scope_und_Features.md:72` sind bereinigt (WP5, Schritt 8)
- [ ] `docs/Bugfix_Plan.md` enthält den Snapshot-Dekodier-Backlog
- [ ] Kein `TODO` im ausgelieferten Code

**Commit-Vorschlag:** `docs: Phase 12 abschliessen - ADR-007, Roadmap, Architektur und Forward-Kompatibilitaets-Nachweis`

---

## 5. Gemeinsame Verifikation am Ende

Nach WP5 zusätzlich:

```
./gradlew clean build detekt ktlintCheck
```

Manueller Smoke-Test (Gerät/Emulator nötig, sonst auslassen und protokollieren):

1. Zahnrad in der Diary-TopAppBar → „Einstellungen" → Zeilen „Tagesziele" und
   „Backup" → „Tagesziele" führt zum alten Goals-Screen, „Backup" zum
   BackupScreen (A9)
2. Drei eigene Lebensmittel, **ein favorisiertes, nicht selbst erstelltes**
   Lebensmittel, zwei Rezepte (mit Unter-Rezept-Zutat), fünf
   Tagebucheinträge, ein Quick-Add-Eintrag (transient) anlegen
3. Export **ohne** Cache-Schalter → Datei in „Downloads" sichern; Datei in einem
   Editor öffnen: `formatVersion` = 1, **vier** Foods (eigene + Favorit, A5),
   zwei Rezepte, fünf Einträge, fünf Settings
4. App-Daten löschen (Einstellungen → Apps → Speicher) → App neu starten
5. Import → Preview zeigt 4/2/5 und den Zeitraum → **Ergänzen** → Report zeigt
   „5 eingefügt"
6. Tagebuch: alle Einträge inklusive Quick-Add (nur Snapshot) und Rezept-
   Tagesanpassung (`recipe_overridden`) sind wieder da; Rezept-Makros stimmen
7. Ein importiertes Food ist über die **lokale Suche** (FTS) findbar (A13)
8. Noch einmal importieren → Report: **„0 eingefügt"**, die Zeilen stehen in
   `skippedDuplicates` (A18), `skippedRows` bleibt leer; untereinander gibt es
   **keine** doppelten Rezeptzutatenzeilen (Idempotenz)
9. Import mit „Ersetzen" → Warndialog → Datenbestand exakt wie das Backup
10. Export mit aktivem Cache-Schalter → Datei enthält zusätzlich die
    gecachten OFF-Lebensmittel
11. `goals_*`-Werte stehen nach dem Import auf den Werten des Exports
12. Ein absichtlich kaputt gemachtes Backup (`formatVersion: 99`) → verständliche
    Fehlermeldung, kein Crash, lokale Daten unverändert

## 6. Nicht-Ziele

- **Keine Verschlüsselung** (A3) — ausdrückliche Nutzerentscheidung; die
  frühere „optional verschlüsselt"-Zusage aus `docs/scope.md`,
  `docs/Scope_und_Features.md` und `docs/roadmap.md` wird in WP5 gestrichen.
  Eine spätere Erweiterung wäre über `formatVersion` möglich.
- **Kein ZIP/gzip** (A4).
- **Keine Zeitplan-/Auto-Backup-Funktion**, keine Cloud-Integration, kein
  Konto.
- **Keine Teil-Exporte** (z. B. nur einzelne Rezepte, nur ein Zeitraum).
- **Kein Export der Session-Cache-Items** (`OfflineFirstFoodRepository`),
  die gar nicht in der DB stehen.
- **Kein Selective Restore** (einzelne Tabellen zurücksetzen).
- **Keine Änderung an `allowBackup`/`backup_rules.xml`** (A17).
- **Keine Umbauten an den bestehenden Entity→Domain-Mappern** — die Validierung
  nutzt sie nur lesend.
- **Keine Migration des Room-Schemas** — es wird keine neue Spalte und keine
  neue Version eingeführt.

## 7. Dokumentations-Pflege

| Dokument | WP | Änderung |
|---|---|---|
| `docs/adr/ADR-007-backup-format.md` | WP2 anlegen, WP5 finalisieren | Format, `formatVersion`, Prüfsumme, Merge/Ersetzen, Forward-Kompatibilitätsregeln |
| `docs/coding-style.md` | WP5 | Regel: neue `@Serializable`-Felder brauchen Defaults (A12) |
| `docs/Bugfix_Plan.md` | WP5 | Backlog: Snapshot-Dekodier-Gap in `JsonConverters` |
| `docs/architecture.md` | WP5 | `:feature:settings:presentation`, Abschnitt „Backup" |
| `docs/roadmap.md` | WP5 | Phase 12 ✅, M4 ✅, Passwort-/Verschlüsselungs-Zusage streichen (`:22`, `:52`) |
| `docs/scope.md` | WP5 | Passwort-Zusage (`:44`) streichen bzw. ersetzen |
| `docs/Scope_und_Features.md` | WP5 | Passwort-Zusage (`:72`) streichen bzw. ersetzen |
| `docs/Vollstaendiger_Implementierungsplan.md` | WP5 | Phase 12 ✅ + Kurzfassung, Verweis auf diesen Plan |
| `docs/Plan_Backup_System.md` | nach jedem WP | Statuszeile je WP auf ✅ + Commit-Hash |

---

## 8. Agent-Prompts pro WP

Jeder Prompt ist **vollständig und eigenständig** — das Agent-Fenster kennt
diese Unterhaltung nicht. Vor „Verifiziere" steht in jedem Prompt die
Commit-Regel.

---

### Prompt WP1

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lies zuerst
> `docs/Plan_Backup_System.md`, die Abschnitte **„WP1"** (Zeilen mit
> „## WP1 —" bis „## WP2 —"), außerdem „§2 Festgelegte Entscheidungen" (A7,
> A13) und „§3.4 Import-Validierung". Skill `android-data-layer` laden.
>
> Kontext: Es geht um Phase 12 (Backup). Der gesamte Datenlayer für Backup fehlt
> bisher vollständig — kein Export, kein Import, keine All-Tabellen-Queries, kein
> `FileProvider`, keine SAF-Nutzung. Dieser Prompt baut **nur die
> Roh-Datenanbindung** in `:core:database`; **kein JSON, kein Format, kein UI,
> kein Repository** in diesem Schritt.
>
> Umsetzung:
> 1. Lies die in „Zu lesen" genannten Dateien (Entities, DAOs, Schema-JSON 9,
>    `DatabaseTest`-Harness, Fixtures, Detekt-Schwellen).
> 2. Lege `BackupDao` mit genau den im Plan genannten Queries an:
>    5× `SELECT *` (food_items, recipes, recipe_ingredients, diary_entries,
>    weight_logs), 2× ID-Projektionen, 5× `@Insert(onConflict =
>    OnConflictStrategy.IGNORE)` über **Listen-Parameter**, 5× `COUNT(*)`,
>    5× `DELETE FROM`, plus die beiden `@Transaction`-Default-Methoden
>    `insertAll(...)` und `replaceAll(...)`. **Insert-Reihenfolge: Eltern vor
>    Kindern** (food_items → recipes → recipe_ingredients → diary_entries →
>    weight_logs). **Lösch-Reihenfolge: Kinder vor Eltern**
>    (recipe_ingredients, diary_entries, recipes, food_items, weight_logs).
>    `row_id` wird nicht geschrieben (Entities kommen mit `rowId = 0`).
>    **`insertAll` filtert Zutaten auf neu eingefügte Rezepte** (Zeilen mit
>    `recipeIds[i] >= 0`, A7/A19 — sonst doppelte Zutaten beim MERGE-Zweitimport);
>    gefilterte Zeilen zählen als `skipped`.
> 3. Lege `TableInsertCounts` (mit `from(rowIds, preSkipped = 0)` — Room meldet
>    `-1` für per IGNORE übersprungene Zeilen) und `BackupInsertReport` an.
> 4. Ergänze in `core/database/.../di/DatabaseModule.kt` genau ein Binding nach
>    dem bestehenden Muster: `single { get<EZDatabase>().backupDao() }`.
> 5. Schreibe `core/database/src/test/.../dao/BackupDaoTest.kt` (Robolectric,
>    `DatabaseTest`-Harness) mit allen in „Tests" genannten Fällen — insbesondere
>    den **FTS-Nachweis** (nach `insertAll` findet `FoodItemDao.searchByName`
>    importierte Foods), den Nachweis, dass `insertFoodItems` bei doppelter
>    `id` überspringt und dabei `is_favorite`/`last_used_at` **nicht**
>    zurücksetzt, und den **MERGE-Idempotenz-Nachweis**: zweimal identisches
>    `insertAll` erzeugt keine zusätzlichen `recipe_ingredients`-Zeilen,
>    `recipes.skipped` entspricht der Rezeptanzahl (A7/A19).
>
> Wichtige Regeln: Keine Entity-Listen-Query mit mehr als einem Parameter (Detekt
> `LongParameterList`, Schwelle 8). Keine foreign keys deaktivieren. Kein JSON,
> kein `@Serializable`, kein `Context` in `:core:database`. Keine Änderung an
> bestehenden DAOs, Entities, Migrationen oder Tests.
>
> Verifiziere: `./gradlew :core:database:test` und danach
> `./gradlew build detekt ktlintCheck`. **Keine Commits.** Melde: geänderte und
> neue Dateien, Testergebnis, Abweichungen vom Plan, offene Punkte.

---

### Prompt WP2

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lies zuerst
> `docs/Plan_Backup_System.md`, die Abschnitte **„WP2"** (bis „## WP3 —"),
> außerdem „§2 Festgelegte Entscheidungen" (A2, A3, A4, A8, A12, A13, A14) und
> „§3.1 Backup-Datei". Skills `android-data-layer` und `android-error-handling`
> laden. WP1 muss abgeschlossen sein (`BackupDao` existiert).
>
> Kontext: Phase 12 (Backup). Der Export ist ein **logisches JSON-Format, das
> bewusst nicht an Room-Spalten gekoppelt ist**, damit er spätere
> Schema-Änderungen überlebt. Dieser Prompt baut **nur Format + Codec** in
> `:core:data`; noch kein Repository, keine IO, kein UI.
>
> Umsetzung:
> 1. `core/data/build.gradle.kts`: Plugin `alias(libs.plugins.ezmakro.kotlinx.serialization)`
>    ergänzen.
> 2. Lege `core/data/.../backup/BackupArchive.kt` mit **allen im Plan
>    ausgeschriebenen `@Serializable`-DTOs** an. **Optionale** Felder haben
>    Default-Werte (A12); die Pflichtfelder des Rahmens (`format`,
>    `formatVersion`, `appVersion*`, `exportedAtEpochMillis`,
>    `dbSchemaVersion`, `content`) bleiben default-frei, `checksum` ist mit
>    `""` optional. `settings` ist eine **Liste** (nicht `Map`). Es gibt kein
>    `rowId`-Feld und keine FTS-Sektion. `cachedStatus`/`slot` bleiben `String`.
>    Die DTO-Felder sind das CamelCase-Pendant der aktuellen Spalten aus
>    `core/database/.../entity/*.kt` — halte dich bei den Spaltennamen exakt an die
>    dortigen `@ColumnInfo`-Namen.
> 3. Lege `core/data/.../backup/BackupCodec.kt` an: `FORMAT_ID`, `FORMAT_VERSION = 1`,
>    `MIN_SUPPORTED_FORMAT_VERSION = 1`, die `Json`-Instanz
>    (`ignoreUnknownKeys`, `isLenient`, `encodeDefaults`), `encode`,
>    `canonicalContent`, `checksum` (SHA-256, Ergebnis als `"sha256:<hex>"`),
>    `decode` mit der Reihenfolge Parse → **Vorabprüfung `format`/`formatVersion`
>    auf dem rohen `JsonObject`** → striktes Dekodieren → Versionsbereich
>    (§3.4). Typisierte
>    Fehler als `DecodeResult.Failure(DecodeError)` mit `NotAZBackup`,
>    `UnsupportedVersion(found, supportedMax)`, `Corrupted`. Die Prüfsumme ist
>    **kein** Fehlerfall, sondern ein Flag `checksumValid` in
>    `DecodeResult.Success` (`checksum` fehlt ⇒ `false`).
> 4. Lege die drei Fixtures in `core/data/src/test/resources/backup/` an:
>    `backup-v1-minimal.json` (valide, korrekte Prüfsumme),
>    `backup-v1-evolved.json` (unbekannte Felder, unbekannte Sektion,
>    fehlende optionale Felder, unbekannter `cachedStatus`),
>    `backup-corrupt.json` (abgeschnitten).
> 5. Schreibe `core/data/src/test/.../backup/BackupCodecTest.kt` mit **allen** in
>    „Tests" genannten Fällen (JUnit5 + AssertK, ohne Robolectric).
> 6. Lege `docs/adr/ADR-007-backup-format.md` an (Format wie die vorhandenen
>    ADRs, Status „accepted") und trage dort die Forward-Kompatibilitätsregeln
>    aus „§3.1 Backup-Datei" ein.
>
> Wichtige Regeln: Keine Verschlüsselung, keine Kompression. Keine Änderung an
> `FoodItemDao`/`RecipeDao` und keine Nutzung von `BackupDao` in diesem Schritt.
> Bestehende `:core:data`-Tests müssen unverändert grün bleiben.
>
> Verifiziere: `./gradlew :core:data:test` und danach
> `./gradlew build detekt ktlintCheck`. **Keine Commits.** Melde: geänderte und
> neue Dateien, Testergebnis, Abweichungen vom Plan, offene Punkte.

---

### Prompt WP3

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lies zuerst
> `docs/Plan_Backup_System.md`, die Abschnitte **„WP3"** (bis „## WP4 —"),
> außerdem „§2 Festgelegte Entscheidungen" (A5–A8, A10, A11, A14, A16, A18,
> A19), „§3.1", „§3.3" und „§3.4". Skills `android-data-layer` und
> `android-error-handling` laden. WP1 und WP2 müssen abgeschlossen sein
> (`BackupDao` und `BackupCodec` existieren).
>
> Kontext: Phase 12 (Backup). Jetzt kommt der Prozess: Settings sichern, über SAF
> schreiben/lesen, validieren, in **einer** Transaktion importieren. Merge
> ergänzt nur, Replace leert vorher.
>
> Umsetzung:
> 1. `core/domain/.../model/backup/`: `ImportMode`, `BackupOptions`,
>    `BackupExportSummary`, `BackupPreview`, `ImportReport` (inkl.
>    `skippedDuplicates: TableCounts`, A18), `BackupWarning`,
>    `TableCounts`, `SkippedRow` (Signaturen aus dem Plan). `:core:domain`
>    bleibt Android-frei — **kein** `Uri`, **kein** `java.time` ist verboten,
>    `LocalDate` ist erlaubt (reines JVM).
> 2. `core/domain/.../repository/BackupRepository.kt` mit `exportTo(uri: String,
>    options: BackupOptions)` / `preview(uri: String)` /
>    `import(uri: String, mode: ImportMode)` /
>    `counts(): Result<BackupExportSummary, DataError.Local>` — je
>    `Result<…, DataError.Local>`. Der Count-Zugriff ist für den
>    Settings-Screen in WP4 (kein Einmal-Read eines sich ändernden Flows ohne
>    UI-Reaktion, Fehlerklasse aus `docs/Bugfix_Plan_Stale_Snapshots.md`).
>    Ergänze außerdem `AppVersionProvider` in `core/domain/.../util/` und das
>    schmale **`BackupLocalDataSource`-Interface samt `BackupRows` in
>    `core/data/.../backup/`** (Achtung: **nicht** nach `:core:domain` — die
>    Entities und `BackupInsertReport` sind dort nicht verfügbar, siehe
>    „Festgelegte Vorgaben").
> 3. `DataError.Local` um `INVALID_BACKUP` und `UNSUPPORTED_BACKUP_VERSION`
>    erweitern; das exhaustive `when` in
>    `core/presentation/.../uitext/DataErrorUiText.kt` ergänzen und in
>    `core/presentation/src/main/res/values/strings.xml` die zwei Texte anlegen.
> 4. `SettingsDataStore`: `snapshot(): Map<String, String>` (nur String-Werte) und
>    `restore(values: Map<String, String>)` (**ohne** Löschen bestehender Keys).
> 5. `core/data/.../backup/ContentResolverBackupStorage.kt` (Interface
>    `BackupStorage` mit `openOutput`/`openInput` auf `uri: String`, Implementierung
>    über `context.contentResolver`) und
>    `PackageManagerAppVersionProvider.kt` (minSdk 25 → `versionCode` mit
>    `@Suppress("DEPRECATION")`). **Kein** `FileProvider`, **kein** Manifest-Edit.
> 6. `BackupMapper.kt`: Entity↔DTO in beide Richtungen für alle fünf Tabellen.
>    `rowId = 0` beim Import, `recipes.isFavorite: Boolean → Int` bzw.
>    `Int → Boolean`, `origIsUserCreated` bleibt nullable, `recipe_ingredients`
>    werden beim Export nach `recipeId` gruppiert.
> 7. `BackupValidator.kt` — **alles ohne Ziel-DB-Zugriff** (A19):
>    `validateFood(dto): FoodItem?` über `toFoodItem()` in `runCatching`
>    (greift `require(name.isNotBlank())`, Nährwerte ≥ 0, Rückgabe des
>    validen `FoodItem`), `validateRecipe(dto, validFoods,
>    knownExistingFoodIds)` als **eigene backup-interne Prüfung** (genau eine
>    Zutatenquelle, `rawWeight`/`servingCount` > 0, Food-Auflösbarkeit gegen
>    `validFoods` ∪ Known-IDs, `portions > 0`), `validateDiaryEntry(dto)` über
>    `toDiaryEntry()` (`MealSlot.valueOf`, `LocalDate.parse`,
>    Snapshot-Dekodierung), `validateWeightLog(dto)` (`kilograms > 0`,
>    parsebares Datum). **Niemals** `RecipeEntity.toRecipe(…, foodLocalDataSource)`
>    aufrufen — dieser Mapper hängt an der Ziel-DB und würde auf leerer DB jedes
>    Rezept verwerfen (Review-Befund 🔴). Ablehnungen werden zu
>    `SkippedRow(table, id, reason)` mit konkreter Begründung.
> 8. `BackupExporter.kt`: Sections lesen, `food_items` gemäß A5 filtern
>    (eigene Foods **∪ Favoriten** ∪ von Rezepten/Tagebuch referenzierte; bei
>    Schalter **alles**),
>    `settings` **nach Key sortiert** als Liste, `checksum` über den kanonischen
>    Content, `appVersion*` aus `AppVersionProvider`, `exportedAt` aus einem
>    injizierten `clock: () -> Long` mit Default, `dbSchemaVersion` nur als Info.
>    Schreiben über `BackupStorage.openOutput(uri)` mit `use { }`.
> 9. `BackupImporter.kt`: `preview(uri)` liest, dekodiert, prüft (A11 Stufe 1–4),
>    ermittelt Counts + Datumsspanne + Warnungen und schreibt **nichts**.
>    `import(uri, mode)` validiert (Stufe 4), bereinigt Referenzen (Stufe 5,
>    Known-IDs nach A19: MERGE = bestehend ∪ gültige Backup-IDs, REPLACE = nur
>    gültige Backup-IDs; unbekannte `foodItemId`/`recipeId` → `null`, zählen;
>    Einträge ohne dekodierbaren Snapshot überspringen), ruft bei `MERGE`
>    `insertAll`, bei `REPLACE` `replaceAll` — **jeweils in einer Transaktion** —
>    stellt die Settings **erst nach erfolgreichem Commit** via `restore()`
>    wieder her und liefert einen `ImportReport` mit `skippedDuplicates` aus
>    `BackupInsertReport` (A18).
> 10. `RoomBackupLocalDataSource.kt` (DAO + SettingsDataStore, `runCatchingLocal`-
>    Muster aus `RoomDiaryLocalDataSource.kt`, `CancellationException`
>    durchreichen, Fehler → `DataError.Local.UNKNOWN`) und
>    `RoomBackupRepository.kt`. Koin-Modul `backupDataModule` in
>    `core/data/.../di/BackupDataModule.kt` (**flach** in `di/`, wie
>    `FoodDataModule`), registriert in
>    `app/.../di/AppModule.kt` (`includes(…)`, alphabetisch eingeordnet).
> 11. Test-Infrastruktur in `core/data/build.gradle.kts`: `robolectric`,
>     `room-testing`, `androidx-test-core`, `androidx-test-runner` als
>    `testImplementation`, `testOptions { unitTests.isIncludeAndroidResources = true }`,
>    **JUnit-Engines explizit** als `testRuntimeOnly`: `jupiter.engine`,
>    `vintage.engine` (Robolectric-`@RunWith` braucht das Vintage-Engine) und
>    `platform.launcher` (Muster `core/database/build.gradle.kts:33-43`),
>    neu `core/data/src/test/resources/robolectric.properties` mit `sdk=34`.
> 12. Schreibe `BackupRoundTripTest` (Robolectric + In-Memory-Room + `@TempDir`-
>    DataStore + In-Memory-`BackupStorage`) mit **allen** in „Tests" genannten
>    Szenarien, dazu `BackupExporterTest` und `BackupImporterTest` mit einem
>    handgeschriebenen Fake-`BackupLocalDataSource`, und erweitere
>    `SettingsDataStoreTest`.
>
> Wichtige Regeln: Keine Änderung oder Verschiebung bestehender Mapper. Keine
> Verschlüsselung. Keine App-Version aus `BuildConfig` (kein
> `buildConfig = true`). `dbSchemaVersion` nie auswerten. Kein Schließen oder
> Neubauen der Room-Instanz. `seed`/`@Test` für 10 000 Zeilen nicht vergessen,
> falls du den Performance-Smoke in WP3 ziehst (der steht sonst in WP5).
>
> Verifiziere: `./gradlew :core:data:test :core:domain:test :core:presentation:test`
> und danach `./gradlew build detekt ktlintCheck`. **Keine Commits.** Melde:
> geänderte und neue Dateien, Testergebnis, Abweichungen vom Plan, offene Punkte.

---

### Prompt WP4

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lies zuerst
> `docs/Plan_Backup_System.md`, die Abschnitte **„WP4"** (bis „## WP5 —"),
> außerdem „§2 Festgelegte Entscheidungen" (A9) und „§3.2 BackupScreen". Skills
> `android-presentation-mvi`, `android-compose-ui`, `android-navigation`,
> `android-di-koin` laden. WP1–WP3 müssen abgeschlossen sein
> (`BackupRepository` in Koin verfügbar).
>
> Kontext: Phase 12 (Backup). Der Datenlayer steht; jetzt die Oberfläche. Neues
> Feature-Modul `:feature:settings:presentation` — **Backup ist der erste, aber
> nicht der einzige Settings-Bereich** (Dark Mode, Einheiten stehen im Backlog),
> deshalb heißt das Modul `settings`, nicht `backup`.
>
> Umsetzung:
> 1. Neues Gradle-Modul: `settings.gradle.kts` ergänzen,
>    `feature/settings/presentation/build.gradle.kts` nach Vorlage
>    `feature/diary/presentation/build.gradle.kts` (Plugins
>    android.library/compose/koin/kotlinx.serialization, `namespace`
>    `com.example.ezmakro.feature.settings.presentation`, Dependencies
>    `:core:domain`, `:core:presentation`, `:core:design-system`, koin-android,
>    koin-androidx-compose, coroutines-android, lifecycle-viewmodel-compose,
>    lifecycle-runtime-compose, navigation-compose, Test-Deps wie im
>    Diary-Modul). **Kein** `:feature:*`, **kein** `:core:data`, **kein**
>    `:core:database`.
> 2. `BackupState`, `BackupAction`, `BackupEvent`, `BackupViewModel` exakt nach den
>    Signaturen aus dem Plan. Characteristics:
>    - Export läuft über `Event RequestCreateDocument(suggestedFileName)` mit
>      Namen `ezmakro-backup-<yyyy-MM-dd-HHmm>.json` (Zeit aus `clock()`).
>    - `OnDocumentCreated(null)` bzw. `OnBackupSelected(null)` (Nutzer hat den
>      SAF-Dialog abgebrochen) lösen **keinen** Repository-Aufruf und kein Event
>      aus.
>    - `state.preview` wird aus `repository.preview()` gesetzt,
>      `state.importReport` aus `repository.import()`; beide Dialoge werden über
>      Dismiss-Actions geleert.
>    - `OnModeChange` verändert nur `pendingMode`; die `REPLACE`-Warnung ist
>      reine UI-Ableitung.
>    - `isExporting`/`isImporting` werden in jedem Pfad (auch im Fehlerfall)
>      zurückgesetzt und verhindern Doppelaufrufe.
>    - Bestandszahlen beim Start über `repository.counts()` (suspend, aus WP3)
>      laden und **nach jedem erfolgreichen Import neu laden** — der Screen
>      zeigt sonst alte Zahlen (Fehlerklasse aus
>      `docs/Bugfix_Plan_Stale_Snapshots.md`).
>    - `ShowMessage` wird gesendet und nicht verschluckt (P3.4).
> 3. `BackupScreen` (Root mit `koinViewModel()`, Scaffold, TopAppBar mit
>    `onNavigateBack`, `SnackbarHost`) und `BackupScreenContent` mit:
>    Export-Karte (Bestandszahlen via `formatValue()`, `Switch`
>    „Gecachte OFF-Lebensmittel einschließen", Button „Backup erstellen"),
>    Import-Karte („Backup einspielen"), Ladeindikator via
>    `core/design-system` `LoadingIndicator`, Preview-`AlertDialog` (Datum,
>    App-Version, Format-Version, Counts, Zeitraum, Warnungen, Moduswahl per
>    `RadioButton`), Bestätigungs-`AlertDialog` bei `REPLACE`, Report-`AlertDialog`
>    und die beiden ActivityResult-Launcher
>    (`CreateDocument("application/json")`, `OpenDocument()` mit
>    `arrayOf("application/json", "text/plain", "application/octet-stream")`).
>    `contentDescription` für alle Icons.
> 4. `SettingsScreen` mit **zwei** Zeilen (A9): „Tagesziele" →
>    `onNavigateToGoals` (bestehender Goals-Screen) und „Backup" →
>    `onNavigateToBackup`.
> 5. `navigation/Settings.kt` mit `@Serializable object Settings` und
>    `settingsGraph(onNavigateBack, onNavigateToGoals, onNavigateToBackup)`.
>    Nutze **String-Routen**
>    (`"settings"`, `"settings/backup"`) nach dem Muster aus
>    `feature/diary/.../navigation/DiaryRoutes.kt:17-19` (dort ist der
>    `KClass.serializer()`-Edge-Case in Navigation 2.9 dokumentiert).
> 6. `settings/di/SettingsPresentationModule.kt` mit
>    `viewModel { BackupViewModel(get()) }`; in `app/.../di/AppModule.kt` in
>    `includes(…)` aufnehmen.
> 7. `app/.../MainActivity.kt`: `settingsGraph(onNavigateBack,
>    onNavigateToGoals = { navController.navigate(Goals) },
>    onNavigateToBackup = { navController.navigate("settings/backup") })` in
>    `AppNavHost` registrieren; **`app/build.gradle.kts`** um
>    `implementation(project(":feature:settings:presentation"))` ergänzen
>    (Projekt-Deps — ohne diesen Eintrag baut `:app` nicht).
> 8. Diary-Einstieg (A9) — das **bestehende** Ziele-Zahnrad wird umbenannt:
>    `onNavigateToGoals` → `onNavigateToSettings: () -> Unit = {}` in
>    `DiaryRoutes.kt` (`diaryGraph` **und** `dayComposable`) und in
>    `DiaryDayScreen.kt`; `contentDescription` (Zeile 280) von
>    `R.string.diary_configure_goals` auf `R.string.diary_settings` umstellen;
>    String `diary_settings` = „Einstellungen" ergänzen,
>    `diary_configure_goals` entfernen. **Kein** zweites `IconButton`, **kein**
>    `ic_settings.xml`, kein neues Drawable.
> 9. `feature/settings/presentation/src/main/res/values/strings.xml` mit allen im
>    Plan gelisteten Strings. **Alle** nutzersichtbaren Texte als `UiText`
>    ausgeben (kein hartkodiertes `Text("…")`).
> 10. Schreibe `BackupViewModelTest` (JUnit5 + Turbine + AssertK +
>     `UnconfinedTestDispatcher`, Fake-`BackupRepository` mit schaltbarem Fehler)
>     mit allen in „Tests" genannten Fällen, sowie das Fake unter
>     `src/test/.../settings/fake/`.
>
> Wichtige Regeln: Der ViewModel referenziert **weder** `Uri` noch
> `ActivityResultContracts`. Kein `FileProvider`, kein Manifest-Eintrag, **kein**
> neues Drawable (`ic_settings.xml` existiert nicht), keine
> Änderung an bestehenden Diary-ViewModels. Bestehende Diary-/App-Tests müssen
> unverändert grün bleiben.
>
> Verifiziere:
> `./gradlew :feature:settings:presentation:test :feature:diary:presentation:test :app:test`
> und danach `./gradlew build detekt ktlintCheck`. Instrumented-Tests nur, wenn
> ein Gerät/Emulator verfügbar ist. **Keine Commits.** Melde: geänderte und neue
> Dateien, Testergebnis, Abweichungen vom Plan, offene Punkte.

---

### Prompt WP5

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lies zuerst
> `docs/Plan_Backup_System.md`, die Abschnitte **„WP5"** (bis „## 5.") sowie
> „§5 Gemeinsame Verifikation", „§6 Nicht-Ziele" und „§7 Dokumentations-Pflege".
> Skills `android-data-layer` und `android-testing` laden. WP1–WP4 müssen
> abgeschlossen sein.
>
> Kontext: Phase 12 (Backup) wird abgeschlossen. Zwei Ziele: die Kernanforderung
> **„Import muss auch funktionieren, wenn sich die Datenbank ändert"** als
> Test festhalten und die Phase dokumentarisch abschließen.
>
> Umsetzung:
> 1. Erweitere `core/data/src/test/.../backup/BackupRoundTripTest.kt`:
>    a. **Schema-Änderungs-Test (Kernnachweis):** Export erstellen, in der
>       Test-Datenbank eine Spalte hinzufügen/entfernen (Test-Migration oder
>       separates In-Memory-Schema), dann den alten Import ausführen → er muss
>       weiterhin erfolgreich sein. Benenne den Test so, dass die Kernanforderung
>       direkt ablesbar ist.
>    b. Export → Import → Export: das zweite Backup ist bis auf
>       `exportedAtEpochMillis` und `checksum` identisch mit dem ersten.
>    c. Abbruch mitten im Import (Coroutine-Cancellation) → keine Teiländerung.
>    d. Großer Datenbestand (10 000 Tagebucheinträge) als Performance-Smoke.
> 2. Gehe die Checkliste in „Schritte 2" des Plans durch und dokumentiere sie als
>    Kommentar **am Test** (bzw. in `ADR-007`), welche späteren Änderungen das
>    Format nicht brechen (neue Tabelle braucht neue Sektion mit Default;
>    Spaltenumbau nur `BackupMapper`; neues Domain-Feld braucht Default; Room-
>    Version ist egal).
> 3. `docs/Bugfix_Plan.md`: Backlog-Zeile zu `JsonConverters.decodeFoodItem` /
>    `decodeRecipe` ergänzen (wirksamer Fehlerfall bei der Anzeige, Import
>    verhindert nur neue Fälle).
> 4. `docs/adr/ADR-007-backup-format.md` mit dem implementierten Code abgleichen
>    (Feldnamen, `TableInsertCounts`, Verhalten bei Prüfsummen-Mismatch) und
>    Stand/Status pflegen.
> 5. `docs/coding-style.md`: Regel ergänzen — neue **optionalen** Felder in
>    `@Serializable`-Modellen brauchen Default-Werte, damit versionierte
>    Serialisierungen (Backup-Format, Tagebuch-Snapshots) älterer Datenstände
>    lesbar bleiben; Pflichtfelder des Dokumentrahmens bleiben default-frei.
> 6. `docs/architecture.md`: `:feature:settings:presentation` in die Modulliste,
>    kurzer Abschnitt „Backup" (logischer Export statt DB-Kopie, Formatversion).
> 7. `docs/Vollstaendiger_Implementierungsplan.md`: Phase 12 auf ✅, Abschnitt
>    „Phase 12 — Backup" durch eine Kurzfassung ersetzen (JSON Export/Import,
>    Validierung, **keine** Verschlüsselung), Verweis auf dieses Dokument.
> 8. **Passwort-Zusage streichen** (Nutzerentscheidung A3): `docs/scope.md:44`,
>    `docs/Scope_und_Features.md:72` („optional passwortverschlüsselt") und
>    `docs/roadmap.md:22`/`:52` („optional verschlüsselt" bzw. „Export
>    (optional Passwort)") bereinigen; die Dark-Mode-Backlogs
>    (`docs/scope.md:45`, `docs/Scope_und_Features.md:73`) bleiben.
> 9. `docs/roadmap.md`: Phase 12 auf ✅ mit Verweis, Meilenstein **M4** auf ✅.
> 10. `docs/Plan_Backup_System.md`: In „§4 WP-Übersicht" alle fünf Statuszeilen
>    auf ✅ mit Commit-Hash setzen (die Hashes trägt der Nutzer beim Commit ein,
>    oder du lässt die Zeilen auf ✅ ohne Hash, wenn der Commit noch aussteht) und
>    die Kopfzeile „**Status:**" auf „abgeschlossen" ändern.
>
> Wichtige Regeln: **Keine** Änderung am Snapshot-Dekodier-Verhalten
> (`JsonConverters`), keine Änderung an `allowBackup`/`backup_rules.xml`, keine
> Änderung der Backup-Semantik. Wenn dir beim Review ein echter Fehler in der
> Backup-Logik auffällt: **melden, nicht eigenmächtig ändern** — die Korrektur
> braucht ein eigenes WP.
>
> Verifiziere: `./gradlew clean build detekt ktlintCheck` sowie
> `./gradlew :core:database:test :core:data:test :core:domain:test :feature:settings:presentation:test`.
> **Keine Commits.** Melde: geänderte Dateien, Testergebnis, Abweichungen vom
> Plan, offene Punkte.