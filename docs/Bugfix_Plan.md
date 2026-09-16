# Bugfix-Plan

Stand: 2026, nach Phase 4 (FoodPicker-Vereinheitlichung). Alle Befunde stammen aus
einem strukturierten Code-Review und sind gegen den aktuellen `master`
verifiziert (Datei:Zeile beziehen sich auf diesen Stand).

## 1. Zusammenfassung

Die App ist funktional, aber es existieren einzelne Crash-Pfade (P1) und mehrere
Datenintegritäts-/UX-Schwächen (P2/P3). Priorität liegt auf den P1-Crashes:
Sie sind reproduzierbar, brechen Kerndurchgänge (Rezept-, Picker-, Tagebuch-Flow)
und lassen sich mit engen Regressionstests absichern. Falls noch nicht erledigt:
Das vorliegende Dokument ist der Arbeitsauftrag für einen Implementierungs-Agent
(je P1-Punkt ein Fix + Test, Commit erst nach expliziter Freigabe).

## 2. Bereits behobene Bugs (Kontext)

Diese zwei Bugs sind gefixt und durch Regressionstests abgedeckt — sie sollen
nicht erneut bearbeitet werden:

- **Online-Zutat im Rezept-Editor** — `addIngredient()` fiel bei Online-Zutaten
  (Room-Flow noch nicht emittiert) auf `firstOrNull()` zurück und hing die erste
  vorher hinzugefügte Zutat an. Fix: Name/Label direkt aus dem
  `AddAsIngredient`-Event; fehlerhafter Fallback entfernt.
- **Rezepte mit 0 kcal/100 g im FoodPicker** — `toPickerRecipeUi()` zeigte bei
  Rezepten ohne Endgewicht `0.0`. Fix: Fallback auf `estimatedPer100g()`.

**Wichtig:** Der zweite Fix vergrößert die Angriffsfläche von P1.1 (Division
durch 0), weil `estimatedPer100g()` jetzt auch aus dem FoodPicker aufgerufen
wird. P1.1 hat damit erste Priorität.

## 3. P1 — Crashes (sofort fixen)

### P1.1 Division durch 0 in Rezept-Berechnungen

- **Symptom:** Rezept, dessen Zutaten in Summe 0 g Rohgewicht haben → Crash auf
  Rezeptliste, Detail, FoodPicker oder Tagebuch.
- **Root Cause:** `RecipeCalculation.kt:27`
  `estimatedPer100g()` = `totalNutrition() * (100.0 / totalRawWeight().grams)`
  und `RecipeCalculation.kt:58-65` `DiaryEntry.consumedNutrition()` sowie
  `RecipeEditorViewModel.kt:440-445` `createSnapshot()` rechnen ohne positiven
  Divisor. `0.0 * Infinity` ergibt `NaN`; `NutritionValues.init` wirft mit
  `require(kcal >= 0.0)` (`NutritionValues.kt:22-27`).
- **Repro:** Rezept mit 1 Zutat, Rohgewicht `0`, ohne Endgewicht speichern →
  Liste/Detail/Picker crashen.
- **Fixrichtung:** Denominator mit `> 0.0` schützen (sonst `NutritionValues.ZERO`
  bzw. "nicht definiert"), und/oder `0`-Gewichte im Editor-Validator ablehnen.
  Alle drei Aufrufstellen absichern, Regressionstest in `RecipeCalculationTest`.

### P1.2 FTS-Suche crasht bei Sonderzeichen

- **Symptom:** Eintippen von `"`, `*`, `(` in der Picker-Suche → App-Crash.
- **Root Cause:** `RoomFoodLocalDataSource.kt:49,103` reicht `"$normalized*"`
  unescaped an `food_items_fts MATCH :query` (`FoodItemDao.kt:144-149`). Ungültige
  FTS-Syntax → `SQLiteException` innerhalb des Flows → Crash des Collectors.
  Zusätzlich kein Debounce (jeder Tastendruck = FTS-Scan).
- **Repro:** Im FoodPicker `"` oder `"` + Buchstabe tippen.
- **Fixrichtung:** FTS-Metazeichen escapen/bereinigen, Query absichern
  (try/catch in den Flow oder Query-Sanitizer), optional Debounce im
  `queryFlow`. Test über `RoomFoodLocalDataSource`/DAO.

