# Refinement Plan - Phase 9 Implementation

## Overview

This document outlines the incremental refinement implementation plan for Phase 9 of the EZ Makro project. The plan follows the AGENTS.md development workflow and executes the refinement requirements across multiple development sessions.

### Architektur-Update: Immer per 100g

**Kernentscheidung:** Intern werden Nährwerte IMMER pro 100g gespeichert.
- Beim Erstellen eines Lebensmittels gibt der User immer die Nährwerte pro 100g an (Pflichtfeld)
- Optional kann zusätzlich eine Portionsgröße in Gramm angegeben werden
- Beim Loggen im Tagebuch oder Rezept wird aus den per-100g-Werten + Portionsgröße gerechnet
- Beispiel: Apfel mit 52 kcal pro 100g, Portionsgröße = 120g → eine Portion = 62,4 kcal
- `ServingUnit`-Label wird auto-generiert (z. B. `"1 Portion (120g)"`)
- ADR-003 (Rohgewicht vs. Endgewicht) gilt weiterhin unverändert

### Workflow pro Step

Jeder Step folgt dem AGENTS.md-Workflow:

1. **Planning** — Analyse & Design
2. **Implementation** — Code schreiben
3. **Unit Tests** — Tests schreiben
4. **Integration Tests** — End-to-End testen
5. **Self Review** — Code-Qualität prüfen
6. **Architecture Explanation** — Entscheidungen dokumentieren
7. **Open Questions / Human in the Loop** — Unklarheiten klären
8. **Refinement** — Plan verfeinern
9. **Acceptance Check** — Prüfen ob alle Kriterien erfüllt
10. **Commit** — Nur nach explizitem Befehl vom User

---

## ✅ Step 1 (DONE) — Food Creation: Per-100g optional

**Commit:** `e1f0498`

### Was gemacht wurde
- `FoodItem.nutritionPer100g: NutritionValues` → `NutritionValues?` (optional/nullable)
- FoodEditScreen erlaubt Speichern ohne Nährwerte
- UI zeigt "–" wenn keine Nährwerte vorhanden
- DB-Migration 3→4: `nutri_*`-Spalten nullable
- `RecipeCalculation` fallback auf `NutritionValues.ZERO` bei null
- Neue Tests: `saveWithoutPer100g`, `null nutritionPer100g` in Calculation

### Files
- `core/domain/model/FoodItem.kt`
- `feature/food/presentation/edit/FoodEditViewModel.kt`
- `feature/food/presentation/model/FoodUi.kt`
- `core/data/food/mapper/FoodItemEntityMapper.kt`
- `core/data/food/RoomFoodLocalDataSource.kt`
- `core/data/recipe/RoomRecipeLocalDataSource.kt`
- `core/domain/calculation/RecipeCalculation.kt`
- `core/database/entity/FoodItemEntity.kt`
- `core/database/dao/FoodItemDao.kt`
- `core/database/migration/Migrations.kt`

---

## ✅ Step 2 (DONE) — Recipe Diary Entry Enhancement

### Was gemacht wurde
- Diary unterstützt für Recipes sowohl `amountGrams` als auch `portionCount`
- `DiaryEntry`: Recipes können per Gramm ODER Portion geloggt werden
- Diary-UI: Auswahl zwischen Portionen und Gramm bei Rezept-Eintrag
- AddToDiaryUseCases verarbeiten beide Modi

### Files
- `core/domain/model/DiaryEntry.kt`
- `core/domain/usecase/diary/AddToDiaryUseCases.kt`
- `core/data/diary/mapper/DiaryEntryEntityMapper.kt`
- `feature/diary/presentation/day/DiaryDayViewModel.kt`
- `feature/diary/presentation/day/DiaryDayScreen.kt`
- `feature/diary/presentation/model/DiaryUi.kt`

---

## 🔲 Step 3 — Portionsgröße & Per-100g-Vereinheitlichung

### Ziel
Das UI für Lebensmittel-Erstellung auf die neue Architektur ("immer per 100g") umstellen. Foods UND Recipes im Tagebuch sollen per `portionCount` loggbar sein (wenn `servingUnit` gesetzt).

### Problem
- `nutritionPer100g` ist aktuell nullable (Step 1) — soll aber im Normalfall immer eingegeben werden
- Foods können NICHT per `portionCount` geloggt werden (nur Recipes)
- `FoodEditScreen` hat kein Feld für Portionsgröße in Gramm

