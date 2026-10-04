# Bugfix-Plan (Archiv + Backlog)

Stand: 2026-09-18, nach Abschluss der Bugfix-Phasen P1–P3 (Commits `34610f1`,
`41d341e`, `d0243a3`). Das Dokument dient als Nachweis der abgeschlossenen
Fixes (inkl. Root Cause) und als Backlog für die noch offenen Qualitätspunkte
(Abschnitt 3).

## 1. Zusammenfassung

Alle P1-Crashes, P2-Datenintegritäts- und P3-Robustheits-Bugs sind behoben,
durch Regressionstests abgesichert und committet; das lokale Gate (`build`,
`detekt`, `ktlintCheck`) ist grün. Für weitere Implementierung liegt Phase 10+
an (siehe `roadmap.md`); für weitere Bugfix-/Qualitätsarbeit siehe Abschnitt 3.

**Nachtrag 2026-10-03:** Nach dem QoL-Plan (WPs 1–4) wurde eine sechste
Fehlerklasse aufgedeckt — veraltete Snapshots im Compose-State (UI beobachtet
die Quelle nicht). Sechs Instanzen, davon zwei mit Datenverlust. Plan und
Agent-Prompts: `docs/Bugfix_Plan_Stale_Snapshots.md` (P4.1–P4.5).

## 2. Abgeschlossene Bugfix-Phasen

### Vor dem Plan (Kontext, Commits `389a8e5` + `c5abe44`)

- **Online-Zutat im Rezept-Editor** — `addIngredient()` fiel bei Online-Zutaten
  (Room-Flow noch nicht emittiert) auf `firstOrNull()` zurück und hing die erste
  vorher hinzugefügte Zutat an. Fix: Name/Label direkt aus dem
  `AddAsIngredient`-Event; fehlerhafter Fallback entfernt.
- **Rezepte mit 0 kcal/100 g im FoodPicker** — `toPickerRecipeUi()` zeigte bei
  Rezepten ohne Endgewicht `0.0`. Fix: Fallback auf `estimatedPer100g()`.

### P1 — Crashes (Commit `34610f1`)

| ID | Bug | Fix |
|----|-----|-----|
| P1.1 | Division durch 0 in Rezept-Berechnungen (`NaN` → Crash in `NutritionValues`) | `estimatedPer100g`/`consumedNutrition`/`createSnapshot` geben bei Rohgewicht 0 `NutritionValues.ZERO` statt `NaN` zurück; Editor lehnt Zutaten mit Gewicht/Anzahl 0 ab |
| P1.2 | FTS-Suche crasht bei Sonderzeichen (`"`, `*`, `(`) | `FtsQuerySanitizer` entfernt FTS-Metazeichen vor dem `MATCH`; `searchByName` kapselt die Suche in `runCatching` |
| P1.3 | Serving-Unit entfernen "brickt" Rezepte (Hydration-Crash) | `RecipeEntityMapper` überspringt invalide Zutaten (fehlende Serving-Unit / fehlendes Food), statt das ganze Rezept zu verwerfen |
| P1.4 | Barcode-Double-Scan → doppelte Detailseiten / Zutaten | `FoodPickerViewModel` dedupliziert gleiche Barcode-Scans innerhalb eines 3 s-Fensters |

Regressionstests für alle vier Fixes.

### P2 — Datenintegrität (Commit `41d341e`)

| ID | Bug | Fix |
|----|-----|-----|
| P2.1 | Online-Food bearbeiten: leeres Formular + falsche Identität | `loadExisting()` nutzt `getSessionItem`-Fallback; `save()` basiert das Item auf `getById`/`getSessionItem`, sodass `saveUserEdit` den ADR-004-Snapshot korrekt setzt; `OfflineFirstFoodRepository` nutzt den Session-Cache als Snapshot-Quelle |
| P2.2 | Gelöschtes Unter-Rezept blockiert das Speichern des Eltern-Rezepts | `resolveRecipeIngredient()` greift auf `draft.recipeSnapshot` zurück, wenn das Live-Rezept fehlt |
| P2.3 | "Portionen" von Snapshot-Zutaten im Editor nicht sichtbar/änderbar | Snapshot-Zutaten erhalten ein generisches Label "Portionen"; Feld sichtbar und editierbar, `IngredientMacroDetail` zeigt "X Portionen" |

Regressionstests für alle drei Fixes.

### P3 — Robustheit & UX (Commit `d0243a3`)