### P1.3 Serving-Unit entfernen "brickt" Rezepte

- **Symptom:** Food in Portions-Einheit gefüttert, danach beim Food die
  Portionsgröße geleert (`saveUserEdit`) oder auf OFF-Original zurückgesetzt
  (`resetToOriginal`) → jede Rezept-Hydration crasht.
- **Root Cause:** `RecipeIngredient.kt:22-29` `require(food.servingUnit != null)`
  bei `servingCount != null && rawWeight == null`; `RecipeEntityMapper.kt:51-56`
  baut so einen `RecipeIngredient` ungeprüft. Exception entkommt dem
  `observeAll().map { }`-Flow ungefangen (`RoomRecipeLocalDataSource.kt:29`).
- **Repro:** "Ei" (1 Stück 60 g) als 2 Portionen ins Rezept → Portionsgröße
  leeren → Speichern → Rezeptliste öffnen.
- **Fixrichtung:** Mapper muss ungültige Domain-Objekte vermeiden (Zutat
  degradieren/skippen statt Rezept verwerfen); `parseServingUnit`/
  `resetToOriginal` gegen umgekehrte Referenzen schützen. Optionale Absicherung
  in `RecipeIngredient` selbst. Test im `RecipeEntityMapper`/`RecipeDaoTest`.

### P1.4 Barcode-Double-Scan → doppelte Detailseiten / doppelte Zutaten

- **Symptom:** Barcode kurz stabil halten → Detailseite öffnet sich nach "Back"
  immer wieder; im Rezept-Editor doppelte `AddAsIngredient`.
- **Root Cause:** `MlKitBarcodeScanner.kt:52` sendet jeden Frame mit
  `trySend`; `FoodPickerViewModel.kt:295-344` entsperrt `isLookingUpBarcode`
  sofort nach Erfolg. `events` ist ein geufferter Channel + `flowWithLifecycle`
  (`core/presentation/.../ObserveAsEvents.kt`), der gedeckte Events nach
  Rückkehr replayt.
- **Repro:** Scan-Tab → Barcode halten → Detail öffnen → Back → Detail öffnet
  erneut.
- **Fixrichtung:** Im ViewModel selben Barcode für ein Zeitfenster/Frame-Count
  ignorieren (Settle), Scanner nach Treffer stoppen bzw. Navigation erst nach
  Deaktivierung. Test in `FoodPickerViewModelTest`.

## 4. P2 — Datenintegrität (nach P1)

### P2.1 Online-Food bearbeiten: leeres Formular + falsche Identität

- **Root Cause:** `FoodEditViewModel.loadExisting()` (`food/edit/...:82-99`) liest
  nur `observeAll().first()`, kein `getSessionItem`-Fallback → Formular leer.
  Beim Save greift der "neu anlegen"-Zweig (`:132-143`) mit `isUserCreated = true`
  unter OFF-ID; ADR-004-Snapshot wird umgangen; späterer Barcode-Scan kann die
  Nutzeränderung still überschreiben (`OfflineFirstFoodRepository.kt:44-56`).
- **Fixrichtung:** `loadExisting`-Fallback auf `repository.getSessionItem(id)`;
  Save auf Session-Item basieren (`isUserCreated = false`, Snapshot setzen).

### P2.2 Gelöschtes Unter-Rezept blockiert das Speichern des Eltern-Rezepts

- **Root Cause:** `RecipeEditorViewModel.resolveRecipeIngredient()` (`editor/...:405-409`)
  `recipes.firstOrNull { id } ?: return null` → kein Fallback auf das in der DB noch
  vorhandene `recipe_snapshot_json`. Detail-Anzeige funktioniert weiter, aber
  jeder Save (auch reiner Namens-Tweak) scheitert mit "Zutaten-Gewicht"-Fehler.
- **Fixrichtung:** Bei fehlender Referenz auf den gespeicherten Snapshot zurückgreifen
  (statt Fehler), ggf. mit Log. Test im `RecipeEditorViewModelTest`.

### P2.3 "Portionen" von Snapshot-Zutaten im Editor nicht sichtbar/änderbar