### Lösung
1. **FoodEditScreen umbauen:**
   - Nährwerte pro 100g sind das primäre/zentrale Eingabefeld
   - Zusätzlich: optionales Feld "Portionsgröße in Gramm"
   - `ServingUnit` wird aus Gramm-Zahl auto-generiert (`"1 Portion (120g)"`)
   - Beim Bearbeiten: bestehende `servingUnit` in Gramm-Zahl umwandeln

2. **DiaryEntry erweitern:**
   - Foods dürfen `portionCount` setzen (wenn `servingUnit` != null)
   - Foods müssen nicht zwingend `amountGrams` setzen (dürfen auch `portionCount`)
   - Validierung: `food.servingUnit != null` für `portionCount` bei Food

3. **Diary-UI anpassen:**
   - Beim Hinzufügen eines Foods: Auswahl zwischen Gramm und Portionen
   - Portionen-Auswahl nur wenn `servingUnit` gesetzt

### Betroffene Files
- `feature/food/presentation/edit/FoodEditScreen.kt` — UI umbauen
- `feature/food/presentation/edit/FoodEditViewModel.kt` — Portionsgröße-Logik
- `feature/food/presentation/edit/FoodEditState.kt` — State erweitern
- `core/domain/model/DiaryEntry.kt` — Food darf `portionCount` setzen
- `core/domain/model/FoodItem.kt` — ggf. Revert nullable → required (oder als `require`-Nur-im-Normalfall belassen)
- `core/domain/calculation/DayNutrition.kt` — Berechnung für Food-Portionen
- `feature/diary/presentation/day/DiaryDayViewModel.kt` — Portion-Logik beim Hinzufügen
- `feature/diary/presentation/day/DiaryDayScreen.kt` — Gramm/Portion-Auswahl
- `core/presentation/diary/AddToDiarySheet.kt` — Portion-Auswahl
- `core/data/diary/mapper/DiaryEntryEntityMapper.kt` — erweiterte Logik

### Tests
- Food-Portion-Berechnung (per-100g × gramsEquivalent / 100)
- Diary-Eintrag mit `portionCount` für Food
- Validierung: `servingUnit` muss gesetzt sein für Food-Portion
- UI: Gramm/Portion-Auswahl sichtbar/nicht sichtbar

### Acceptance Criteria
- ✅ FoodEditor zeigt per-100g-Felder als primäre Eingabe + optional "Portionsgröße (g)"
- ✅ `ServingUnit` wird aus Gramm-Zahl auto-generiert
- ✅ Foods können per `portionCount` im Tagebuch geloggt werden
- ✅ Berechnung: Portion-Makros = `nutritionPer100g × gramsEquivalent / 100`
- ✅ Diary-UI zeigt Auswahl Gramm/Portion beim Food-Hinzufügen
- ✅ Bestehende Tests passen weiterhin
- ✅ `./gradlew build` + `./gradlew detekt` + `./gradlew ktlintCheck` grün

---

## 🔲 Step 4 — Enhanced Diary UI (Collapsible Entries)

### Ziel
Die Tagebuch-Übersicht zeigt nur Makro-Zusammenfassungen pro Mahlzeit. Beim Antippen einer Mahlzeit werden die Details sichtbar (einzelne Lebensmittel mit Makros). Einträge sind editierbar (Menge ändern, löschen).

### Problem
- DiaryDayScreen zeigt alle Einträge als flache Liste
- Keine Expand/Collapse-Funktionalität
- Keine Makro-Details pro Lebensmittel in der Übersicht
- Keine Edit-Funktion für Einträge

### Lösung
1. **Neue Composables:**
   - `CollapsibleMealSlot`: Mahlzeit mit Makro-Zusammenfassung + Expand-Button
   - `DiaryEntryDetail`: Zeigt Makros pro Lebensmittel/Rezept + Edit/Delete-Buttons
   - `EditPortionDialog`: Dialog zum Ändern der Menge (Gramm/Portion)

2. **State erweitern:**
   - `DiaryDayState` um `expandedSlots: Set<MealSlot>`
   - `DiaryDayViewModel` um Expand/Collapse-Actions

3. **Edit/Delete:**
   - Delete: bereits vorhanden (DeleteConfirmDialog)
   - Edit: neuer Dialog/Sheet für Mengen-Änderung
   - Nach Edit: `updateEntry` im Repository aufrufen

