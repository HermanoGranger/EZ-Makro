# Plan — Backup-System (JSON Export/Import)

**Status:** Planung abgeschlossen, Freigabe für WP1 durch den Nutzer.
**Stand:** 2026-10-05 (auf Basis Commit `0916d21`).
**Bezug:** `docs/architecture.md`, `docs/coding-style.md`, `docs/domain-model.md`,
`docs/definition-of-done.md`, `docs/roadmap.md`, `docs/Scope_und_Features.md`,
`docs/Bugfix_Plan.md`, `docs/adr/ADR-002-offline-first-room.md`,
`docs/adr/ADR-004-off-eintragsstatus.md`.

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
All-Tabellen-Abfragen. `recipe_ingredients`, `diary_entries` und `weight_logs`
haben **keine** `SELECT *`-Abfrage; kein DAO hat `count()` oder `deleteAll()`.

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
  Settings-Screen; das Backlog in `docs/Bugfix_Plan.md:103` nennt Dark Mode und
  Einheiten als künftige Settings).
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
| **A5** | **Export-Umfang:** eigene Lebensmittel (`is_user_created = 1`) **plus** alle Lebensmittel, die von Rezepten oder Tagebucheinträgen referenziert werden. Der OFF-Cache-Nichtnutzer-Zugriff ist ein **Schalter, Default aus**. Rezepte (+ Zutaten), Tagebucheinträge, Gewichtslogs und **alle** Settings werden immer exportiert. |
| **A6** | **Zwei Import-Modi, im Preview-Dialog wählbar:** *Ergänzen* (Default — fügt nur Fehlendes hinzu, überschreibt nie lokale Daten) und *Ersetzen* (leert die fünf Tabellen FK-sicher, spielt das Backup 1:1 ein, mit deutlicher Warnung und Bestätigung). |
| **A7** | **Merge = `@Insert(onConflict = OnConflictStrategy.IGNORE)`** auf den natürlichen Schlüsseln (`food_items.id`, `recipes.id`, `diary_entries.id`, `weight_logs.date`). `row_id` wird beim Import auf `0` gesetzt (autoGenerate), damit kein PK-Konflikt entsteht und die FTS-Doc-IDs gültig bleiben. Damit braucht es **keine** Upsert-Query mit 20+ Parametern — Detekt `LongParameterList` (Schwelle 8) würde das verbieten, und `FoodItemDao.upsert` (`:22`, `@Suppress("LongParameterList")`) setzt `is_favorite`/`last_used_at` beim Insert bewusst auf `0`/`NULL` — unbrauchbar für einen Import. |
| **A8** | **Snapshots bleiben opake Strings.** `food_snapshot`, `recipe_snapshot`, `recipe_snapshot_json` werden als `String?` im Format geführt und unverändert geschrieben/gelesen. Die Dekodierbarkeit wird beim Import **einmal** geprüft (A11), damit keine unlesbaren Zeilen in die DB gelangen. |
| **A9** | **UI in einem neuen Modul `:feature:settings:presentation`** (nicht `:feature:backup`) — Backup ist der erste, aber nicht der einzige Settings-Bereich (Dark Mode, Einheiten stehen im Backlog). Einstieg über ein neues Icon in der Diary-TopAppBar neben dem Ziele-Icon; `SettingsScreen` ist die Sammel-Seite, `BackupScreen` die Unterseite. |
| **A10** | **Settings generisch sichern** über neue `snapshot()`/`restore()`-Funktionen in `SettingsDataStore` — **nicht** die 5 Goal-Keys hartkodieren. Damit sind spätere Settings (Einheiten, Theme) automatisch im Backup. |
| **A11** | **Validierung in vier Stufen** (siehe [§3.4](#34-import-validierung)), Preview (Dry-Run) vor dem eigentlichen Import, gesamter Import in **einer** Room-Transaktion. Zeilen, die den Domain-Invarianten widersprechen, werden **übersprungen und im Report gelistet** — nie hart abgebrochen. |
| **A12** | **Neue Projektregel:** Jedes neue Feld in einem `@Serializable`-Modell (Domain **und** Backup-DTOs) braucht einen **Default-Wert**. Sonst bricht das Dekodieren alter Backups. Wird in `docs/coding-style.md` und `ADR-007` festgeschrieben. |
| **A13** | **`food_items_fts` wird nicht exportiert.** Der Index wird von den Room-Trigger `room_fts_content_sync_food_items_fts_*` beim Import automatisch mitgepflegt — der WP1-Test weist das nach. `row_id` wird nicht exportiert (kein fachlicher Inhalt). |
| **A14** | **Prüfsumme:** SHA-256 über das kanonisch re-encodete `content`-Objekt, geschrieben als `"sha256:<hex>"`. Für Determinismus ist `settings` eine **sortierte Liste** (nicht `Map`). Ein Mismatch ist eine **Warnung** im Report (kein Abbruch) — eine abgeschnittene Datei fällt ohnehin schon beim JSON-Parser auf und ist dann ein harter Fehler. |
| **A15** | **`weight_logs` gehört ins Backup**, obwohl die Funktion erst in Phase 10 kommt (`WeightLogDao` + Entity existieren bereits, `:core:database`). |
| **A16** | **Kein App-Neustart nach dem Import.** Der Import läuft über die DAOs, bestehende Flows emittieren von selbst. Kein Eingriff in die Room-DB-Instanz, kein Schließen der Datenbank. |
| **A17** | **Android Auto Backup bleibt unangetastet** (`android:allowBackup="true"` in `app/src/main/AndroidManifest.xml`, `backup_rules.xml` leer). Ob der OFF-Cache künftig ausgeschlossen wird, ist eine separate Entscheidung und **nicht** Teil dieses Plans. |

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
| Vorwärtskompatibilität | Alle Felder mit Default, `Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }` |
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
Report-Dialog: eingefügt / übersprungen / ersetzte Referenzen / Warnungen.

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
| 1 | JSON parsebar; `format == "ezmakro.backup"` | `DataError.Local.INVALID_BACKUP` |
| 2 | `MIN_SUPPORTED_FORMAT_VERSION <= formatVersion <= FORMAT_VERSION` | `DataError.Local.UNSUPPORTED_BACKUP_VERSION` |
| 3 | Prüfsumme über `content` | `BackupWarning.ChecksumMismatch`, Import läuft weiter |
| 4 | **Zeilenvalidierung** über die bestehenden Entity→Domain-Mapper in `try`/`catch` — dadurch greifen **alle** `require`-Invarianten automatisch: `name.isNotBlank()`, `portions > 0`, `kilograms > 0`, Nährwerte ≥ 0, `MealSlot.valueOf`, parsebares Datum, dekodierbare Snapshots | Zeile wird **übersprungen** und mit Grund in `ImportReport` gelistet |
| 5 | **Referenzielle Prüfung:** `foodItemId`/`recipeId`, die in der Ziel-DB (nach Modus) fehlen, werden auf `null` gesetzt — der Snapshot hält den Inhalt, sonst gäbe es eine FK-Violation | Zähler `droppedReferences` im Report |

Gesamter Schreibvorgang in **einer** Room-Transaktion (`@Transaction`-Methode in
`BackupDao`) — entweder alles oder nichts.

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
           val ingredientIds = insertRecipeIngredients(ingredients)
           val entryIds = insertDiaryEntries(entries)
           val weightIds = insertWeightLogs(weightLogs)
           return BackupInsertReport(
               foodItems = TableInsertCounts(foodIds),
               recipes = TableInsertCounts(recipeIds),
               recipeIngredients = TableInsertCounts(ingredientIds),
               diaryEntries = TableInsertCounts(entryIds),
               weightLogs = TableInsertCounts(weightIds),
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
   /** Room meldet für per IGNORE übersprungene Zeilen `-1`. */
   data class TableInsertCounts(
       val inserted: Int,
       val skipped: Int,
   ) {
       companion object {
           fun from(rowIds: List<Long>): TableInsertCounts =
               TableInsertCounts(
                   inserted = rowIds.count { it >= 0L },
                   skipped = rowIds.count { it == -1L },
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
Funktion `BackupContent` ↔ `String` und vollständig ohne Android testbar.

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

- **Jedes Feld mit Default-Wert** (A12), damit `formatVersion`-1-Dokumente auch
  nach Ergänzungen dekodieren.
- `settings` ist eine **sortierte Liste** `List<BackupSetting>`, keine `Map` (A14).
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
   data class BackupDocument(
       val format: String = BackupCodec.FORMAT_ID,
       val formatVersion: Int,
       val appVersionName: String,
       val appVersionCode: Int,
       val exportedAtEpochMillis: Long,
       val dbSchemaVersion: Int,
       val checksum: String,
       val content: BackupContent,
   )

   @Serializable
   data class BackupContent(
       val settings: List<BackupSetting> = emptyList(),
       val foodItems: List<BackupFoodItem> = emptyList(),
       val recipes: List<BackupRecipe> = emptyList(),
       val diaryEntries: List<BackupDiaryEntry> = emptyList(),
       val weightLogs: List<BackupWeightLog> = emptyList(),
   )

   @Serializable
   data class BackupSetting(val key: String, val value: String)

   @Serializable
   data class BackupFoodItem(
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
   data class BackupRecipe(
       val id: String,
       val name: String,
       val finalWeightGrams: Double? = null,
       val portions: Int = 1,
       val category: String? = null,
       val lastModifiedAt: Long = 0L,
       val isFavorite: Boolean = false,
       val lastUsedAt: Long? = null,
       val ingredients: List<BackupIngredient> = emptyList(),
   )

   @Serializable
   data class BackupIngredient(
       val foodItemId: String? = null,
       val rawWeightGrams: Double? = null,
       val servingCount: Double? = null,
       val recipeSnapshotJson: String? = null,
   )

   @Serializable
   data class BackupDiaryEntry(
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
   data class BackupWeightLog(
       val date: String,
       val kilograms: Double,
   )
   ```

   `BackupRecipe.ingredients` enthält die Zeilen aus `recipe_ingredients`
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
           data class Success(val document: BackupDocument, val checksumValid: Boolean) : DecodeResult
           data class Failure(val reason: DecodeError) : DecodeResult
       }

       sealed interface DecodeError {
           data object NotAZBackup : DecodeError
           data class UnsupportedVersion(val found: Int, val supportedMax: Int) : DecodeError
           data object Corrupted : DecodeError
       }

       fun encode(document: BackupDocument): String =
           json.encodeToString(BackupDocument.serializer(), document)

       fun checksum(content: BackupContent): String = // "sha256:" + Hex(sha256(canonicalBytes))
       fun canonicalContent(content: BackupContent): String =
           json.encodeToString(BackupContent.serializer(), content)

       fun decode(text: String): DecodeResult { /* Stufen 1+2, s. §3.4 */ }
   }
   ```

   `decode` prüft in dieser Reihenfolge: Parse-Fehler → `Corrupted`; `format !=
   FORMAT_ID` → `NotAZBackup`; Version außerhalb
   `[MIN_SUPPORTED_FORMAT_VERSION, FORMAT_VERSION]` → `UnsupportedVersion`. Der
   Prüfsummenvergleich liefert **kein** Fehlerresultat, sondern ein Flag
   `checksumValid` (A14) — der Aufrufer macht daraus eine Warnung.

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

- **Round-Trip:** `BackupContent` mit allen Feldern belegt (inkl. verschachtelter
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
- **Zukunftsformat** (`formatVersion = 99`) → `Failure(UnsupportedVersion(99, 1))`.
- **Altes Format** (`formatVersion = 0`) → `Failure(UnsupportedVersion(0, 1))`.
- **`settings`-Determinismus:** zwei `BackupContent` mit denselben Settings in
  unterschiedlicher Eingangsreihenfolge → identische `canonicalContent()`.

### Verifikation

```
./gradlew :core:data:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] `BackupCodec` ist Android-frei und ohne Room testbar
- [ ] Jedes Feld in `BackupArchive.kt` hat einen Default-Wert (A12)
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
- Ein schmales **`BackupLocalDataSource`**-Interface (Domain) kapselt den DAO,
  damit `BackupRepository` mit einem handgeschriebenen Fake testbar bleibt; die
  Room-Implementierung lebt in `:core:data`.
- IO und DB-Zugriff in `Dispatchers.IO`; `CancellationException` wird
  durchgereicht (Muster `runCatchingLocal` in `RoomDiaryLocalDataSource.kt:117`).
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
   }
   ```

   Dazu `core/domain/.../datasource/BackupLocalDataSource.kt` mit
   `readAll(): BackupRows`, `insertAll(...)`, `replaceAll(...)`, `readFoodItemIds()`,
   `readRecipeIds()`, `countAll()`. Und
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

   `openOutputStream` ist ab API 19 overloadbar mit `mode`; der Default
   `"w"` reicht. `FileProvider` wird **nicht** gebraucht (A1/Section
   [§1.3](#13-warum-kein-export-der-sqlite-datei)).

6. **App-Version** — `core/data/.../backup/PackageManagerAppVersionProvider.kt`:
   `context.packageManager.getPackageInfo(context.packageName, 0)` →
   `versionName` bzw. `versionCode` (`longVersionCode` ab API 28, darunter
   `versionCode`; minSdk ist 25 → mit `@Suppress("DEPRECATION")` arbeiten).
   Bewusst **kein** `BuildConfig` — dafür müsste `buildConfig = true` in
   `app/build.gradle.kts` aktiviert werden, und `:core:data` soll die
   App-Metadaten nicht aus dem Gradle-Modul beziehen.

7. **Mapper** — `core/data/.../backup/BackupMapper.kt`:
   - `FoodItemEntity.toBackup(): BackupFoodItem`, `BackupFoodItem.toEntity(rowId = 0)`
   - `RecipeEntity.toBackup(): BackupRecipe` (ohne Zutaten),
     `RecipeIngredientEntity.toBackup(foodItemId)`,
     `BackupRecipe.toEntities(): Pair<RecipeEntity, List<RecipeIngredientEntity>>`
     — **`rowId = 0`**, `isFavorite: Boolean → Int` (`1`/`0`),
     `origIsUserCreated: Boolean?` bleibt nullable
   - `DiaryEntryEntity.toBackup()`, `BackupDiaryEntry.toEntity()`
   - `WeightLogEntity.toBackup()`, `BackupWeightLog.toEntity()`
   - Gruppierung der `recipe_ingredients` nach `recipeId` im Exporter

8. **LocalDataSource** — `core/data/.../backup/RoomBackupLocalDataSource.kt`:
   `BackupDao` + `SettingsDataStore`, Delegation auf `readAll/insertAll/replaceAll`,
   `runCatchingLocal`-Muster (Log via `AppLogger`, `CancellationException`
   durchreichen, Fehler → `DataError.Local.UNKNOWN`).

9. **Validator** — `core/data/.../backup/BackupValidator.kt`:
   - `fun validateFood(item: BackupFoodItem): Boolean` über `FoodItemEntity.toFoodItem()`
     in `runCatching` (greift `require(name.isNotBlank())`, Nährwerte ≥ 0)
   - `fun validateDiaryEntry(entry: BackupDiaryEntry): Boolean` über
     `DiaryEntryEntity.toDiaryEntry()` (greift `MealSlot.valueOf`,
     `LocalDate.parse`, Snapshot-Dekodierung, Food/Recipe-XOR)
   - `fun validateRecipe(recipe: BackupRecipe): Boolean` über
     `RecipeEntity.toRecipe(ingredients, foodLocalDataSource)` — **Achtung:** der
     Mapper verwirft einzelne unauflösbare Zutaten stillschweigend und liefert
     bei null auflösbaren Zutaten `null`; das ist für die Validierung genau das
     richtige Signal (Rezept nicht importierbar → Zeile wird übersprungen).
   - `fun validateWeightLog(log: BackupWeightLog): Boolean` (`kilograms > 0`,
     parsebares Datum)
   - Jede Ablehnung erzeugt `SkippedRow(table, id, reason)` mit konkreter
     Begründung (z. B. `"unbekannter MealSlot: SUPPER"`, `"kcal negativ"`)

10. **Exporter** — `core/data/.../backup/BackupExporter.kt`:
    - Sections lesen, `food_items` gemäß A5 filtern:
      `isUserCreated == true` **oder** ID in (Rezept-Zutat-IDs ∪ Tagebuch-`foodItemId`s)
      — bei `includeCachedOpenFoodFactsItems == true` alles
    - `BackupDocument` bauen: `formatVersion = BackupCodec.FORMAT_VERSION`,
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
    - `import(uri, mode)`: lesen → dekodieren → validieren → **Referenzbereinigung**:
      vorhandene `foodItemId`s/`recipeId`s per `readFoodItemIds()`/`readRecipeIds()`
      (bei `REPLACE` die IDs aus dem Backup selbst) ermitteln, unbekannte
      Referenzen auf `null` setzen und zählen → `insertAll` bzw. `replaceAll`
      **in einer** Transaktion → Settings via `restore()` → `ImportReport`

12. **Repository + Koin** — `core/data/.../backup/RoomBackupRepository.kt` und
    `core/data/.../backup/di/BackupDataModule.kt`:

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

13. **Test-Infrastruktur** — `core/data/build.gradle.kts`: Test-Dependencies
    `robolectric`, `room-testing`, `androidx-test-core`, `androidx-test-runner`
    ergänzen und `testOptions { unitTests.isIncludeAndroidResources = true }`
    setzen (Muster `core/database/build.gradle.kts:13-21`). Neu:
    `core/data/src/test/resources/robolectric.properties` mit `sdk=34`.

### Dateien

**Neu**
- `core/domain/.../model/backup/{ImportMode,BackupOptions,BackupExportSummary,BackupPreview,ImportReport,BackupWarning,TableCounts,SkippedRow}.kt`
- `core/domain/.../repository/BackupRepository.kt`
- `core/domain/.../datasource/BackupLocalDataSource.kt`
- `core/domain/.../util/AppVersionProvider.kt`
- `core/data/.../backup/{BackupMapper,BackupValidator,BackupExporter,BackupImporter,RoomBackupLocalDataSource,ContentResolverBackupStorage,PackageManagerAppVersionProvider,RoomBackupRepository}.kt`
- `core/data/.../backup/di/BackupDataModule.kt`
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

`BackupExporterTest` / `BackupImporterTest` (mit Fake-`BackupLocalDataSource`):
- Export **ohne** OFF-Cache enthält nur eigene + referenzierte Foods, **mit**
  Schalter alle.
- `REPLACE` verwendet `replaceAll`, `MERGE` verwendet `insertAll`.
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

- [ ] `core:domain` bleibt Android-frei; `Uri`/`Context` kommen dort nicht vor
- [ ] `DataError.Local` hat die zwei neuen Cases, `DataErrorUiText` ist vollständig
- [ ] Ein Fehler im Backup führt zu `Result.Error`, nie zu einer Exception in
      den ViewModel
- [ ] Der Import läuft vollständig in **einer** Transaktion (A11, A16)
- [ ] Merge überschreibt keine bestehende Zeile und setzt `is_favorite` /
      `last_used_at` nicht zurück (A7)
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
  `IconButton`-Muster, `onNavigateToGoals`-Verdrahtung)
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
- Kein Umbenennen/Refactoring bestehender Diary-Dateien außer dem nötigen
  `onNavigateToSettings`-Parameter.
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
   - `init`: aktuelle Bestandszahlen **einmal** laden (ein
     `BackupRepository.readCounts()`-Aufruf bzw. über einen
     `observeCounts()`-Flow — die Entscheidung ist zu treffen; **wichtig**: kein
     Einmal-Read eines Flows, der sich später ändert, ohne dass die UI es merkt
     (Fehlerklasse aus `docs/Bugfix_Plan_Stale_Snapshots.md`). Wenn
     `BackupRepository` in WP3 keinen Count-Zugriff bekam, wird er hier als
     kleinster Zusatz ergänzt: `suspend fun counts(): Result<BackupExportSummary, DataError.Local>`).
   - `OnExportClick` → Event `RequestCreateDocument("ezmakro-backup-<yyyy-MM-dd-HHmm>.json")`
     (Zeitformat über `java.time`, Datum aus `clock()`)
   - `OnDocumentCreated(null)` → **nichts tun** (Nutzer hat abgebrochen)
   - `OnDocumentCreated(uri)` → `isExporting = true`, `repository.exportTo(uri,
     BackupOptions(includeCachedItems))`, Erfolg → `ShowMessage(Erfolg)`,
     Fehler → `ShowMessage(error.toUiText())`; `isExporting` immer zurücksetzen
   - `OnImportClick` → Event `RequestOpenDocument`
   - `OnBackupSelected(null)` → nichts tun
   - `OnBackupSelected(uri)` → `repository.preview(uri)` → `state.preview` oder Fehlermeldung
   - `OnConfirmImportClick` → `isImporting = true`, `repository.import(uri,
     pendingMode)` → `state.importReport` (und `preview = null`), Fehler → Meldung
   - Abbruch-Dialogs setzen `preview`/`importReport` auf `null`
   - `OnModeChange` verändert **nur** `pendingMode` — die Warnung für `REPLACE`
     ist reine UI-Zustandsableitung (`pendingMode == REPLACE`), keine zweite
     Confirm-Action im ViewModel
   - `ShowMessage` **nicht** verschlucken (P3.4-Lehre: Events konsistent behandeln)

3. **Screen** — `BackupScreen.kt` (Root mit `koinViewModel()`, Scaffold mit
   TopAppBar + `onNavigateBack`) und `BackupScreenContent.kt`:
   - „Exportieren"-Karte: Bestandszahlen (mit `formatValue`, WP1 QoL-Regel),
     `Switch` „Gecachte OFF-Lebensmittel einschließen", Button
     „Backup erstellen"
   - „Importieren"-Karte: Button „Backup einspielen"
   - `LinearProgressIndicator`/design-system-`LoadingIndicator` bei
     `isExporting`/`isImporting`; Buttons in diesen Zuständen deaktiviert
   - `AlertDialog` für Preview (Datum, App-Version, Format-Version, Counts,
     Zeitraum, Warnungen, Moduswahl als `RadioButton`-Zeilen) und Bestätigung bei
     `REPLACE` („Alle lokal gespeicherten Lebensmittel, Rezepte,
     Tagebucheinträge und Gewichtsdaten werden gelöscht.")
   - `AlertDialog` für den `ImportReport` (eingefügt/übersprungen/Referenzen/
     Warnungen)
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
   Listen-Einstieg mit einer Zeile „Backup" (Icon + Untertitel „Daten sichern
   und wiederherstellen") → `onNavigateToBackup`. Heute bewusst nur ein
   Eintrag; die Struktur ist bewusst erweiterbar (Dark Mode, Einheiten).

5. **Navigation** — `feature/settings/.../settings/navigation/Settings.kt`:

   ```kotlin
   @Serializable
   object Settings

   fun NavGraphBuilder.settingsGraph(
       onNavigateBack: () -> Unit,
       onNavigateToBackup: () -> Unit,
   ) {
       composable<Settings> { SettingsRoot(onNavigateBack = onNavigateBack, onNavigateToBackup = onNavigateToBackup) }
       composable<Backup> { BackupRoot(onNavigateBack = onNavigateBack) }
   }
   ```

   Hinweis aus `DiaryRoutes.kt:11-13`: Das Diary-Modul nutzt bewusst
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
       onNavigateToBackup = { navController.navigate("settings/backup") },
   )
   ```
   und `feature.diary.presentation.settings` importieren. Zusätzlich
   `app/.../di/AppModule.kt` (`settingsPresentationModule`).

8. **Diary-Einstieg** —
   - `app/src/main/res/drawable/ic_settings.xml`: neues Vector-Icon (Material-
     Stil wie `ic_diary.xml`/`ic_food.xml`, Pfad für „Settings"/Zahnrad)
   - `feature/diary/.../navigation/DiaryRoutes.kt`: Parameter
     `onNavigateToSettings: () -> Unit = {}` in `diaryGraph` **und**
     `dayComposable` ergänzen und an `DiaryDayRoot` durchreichen
   - `DiaryDayScreen.kt`: Callback-Parameter, `IconButton` in der TopAppBar
     neben dem Ziele-Icon, `contentDescription` aus der neuen Diary-String
   - `feature/diary/presentation/src/main/res/values/strings.xml`:
     `diary_settings` = „Einstellungen"

9. **Strings** — `feature/settings/presentation/src/main/res/values/strings.xml`:
   `settings_title`, `settings_backup_title`, `settings_backup_subtitle`,
   `backup_export_title`, `backup_import_title`, `backup_include_cached`,
   `backup_create`, `backup_open`, `backup_preview_title`,
   `backup_exported_at`, `backup_app_version`, `backup_format_version`,
   `backup_date_range`, `backup_mode_merge`, `backup_mode_merge_hint`,
   `backup_mode_replace`, `backup_mode_replace_warning`,
   `backup_confirm_import`, `backup_cancel`, `backup_report_title`,
   `backup_report_inserted`, `backup_report_skipped`,
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
- `app/src/main/res/drawable/ic_settings.xml`

**Geändert**
- `settings.gradle.kts`
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
- `OnModeChange(REPLACE)` → `import(uri, REPLACE)`; Abbruch setzt `preview = null`
- `ShowMessage` wird nicht verschluckt; `NavigateBack` bei `OnBackClick`
- Doppelter Klick während `isExporting`/`isImporting` löst keinen zweiten
  Repository-Aufruf aus
- `BackupOptions.includeCachedItems` wird aus dem Switch-Zustand durchgereicht

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
- [ ] `ShowMessage`-Events werden in Snackbar angezeigt (keine verschluckten
      Events, P3.4)
- [ ] Der Einstieg ist über ein Icon in der Diary-TopAppBar erreichbar
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
- `docs/roadmap.md:7-31` (Phasentabelle, Meilensteine M4)
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
     in `BackupContent` (A12) plus `formatVersion`-Erhöhung nur dann, wenn
     bestehende Daten semantisch anders gelesen werden müssen.
   - Spaltenumbau in `food_items`/`recipes` → **keine** Formatänderung; nur
     `BackupMapper` anpassen.
   - Neues Feld in `FoodItem`/`Recipe` (Domain) → Default Pflicht (A12),
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
   *Neue Felder in `@Serializable`-Modellen brauchen einen Default-Wert, damit
   versionierte Serialisierungen (Backup-Format, Tagebuch-Snapshots) älterer
   Datenstände lesbar bleiben.* (A12)

6. **`docs/architecture.md`** — `:feature:settings:presentation` in die
   Modulliste, ein kurzer Abschnitt „Backup" mit dem Hinweis auf logischen
   Export (A1), Formatversion (A2) und die Tatsache, dass `:app` die
   Koin-Assemblierung des neuen Modulbereichs übernimmt.

7. **`docs/Vollstaendiger_Implementierungsplan.md`** — Phase 12 auf ✅, den
   Abschnitt „Phase 12 — Backup" durch eine Kurzfassung ersetzen (JSON
   Export/Import, Validierung, **keine** Verschlüsselung) und den Verweis auf
   dieses Dokument aufnehmen.

8. **`docs/roadmap.md`** — Phase 12 auf ✅ mit Verweis auf
   `docs/Plan_Backup_System.md`, Meilenstein **M4** („Vollständige,
   backup-fähige App") auf ✅, und die Aussage zum User-Flow „Backup"
   (`:52`) präzisieren (ohne Passwort).

9. **Statuspflege** — `§4` in diesem Dokument: alle fünf Zeilen auf ✅ mit
   Commit-Hash; Kopfzeile „**Status:**" auf „abgeschlossen" setzen.

### Dateien

**Geändert (Doku)**
- `docs/adr/ADR-007-backup-format.md`
- `docs/Vollstaendiger_Implementierungsplan.md`
- `docs/roadmap.md`
- `docs/architecture.md`
- `docs/coding-style.md`
- `docs/Bugfix_Plan.md`
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
      Verschlüsselungs-Zusage
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

1. Settings-Icon in der Diary-TopAppBar → „Einstellungen" → „Backup"
2. Drei eigene Lebensmittel, zwei Rezepte (mit Unter-Rezept-Zutat), fünf
   Tagebucheinträge, ein Quick-Add-Eintrag (transient) anlegen
3. Export **ohne** Cache-Schalter → Datei in „Downloads" sichern; Datei in einem
   Editor öffnen: `formatVersion` = 1, drei Foods, zwei Rezepte, fünf Einträge,
   fünf Settings
4. App-Daten löschen (Einstellungen → Apps → Speicher) → App neu starten
5. Import → Preview zeigt 3/2/5 und den Zeitraum → **Ergänzen** → Report zeigt
   „5 eingefügt"
6. Tagebuch: alle Einträge inklusive Quick-Add (nur Snapshot) und Rezept-
   Tagesanpassung (`recipe_overridden`) sind wieder da; Rezept-Makros stimmen
7. Ein importiertes Food ist über die **lokale Suche** (FTS) findbar (A13)
8. Noch einmal importieren → Report: „0 eingefügt, 5 übersprungen" (Idempotenz)
9. Import mit „Ersetzen" → Warndialog → Datenbestand exakt wie das Backup
10. Export mit aktivem Cache-Schalter → Datei enthält zusätzlich die
    gecachten OFF-Lebensmittel
11. `goals_*`-Werte stehen nach dem Import auf den Werten des Exports
12. Ein absichtlich kaputt gemachtes Backup (`formatVersion: 99`) → verständliche
    Fehlermeldung, kein Crash, lokale Daten unverändert

## 6. Nicht-Ziele

- **Keine Verschlüsselung** (A3). Der Phasenplan nennt sie als optional; sie
  bleibt eine mögliche spätere Erweiterung über `formatVersion`.
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
| `docs/roadmap.md` | WP5 | Phase 12 ✅, M4 ✅, User-Flow 5 ohne Passwort |
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
> 3. Lege `TableInsertCounts` (mit `from(rowIds)` — Room meldet `-1` für per
>    IGNORE übersprungene Zeilen) und `BackupInsertReport` an.
> 4. Ergänze in `core/database/.../di/DatabaseModule.kt` genau ein Binding nach
>    dem bestehenden Muster: `single { get<EZDatabase>().backupDao() }`.
> 5. Schreibe `core/database/src/test/.../dao/BackupDaoTest.kt` (Robolectric,
>    `DatabaseTest`-Harness) mit allen in „Tests" genannten Fällen — insbesondere
>    den **FTS-Nachweis** (nach `insertAll` findet `FoodItemDao.searchByName`
>    importierte Foods) und den Nachweis, dass `insertFoodItems` bei doppelter
>    `id` überspringt und dabei `is_favorite`/`last_used_at` **nicht**
>    zurücksetzt.
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
>    ausgeschriebenen `@Serializable`-DTOs** an. Jedes Feld hat einen
>    Default-Wert. `settings` ist eine **Liste** (nicht `Map`). Es gibt kein
>    `rowId`-Feld und keine FTS-Sektion. `cachedStatus`/`slot` bleiben `String`.
>    Die DTO-Felder sind das CamelCase-Pendant der aktuellen Spalten aus
>    `core/database/.../entity/*.kt` — halte dich bei den N Namen exakt an die
>    dortigen `@ColumnInfo`-Namen.
> 3. Lege `core/data/.../backup/BackupCodec.kt` an: `FORMAT_ID`, `FORMAT_VERSION = 1`,
>    `MIN_SUPPORTED_FORMAT_VERSION = 1`, die `Json`-Instanz
>    (`ignoreUnknownKeys`, `isLenient`, `encodeDefaults`), `encode`,
>    `canonicalContent`, `checksum` (SHA-256, Ergebnis als `"sha256:<hex>"`),
>    `decode` mit der Reihenfolge Parse → `format` → Versionsbereich. Typisierte
>    Fehler als `DecodeResult.Failure(DecodeError)` mit `NotAZBackup`,
>    `UnsupportedVersion(found, supportedMax)`, `Corrupted`. Die Prüfsumme ist
>    **kein** Fehlerfall, sondern ein Flag `checksumValid` in
>    `DecodeResult.Success`.
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
> außerdem „§2 Festgelegte Entscheidungen" (A5–A8, A10, A11, A14, A16),
> „§3.1", „§3.3" und „§3.4". Skills `android-data-layer` und
> `android-error-handling` laden. WP1 und WP2 müssen abgeschlossen sein
> (`BackupDao` und `BackupCodec` existieren).
>
> Kontext: Phase 12 (Backup). Jetzt kommt der Prozess: Settings sichern, über SAF
> schreiben/lesen, validieren, in **einer** Transaktion importieren. Merge
> ergänzt nur, Replace leert vorher.
>
> Umsetzung:
> 1. `core/domain/.../model/backup/`: `ImportMode`, `BackupOptions`,
>    `BackupExportSummary`, `BackupPreview`, `ImportReport`, `BackupWarning`,
>    `TableCounts`, `SkippedRow` (Signaturen aus dem Plan). `:core:domain`
>    bleibt Android-frei — **kein** `Uri`, **kein** `java.time` ist verboten,
>    `LocalDate` ist erlaubt (reines JVM).
> 2. `core/domain/.../repository/BackupRepository.kt` mit `exportTo(uri: String,
>    options: BackupOptions)` / `preview(uri: String)` /
>    `import(uri: String, mode: ImportMode)` — je `Result<…, DataError.Local>`.
>    Ergänze außerdem `BackupLocalDataSource` in
>    `core/domain/.../datasource/` und `AppVersionProvider` in
>    `core/domain/.../util/`. **Füge dem Repository einen Count-Zugriff hinzu**
>    (`suspend fun counts(): Result<BackupExportSummary, DataError.Local>`) —
>    der Settings-Screen in WP4 zeigt den aktuellen Datenbestand, und ein
>    Einmal-Read eines sich ändernden Flows ohne UI-Reaktion ist die Fehlerklasse
>    aus `docs/Bugfix_Plan_Stale_Snapshots.md`.
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
> 7. `BackupValidator.kt`: Validiere jede Zeile, indem du die **bestehenden**
>    Entity→Domain-Mapper in `runCatching` aufrufst (`toFoodItem()`,
>    `toDiaryEntry()`, `toRecipe(ingredients, foodLocalDataSource)`, WeightLog per
>    `kilograms > 0` + parsebarem Datum). Ablehnungen werden zu
>    `SkippedRow(table, id, reason)` mit konkreter Begründung. Beachte: Der
>    Rezept-Mapper verwirft unauflösbare Zutaten stillschweigend und liefert bei
>    null auflösbaren Zutaten `null` — nutze genau das als Validierungssignal.
> 8. `BackupExporter.kt`: Sections lesen, `food_items` gemäß A5 filtern
>    (eigene Foods ∪ von Rezepten/Tagebuch referenzierte; bei Schalter **alles**),
>    `settings` **nach Key sortiert** als Liste, `checksum` über den kanonischen
>    Content, `appVersion*` aus `AppVersionProvider`, `exportedAt` aus einem
>    injizierten `clock: () -> Long` mit Default, `dbSchemaVersion` nur als Info.
>    Schreiben über `BackupStorage.openOutput(uri)` mit `use { }`.
> 9. `BackupImporter.kt`: `preview(uri)` liest, dekodiert, prüft (A11 Stufe 1–4),
>    ermittelt Counts + Datumsspanne + Warnungen und schreibt **nichts**.
>    `import(uri, mode)` validiert, bereinigt Referenzen (unbekannte
>    `foodItemId`/`recipeId` → `null`, zählen), ruft bei `MERGE` `insertAll`, bei
>    `REPLACE` `replaceAll` — **jeweils in einer Transaktion** — und stellt
>    Settings via `restore()` wieder her.
> 10. `RoomBackupLocalDataSource.kt` (DAO + SettingsDataStore, `runCatchingLocal`-
>    Muster aus `RoomDiaryLocalDataSource.kt`, `CancellationException`
>    durchreichen, Fehler → `DataError.Local.UNKNOWN`) und
>    `RoomBackupRepository.kt`. Koin-Modul `backupDataModule` in
>    `core/data/.../backup/di/BackupDataModule.kt`, registriert in
>    `app/.../di/AppModule.kt` (`includes(…)`, alphabetisch eingeordnet).
> 11. Test-Infrastruktur in `core/data/build.gradle.kts`: `robolectric`,
>     `room-testing`, `androidx-test-core`, `androidx-test-runner` als
>    `testImplementation`, `testOptions { unitTests.isIncludeAndroidResources = true }`,
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
>    - Bestandszahlen beim Start über den Count-Zugriff aus WP3 laden; wenn das
>      ein Flow ist, **beobachte** ihn (`stateIn`), statt ihn einmal zu lesen
>      (Fehlerklasse aus `docs/Bugfix_Plan_Stale_Snapshots.md`).
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
> 4. `SettingsScreen` mit einer Zeile „Backup" → `onNavigateToBackup`.
> 5. `navigation/Settings.kt` mit `@Serializable object Settings` und
>    `settingsGraph(onNavigateBack, onNavigateToBackup)`. Nutze **String-Routen**
>    (`"settings"`, `"settings/backup"`) nach dem Muster aus
>    `feature/diary/.../navigation/DiaryRoutes.kt:11-13` (dort ist der
>    `KClass.serializer()`-Edge-Case in Navigation 2.9 dokumentiert).
> 6. `settings/di/SettingsPresentationModule.kt` mit
>    `viewModel { BackupViewModel(get()) }`; in `app/.../di/AppModule.kt` in
>    `includes(…)` aufnehmen.
> 7. `app/.../MainActivity.kt`: `settingsGraph(...)` in `AppNavHost` registrieren.
> 8. Diary-Einstieg: neues `app/src/main/res/drawable/ic_settings.xml` (Vektor im
>    Stil der vorhandenen Icons), `onNavigateToSettings: () -> Unit = {}` in
>    `DiaryRoutes.kt` (in `diaryGraph` **und** `dayComposable`) plus
>    `IconButton` in der TopAppBar von `DiaryDayScreen.kt` neben dem Ziele-Icon,
>    String `diary_settings` = „Einstellungen" in der Diary-`strings.xml`.
> 9. `feature/settings/presentation/src/main/res/values/strings.xml` mit allen im
>    Plan gelisteten Strings. **Alle** nutzersichtbaren Texte als `UiText`
>    ausgeben (kein hartkodiertes `Text("…")`).
> 10. Schreibe `BackupViewModelTest` (JUnit5 + Turbine + AssertK +
>     `UnconfinedTestDispatcher`, Fake-`BackupRepository` mit schaltbarem Fehler)
>     mit allen in „Tests" genannten Fällen, sowie das Fake unter
>     `src/test/.../settings/fake/`.
>
> Wichtige Regeln: Der ViewModel referenziert **weder** `Uri` noch
> `ActivityResultContracts`. Kein `FileProvider`, kein Manifest-Eintrag, keine
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
> 5. `docs/coding-style.md`: Regel ergänzen — neue Felder in `@Serializable`-
>    Modellen brauchen Default-Werte, damit versionierte Serialisierungen
>    (Backup-Format, Tagebuch-Snapshots) älterer Datenstände lesbar bleiben.
> 6. `docs/architecture.md`: `:feature:settings:presentation` in die Modulliste,
>    kurzer Abschnitt „Backup" (logischer Export statt DB-Kopie, Formatversion).
> 7. `docs/Vollstaendiger_Implementierungsplan.md`: Phase 12 auf ✅, Abschnitt
>    „Phase 12 — Backup" durch eine Kurzfassung ersetzen (JSON Export/Import,
>    Validierung, **keine** Verschlüsselung), Verweis auf dieses Dokument.
> 8. `docs/roadmap.md`: Phase 12 auf ✅ mit Verweis, Meilenstein **M4** auf ✅,
>    User-Flow „Backup" ohne Passwort formulieren.
> 9. `docs/Plan_Backup_System.md`: In „§4 WP-Übersicht" alle fünf Statuszeilen
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