- **Root Cause:** `RecipeEditorViewModel.kt:240` setzt `servingLabel` nur aus
  `ing.food?.servingUnit` → null bei Snapshot-Zutaten; `IngredientRow.kt:59-74`
  blendet das Portionen-Feld dann aus. Detailseite kann es (`:56-57`).
- **Fixrichtung:** Für Snapshot-Zutaten das Portionen-Feld (Label "Portionen")
  einblenden und in `IngredientMacroDetail` darstellen. Test UI/ViewModel.

## 5. P3 — Robustheit & UX (optional, später)

- **P3.1 Stale Online-Ergebnisse unter neuer Query klickbar**
  `FoodPickerViewModel.kt:84-90` löscht `onlineSearchResults`/`onlineFoodItems`
  bei Query-Wechsel nicht. → Bei Query-Änderung / neuen Online-Search leeren.
- **P3.2 Nav-Routen mit `/` im ID crashen** — code-lose OFF-Produkte: `OffProductMapper.kt:40-41`
  nutzt `off:<name>`; Routen werden unencoded gebaut (`FoodRoutes.kt`, `MainActivity.kt`).
  → IDs URI-encoden bzw. Type-safe Routes.
- **P3.3 LazyColumn-Keys nach Index** (`RecipeEditorScreen.kt:181/187`) →
  State-Bleed beim Entfernen. → Stabile Per-Draft-IDs.
- **P3.4 `FoodDetailScreen.kt:66` schluckt `ShowMessage`** → Reset-/Refetch-Fehler
  unsichtbar. → Snackbar rendern.
- **P3.5 `toggleFavorite` auf Session-Item scheitert still**
  (`OfflineFirstFoodRepository.kt:108-121`) → Session-Item vorher `cacheRemoteItem`
  oder Fehler anzeigen.
- **P3.6 `executeSave` nutzt eingefrorenen State** (`RecipeEditorViewModel.kt:272-281`)
  → Eingaben während des Speicherns gehen verloren. → State in `executeSave`
  erneut lesen oder Felder während `isSaving` sperren.
- **P3.7 `duplicate()` setzt `lastModifiedAt = 0`** (`RoomRecipeRepository.kt:47-68`)
  → falsche "geändert"-Badges bei Snapshot-Verwendung. → `lastModifiedAt = now()`.

## 6. Priorisierung & Reihenfolge

| Rang | ID | Schwere × Likelihood | Modul |
|------|----|--------------------|-------|
| 1 | P1.1 | Division durch 0, zentral erreichbar | core/domain, recipes, diary |
| 2 | P1.2 | FTS-Crash durch normale Tastatureingabe | core/data, foodpicker |
| 3 | P1.3 | Rezept-Flow komplett down nach Food-Edit | core/data + domain model |
| 4 | P1.4 | Doppelte Navigation/Adds im Scan | foodpicker, core/barcode |

Danach P2.1 → P2.2 → P2.3, dann bei Bedarf P3 nach Aufwand.

## 7. Ablauf pro Fix

1. Regressionstest schreiben (rot stellen)
2. Fix implementieren
3. Tests grün
4. `./gradlew build`, `detekt`, `ktlintCheck` als lokales Gate
5. Commit **nur** nach expliziter Freigabe

## 8. Offene Punkte für später (separate Analyse)

- OFF-`UpdateAvailable`-Status ist toter Code (kein Produktions-Aufrufer von
  `markUpdateAvailable`) → Feature-Lücke, kein Bug.
- `searchRemote`-Kdoc verspricht Persistenz, Implementierung cached nicht
  (`OfflineFirstFoodRepository.kt:41-42`) → Kommentar/Verhalten angleichen.
- `getByBarcode` ohne `ORDER BY` (`FoodItemDao.kt:102-103`) → deterministische
  Präferenz (User-Food vor OFF).
- `ON DELETE SET NULL` auf `food_item_id` (Latentrisiko, kein Delete-UI).
- Session-Cache (`rememberRemoteItem`) nach Process Death verloren → Navigation
  mit `getSessionItem == null` handelt `MainActivity.kt:217-241` still (P3.5 verzahnt).