### Betroffene Files
- `feature/diary/presentation/day/DiaryDayScreen.kt` — Haupt-UI-Redesign
- `feature/diary/presentation/day/DiaryDayState.kt` — `expandedSlots` + Edit-State
- `feature/diary/presentation/day/DiaryDayAction.kt` — Expand/Collapse/Edit-Actions
- `feature/diary/presentation/day/DiaryDayEvent.kt` — ggf. neue Events
- `feature/diary/presentation/day/DiaryDayViewModel.kt` — neue Actions verarbeiten
- `feature/diary/presentation/model/DiaryUi.kt` — ggf. neue UI-Modelle
- `core/presentation/diary/` — neue Composables
- `core/domain/repository/DiaryRepository.kt` — ggf. `updateEntry`
- `core/domain/usecase/diary/` — ggf. neuer UseCase

### Tests
- DiaryDayViewModel: Expand/Collapse-State
- DiaryDayViewModel: Edit-Menge-Update
- UI: Collapsible verhalten sich korrekt
- UI: Edit-Dialog öffnet/schließt

### Acceptance Criteria
- ✅ Makro-Zusammenfassung pro Mahlzeit (kcal, Protein, Fett, KH, Ballaststoffe)
- ✅ Expand/Collapse pro Mahlzeit mit Animation
- ✅ Detail-Ansicht mit jedem Lebensmittel + Makros + Menge
- ✅ Editieren der Menge pro Eintrag (Gramm/Portion)
- ✅ Löschen pro Eintrag (bereits vorhanden)
- ✅ Alle Tests grün
- ✅ `./gradlew build` + `./gradlew detekt` + `./gradlew ktlintCheck` grün

---

## 🔲 Step 5 — Date Navigation

### Ziel
Im Tagebuch können Tage vor und zurück gewechselt werden. Future-Entries und Vergangenheits-Änderungen sind möglich. Default beim Öffnen ist der aktuelle Tag. Navigation auch vom Dashboard aus.

### Problem
- Diary zeigt nur den aktuellen Tag (kein Wechsel möglich)
- Keine Möglichkeit Future-Entries zu erfassen
- Kein Datums-Sprung aus History/Dashboard
- `DiaryDayViewModel` lädt festes Datum aus `SavedStateHandle`

### Lösung
1. **Navigation UI:**
   - Prev/Next-Pfeile in der TopAppBar
   - Datum-Anzeige zwischen den Pfeilen
   - Antippen des Datums → DatePicker oder Kalender

2. **ViewModel:**
   - `date` wird über Action geändert (nicht nur init)
   - `onAction(OnPreviousDay)`, `onAction(OnNextDay)`, `onAction(OnGoToDate(date))`
   - Flow wird bei Datums-Wechsel neu gestartet

3. **Routing:**
   - History → Day: `navigate("diary/day/$date")`
   - Dashboard → Day: `navigate("diary/day/$date")`
   - Nav-Back kehrt zum vorherigen Datum zurück

4. **Dashboard-Integration:**
   - Dashboard zeigt aktuellen Tag + Makros
   - Klick auf Tag → öffnet Diary an diesem Datum
   - Dashboard hat auch Prev/Next

### Betroffene Files
- `feature/diary/presentation/day/DiaryDayViewModel.kt` — date-Änderungen verarbeiten
- `feature/diary/presentation/day/DiaryDayScreen.kt` — Navigation UI (Pfeile, Datum)
- `feature/diary/presentation/day/DiaryDayAction.kt` — neue Navigation-Actions
- `feature/diary/presentation/day/DiaryDayState.kt` — aktuelles Datum
- `feature/diary/presentation/navigation/DiaryRoutes.kt` — Routing
- `feature/diary/presentation/history/HistoryViewModel.kt` — Jump-to-Date
- `core/domain/datasource/DiaryLocalDataSource.kt` — datums-basierte Queries
- `core/data/diary/RoomDiaryLocalDataSource.kt` — datums-basierte Queries
- `feature/dashboard/presentation/DashboardScreen.kt` — Tag-Navigation
- `feature/dashboard/presentation/DashboardViewModel.kt` — Tag-Wechsel

### Tests
- DiaryDayViewModel: date-Wechsel via Action
- DiaryDayViewModel: Neuladen bei Datums-Wechsel
- Navigation: prev/next ändert den State korrekt
- Dashboard: Klick auf Tag öffnet Diary an dem Datum