| ID | Bug | Fix |
|----|-----|-----|
| P3.1 | Stale Online-Ergebnisse unter neuer Query klickbar | `OnSearchQueryChange` leert `onlineSearchResults`/`onlineFoodItems` (Handler in `onSearchQueryChange()` extrahiert) |
| P3.2 | Nav-Routen mit `/`-ID crashen (code-lose OFF-Produkte) | Pure-Kotlin `encodeRouteParam`/`decodeRouteParam` in `FoodRoutes.kt` (Uri-äquivalent, kein Robolectric nötig); Encoding an allen Build-/Read-Stellen in `FoodRoutes.kt` + `MainActivity.kt` |
| P3.3 | LazyColumn-Keys nach Index → State-Bleed beim Entfernen | Stabile Per-Draft-IDs: `IngredientDraft.id = UUID`, `key = ingredient.id` |
| P3.4 | `FoodDetailScreen` schluckt `ShowMessage` → Reset-/Refetch-Fehler unsichtbar | `SnackbarHostState` in `FoodDetailRoot` + `Scaffold(snackbarHost=…)`; `ShowMessage` zeigt die Fehler als Snackbar |
| P3.5 | `toggleFavorite` auf Session-Item scheitert still | `cacheRemoteItem`-Fehler wird propagiert (kein `setFavorite` bei fehlgeschlagenem Persist); `toggleFavorite` als `when`-Ausdruck |
| P3.6 | `executeSave` nutzt eingefrorenen State → Eingaben beim Speichern verloren | `executeSave()` liest `_state.value` neu statt den Snapshot-Parameter |
| P3.7 | `duplicate()` setzt `lastModifiedAt = 0` | `lastModifiedAt = System.currentTimeMillis()`, `lastUsedAt` wird nicht kopiert |

Regressionstests für alle sieben Fixes.

### P4 — Stale Snapshots (geplant, eigenes Dokument)

Ausgelöst durch den Nutzer-Report „Lebensmittel Ansicht → Vollbild → lokale
Änderung → zurück zur Lebensmittel Ansicht (Änderung noch nicht da)". Ursache:
`FoodPickerViewModel` hält `state.detailFood` als eingefrorenen Snapshot. Bei der
Analyse fanden sich fünf weitere Instanzen derselben Fehlerklasse — darunter
Datenverlust im Rezept-Editor (P4.3) und im History-Screen (P4.4).

Details, Fix-Entwurf, Testplan und Agent-Prompts:
`docs/Bugfix_Plan_Stale_Snapshots.md`.

## 3. Backlog — offene Qualitätspunkte (separate Analyse)

Keine P-Bugs offen (P1–P3 abgeschlossen, P4 in Arbeit). Diese Punkte sind
bewusst **keine Bugs**, sondern Feature-Lücken bzw. Verbesserungen — vorrangig
für die Arbeit nach Abschluss der Kernfunktion (Phase 10+):

- **OFF-`UpdateAvailable`-Status ist toter Code** — kein Produktions-Aufrufer
  von `markUpdateAvailable` → Feature-Lücke. Umsetzung, sobald ein OFF-Sync
  realisiert wird.
- **`searchRemote`-Kdoc verspricht Persistenz, Implementierung cached nicht**
  (`OfflineFirstFoodRepository.kt:41-42`) → Kommentar oder Verhalten angleichen.
- **`getByBarcode` ohne `ORDER BY`** (`FoodItemDao.kt:102-103`) → deterministische
  Präferenz (User-Food vor OFF) definieren.
- **`ON DELETE SET NULL` auf `food_item_id`** — Latentrisiko, aktuell kein
  Delete-UI.
- **Session-Cache nach Process Death verloren** — Navigation
  (`MainActivity.kt:217-241`) behandelt `getSessionItem == null` still; ggf.
  wiederherstellen oder Rückfrage.
- **Session-Cache ist nicht beobachtbar** — `getSessionItem` liest eine
  `ConcurrentHashMap` ohne Flow-Gegenstück; für P4.1/P4.5 wird er deshalb
  lokal im ViewModel gespiegelt. Mittelfristig `observeSessionItem(id)` ergänzen.
- **`DiaryDayViewModel.date` aus dem Koin-Singleton `CurrentDateProvider`
  geseedet** — Verhalten ist über `saveState`/`restoreState` beabsichtigt
  ("gemerktes Datum bleibt erhalten"); erst bei konkretem Fehlreport bearbeiten.
- **Totes Feld `latestRecipe`** in `RecipeDetailViewModel.kt:33` — kosmetisch.
- **`:feature:diary → :feature:foodpicker`** (`feature/diary/presentation/build.gradle.kts`)
  ist eine **bewusste** Abweichung von der Skill-Regel „Features never depend on
  each other": `FoodPickerSheet` ist ein geteilter Bottom-Sheet-Baustein, den Diary
  (`DiaryDayScreen.kt`) und Recipes (`RecipeEditorScreen.kt`) beide nutzen. Kein Bug.

## 4. Ablauf pro Fix

1. Regressionstest schreiben (rot stellen)
2. Fix implementieren
3. Tests grün
4. `./gradlew build`, `detekt`, `ktlintCheck` als lokales Gate
5. Commit **nur** nach expliziter Freigabe