### Acceptance Criteria
- ✅ Prev/Next-Pfeile im Diary ändern den Tag
- ✅ Datum wird in der TopAppBar angezeigt
- ✅ Klick auf Datum → DatePicker (optional)
- ✅ Future-Entries sind möglich
- ✅ Vergangenheits-Änderungen sind möglich
- ✅ Default beim Öffnen: aktueller Tag
- ✅ Dashboard hat Navigation zum Diary mit korrektem Datum
- ✅ History → Day funktioniert
- ✅ Alle Tests grün
- ✅ `./gradlew build` + `./gradlew detekt` + `./gradlew ktlintCheck` grün

---

## 🔲 Step 6 — Recipe-as-Ingredient (Snapshot Independence)

### Ziel
Rezepte sollen als Zutat in anderen Rezepten verwendet werden können (z. B. Pizzateig in Pizza mit Toppings). Dabei sind sie unabhängig: Wenn das innere Rezept geändert oder gelöscht wird, bleibt das äußere Rezept unverändert.

### Architektur
**Referenz + Snapshot:** Die Nährwerte werden beim Hinzufügen eingefroren (Snapshot), zusätzlich bleibt eine Referenz auf das Original-Rezept. Die UI zeigt an, ob sich das Original geändert hat.

### Lösung
1. **Neues Domain-Modell: `RecipeSnapshot`:**
   ```kotlin
   data class RecipeSnapshot(
       val recipeId: RecipeId,
       val recipeName: String,
       val nutritionPer100g: NutritionValues,
       val originalLastModifiedAt: Long?,
   )
   ```

2. **`RecipeIngredient` erweitern:**
   ```kotlin
   data class RecipeIngredient(
       val food: FoodItem?,
       val recipeSnapshot: RecipeSnapshot?,
       val rawWeight: RawWeight?,
       val servingCount: Double?,
   )
   ```
   - `food` XOR `recipeSnapshot` (eines von beiden muss gesetzt sein)
   - `rawWeight` oder `servingCount` für Mengenangabe

3. **RecipeEditor anpassen:**
   - Zutaten-Picker zeigt auch Rezepte an
   - Beim Hinzufügen eines Rezepts: Snapshot erstellen (aktuelle Nährwerte + Name kopieren)
   - Menge kann als Gramm oder Portionen eingegeben werden
   - Portionen werden über per-100g + finalWeight/portions berechnet

4. **Snapshot-Aktualisierung (optional, später):**
   - UI-Badge "Original geändert" wenn `originalLastModifiedAt` vom aktuellen Recipe abweicht
   - Button "Snapshot aktualisieren"

### Betroffene Files
- `core/domain/model/RecipeSnapshot.kt` — NEU
- `core/domain/model/RecipeIngredient.kt` — erweitern
- `core/domain/model/Recipe.kt` — ggf. Anpassung
- `core/domain/model/DiaryEntry.kt` — recipe-Snapshot in Diary (optional)
- `core/domain/calculation/RecipeCalculation.kt` — Snapshot-Berechnung
- `core/data/recipe/mapper/RecipeEntityMapper.kt` — Snapshot persistieren
- `core/database/entity/RecipeEntity.kt` — Snapshot-Felder
- `core/database/dao/RecipeDao.kt` — ggf. Queries
- `feature/recipes/presentation/editor/RecipeEditorViewModel.kt` — Snapshot-Logik
- `feature/recipes/presentation/editor/RecipeEditorState.kt` — Snapshot-State
- `feature/recipes/presentation/editor/RecipeEditorScreen.kt` — Recipe-Picker
- `core/presentation/diary/AddToDiarySheet.kt` — ggf. Recipe-Snapshot

### Tests
- RecipeSnapshot: Erstellung + Aktualisierung
- RecipeIngredient: `food` XOR `recipeSnapshot`
- Berechnung: Snapshot-Nährwerte werden korrekt verwendet
- Independence: Änderung am Original ändert Snapshot NICHT
- UI: Recipe-Picker zeigt Recipes an

### Acceptance Criteria
- ✅ Recipe-Picker zeigt Recipes als Zutaten-Auswahl
- ✅ Beim Hinzufügen wird Snapshot erstellt (Nährwerte + Name)
- ✅ Änderungen am inneren Rezept beeinflussen äußeres NICHT
- ✅ Löschen des inneren Rezepts beeinflusst äußeres NICHT
- ✅ Menge kann als Gramm oder Rezept-Portionen eingegeben werden
- ✅ DB-Schema unterstützt Snapshot-Persistierung
- ✅ Migration für bestehende Recipes (Snapshot = null)
- ✅ Alle Tests grün
- ✅ `./gradlew build` + `./gradlew detekt` + `./gradlew ktlintCheck` grün

---

## 🔲 Step 7 — Unified Food Interface Analysis

### Ziel
Analysieren, ob und wie ein einheitliches Lebensmittel-Hinzufügen-Interface (ähnlich Foodvisor) implementiert werden soll. Ein Bottom-Sheet-Dialog über den ganzen Screen mit drei Tabs: Barcode, Suche, Meine Lebensmittel.

### Analyse
1. **Aktuelle Situation:**
   - Food hinzufügen: `FoodEditScreen`, `BarcodeScanScreen`, `FoodSearchScreen`
   - Recipe hinzufügen: `RecipeEditorScreen`
   - Diary hinzufügen: `AddToDiarySheet`, `QuickAddScreen`
   - Alle haben eigene Einstiegspunkte und UIs

2. **Option A: Refactor bestehender Interfaces**
   - `AddToDiarySheet` zum zentralen Bottom-Sheet ausbauen
   - Tabs: Barcode, Suche, Meine Lebensmittel
   - Vorteil: kein neues Modul nötig
   - Nachteil: Komplexität in `:core-presentation` steigt

3. **Option B: Neues Feature-Modul `:feature-foodpicker`**
   - Eigenes Modul nach Clean Architecture
   - Klare Trennung, wiederverwendbar
   - Vorteil: saubere Architektur
   - Nachteil: neues Modul, Aufwand

4. **Option C: Graduelle Migration**
   - Erst `AddToDiarySheet` ausbauen
   - Später in eigenes Modul auslagern
   - Vorteil: schneller erste Ergebnisse
   - Nachteil: temporäre Unschärfe

### Deliverables
- Vergleichsanalyse der Optionen
- Architecture Impact Assessment
- Risiko/Nutzen-Bewertung
- Detaillierte Empfehlung mit Begründung
- Aufwandsschätzung

### Acceptance Criteria
- ✅ Analyse dokumentiert
- ✅ Empfehlung mit Begründung
- ✅ Aufwandsschätzung
- ✅ Entscheidung getroffen (vom User)

---

## Cross-Step Dependencies

```mermaid
graph TD
    A[Step 1 ✅ DONE] --> B[Step 2 ✅ DONE]
    B --> C[Step 3 🔲]
    C --> D[Step 4 🔲]
    D --> E[Step 5 🔲]
    E --> F[Step 6 🔲]
    F --> G[Step 7 🔲]
```

**Key Dependencies:**
- **Step 3** (portionsgröße) benötigt **Step 1+2** als Basis
- **Step 4** (collapsible UI) benötigt **Step 3** (Portion-Logik in Diary)
- **Step 5** (date nav) benötigt **Step 4** (aktuelle Diary-UI)
- **Step 6** (recipe-as-ingredient) kann parallel zu **Step 4+5** laufen
- **Step 7** (food interface) ist letzter Schritt, baut auf allen auf

---

## Shared Requirements

### Architecture Adherence
- Alle Änderungen folgen Clean Architecture
- Dependency Direction: `app → feature → core → domain`
- Feature-Module bleiben unabhängig voneinander
- Domain hat KEINE Android-Abhängigkeiten

### Code Quality
- `./gradlew detekt` muss grün sein
- `./gradlew ktlintCheck` muss grün sein
- `./gradlew build` muss grün sein
- Keine TODOs im Code (nur im Plan)
- Type Safety und Null Safety erhalten

### Testing
- Unit Tests für alle Domain-Logik
- Integrationstests für End-to-End-Flows
- ViewModel-Tests mit Turbine + UnconfinedTestDispatcher
- Bestehende Tests müssen weiterhin passen

### Commit-Regel
Niemals automatisch committen. Erst nach explizitem Befehl vom User (`Commit Phase X`).

---

**Document Version:** 2.0
**Created:** 2026-07-29
**Project:** EZ Makro Phase 9 Refinement
**Status:** Aktualisiert (Steps 1+2 DONE)
