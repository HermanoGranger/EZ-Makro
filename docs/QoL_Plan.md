# QoL-Plan — Quality of Life Fixes

Stand: 2026-09-18. Vier Arbeitspakete zur Verbesserung der Nutzerfreundlichkeit:
einheitliche Zahlenformatierung, Inline-Detail im FoodPicker, Makros im
Rezepteditor und ein Tagebuch-Layout, das alles auf einen Blick zeigt.

Jedes Arbeitspaket wird durch einen eigenen Agent-Prompt abgearbeitet (siehe
letzter Abschnitt). Gültig ist immer das lokale Gate: `./gradlew build`,
`./gradlew detekt`, `./gradlew ktlintCheck`. Kein Commit ohne explizite
Freigabe (`Commit <X>`).

## Überblick

| WP | Thema | Kernproblem | Lösung |
|----|-------|-------------|--------|
| 1 | Zahlenformatierung | 14 verstreute Formatier-Regeln (1–10 Dezimalstellen, Truncation, rohe Doubles) | Eine zentrale `formatValue(maxDecimals = 2)`; alte Helfer entfernen |
| 2 | FoodPicker-Navigation | Row-Tap verdrängt den Picker durch Vollbild-Detail → Kontextverlust im Rezepteditor | Inline-Detail-Panel im Picker (kein Screen-Wechsel) |
| 3 | Makros im Rezepteditor | Zutaten zeigen keine Nährwerte während der Erstellung | Pro-Zutat- und Gesamt-Makros live im Editor |
| 4 | Tagebuch auf einen Blick | Slots starten collapsed, Header zeigt Makros nur als Balken | Donut-Header (Kalorien links, 2×2 Makro-Donuts rechts), alle Slots standardmäßig expandiert |

---

## WP 1 — Einheitliche Zahlenformatierung (max 2 Nachkommastellen)

### Ziel

Jede nutzerseitige Anzeige numerischer Werte (kcal, g, Mengen) rundet auf maximal
2 Nachkommastellen (HALF_UP) und trimmte Nachkomma-Nullen
(`12.34`, `12.3`, `42`). Keine Truncation, keine 1-Dezimal-Beschränkung, keine
rohen `Double.toString()`-Werte in der UI.

### Umsetzung

**1. Zentrale Utility neu anlegen**
`core/presentation/src/main/java/com/example/ezmakro/core/presentation/util/Formatting.kt`:

```kotlin
fun Double.formatValue(maxDecimals: Int = 2): String {
    check(maxDecimals >= 0)
    val scaled = BigDecimal(toString()).setScale(maxDecimals, RoundingMode.HALF_UP)
    return if (maxDecimals == 0) scaled.toPlainString()
           else scaled.stripTrailingZeros().toPlainString()
}
```

Alle Feature-Module hängen bereits an `core/presentation`.

**2. Duplizierte Helfer ersetzen** (jeweils durch `formatValue()`):

- `feature/diary/.../model/DiaryUi.kt` — `formatMacro()` (Zeilen ~42–47, inkl.
  `MACRO_DECIMAL_PLACES`) entfernen; Aufrufer (Entry-Ui-Zeilen, `sumNutrition()`)
  umstellen. `sumNutrition()` parst die formatierten Strings weiterhin nach Double.
- `feature/diary/.../model/DiarySummaryUi.kt` — `formatSigned()` nutzt
  `formatValue()`.
- `feature/food/.../model/FoodUi.kt` — `formatMacro()`/`formatOrDash()`
  (Zeilen ~44–52) ersetzen.
- `feature/recipes/.../model/RecipeUi.kt` — `formatMacro()` (Zeilen ~29–34)
  ersetzen.
- `feature/foodpicker/.../picker/search/SearchTab.kt` + `myfoods/MyFoodsTab.kt` —
  lokales `formatKcal()` (`%.1f`, `Locale.US`) ersetzen.

**3. Rohe Doubles / Truncation beheben:**

- `feature/recipes/.../detail/RecipeDetailScreen.kt` Zeilen ~232–246 (kcal + P/F/KH/B
  als rohe Doubles) — Werte mit `formatValue()` formatieren. Sauberste Stelle: die
  Werte bereits im `IngredientDetailUi` (`RecipeDetailState.kt`) bzw. bei der
  Befüllung im `RecipeDetailViewModel.kt` (Zeilen ~51–67) formatieren.
- `feature/recipes/.../detail/RecipeDetailViewModel.kt` — `rawWeight.grams.toInt().toString()`
  (Truncation) → `formatValue()`.
- `feature/recipes/.../editor/IngredientRow.kt` — `rawWeight.toInt().toString()`
  (Truncation) → `formatValue()`; `$servingCount` raw → `formatValue()`.
- `feature/recipes/.../editor/RecipeEditorScreen.kt` — `gramsPerPortion.toInt()`
  → `formatValue()`.

**4. Formular-Prefills normalisieren** (Read-only-Belegung vor dem Editieren):

- `feature/diary/.../goals/GoalsViewModel.kt` — `trimZeros()` (Zeile ~115, Aufrufer
  37–41) durch `formatValue()` ersetzen.
- `feature/recipes/.../editor/RecipeEditorViewModel.kt` — `loadExisting()`
  (Zeilen ~225–249): `toString()`/`toInt()`-Prefills durch `formatValue()` ersetzen.
- `feature/food/.../edit/FoodEditViewModel.kt` — Form-Prefill (Zeilen ~93–98) und
  Portions-Label (Zeilen ~201–203) normalisieren.
- `core/presentation/.../diary/EditPortionDialog.kt` — Prefill-Branching
  (Zeilen ~46,49) durch `formatValue()` ersetzen.

### Betroffene Tests

- `feature/food/.../model/FoodUiTest.kt` — erwartete Format-Strings anpassen.
- `feature/recipes/.../editor/RecipeEditorViewModelTest.kt` — `"200.0"` →
  `"200"` (Zeile ~139 und weitere Prefill-Asserts).
- `feature/diary/.../day/DiaryDayViewModelTest.kt` — Werte wie `"200"` bleiben
  (keine Änderung nötig, nur verifizieren).

---

## WP 2 — FoodPicker: Inline-Detail statt Screen-Wechsel

### Ziel

Row-Tap auf ein Food/Rezept im FoodPicker öffnet die Details **innerhalb des
Pickers** (Inline-Panel), statt auf die volle `food_detail`/`recipe_detail`-Route
zu navigieren. Der Nutzer prüft die Makros und fügt hinzu, ohne den Kontext zu
verlassen (besonders wichtig im Rezepteditor mit halb ausgefülltem Formular).

### Entscheidungen (fix)

- Row-Tap geht **immer** ins Inline-Panel (alle drei Targets: DIARY,
  RECIPE_INGREDIENT, BROWSE).
- Barcode-Scan-Verhalten bleibt wie heute (DIARY/BROWSE → NavigateToFoodDetail/
  FoodCreate; RECIPE_INGREDIENT → AddAsIngredient).
- Das Inline-Panel hat einen sekundären „Vollbild öffnen"-Button, der das
  bestehende `NavigateToFoodDetail`/`NavigateToRecipeDetail`-Event emittiert
  (für Bearbeiten-/Zurücksetzen-Flows).

### Umsetzung

**1. `FeatureState` erweitern** (`FoodPickerState.kt`):

```kotlin
val detailFood: FoodItem? = null,
val detailRecipe: Recipe? = null,
```

**2. `FoodPickerViewModel`** (`onFoodClick`/`onRecipeClick` umbauen):

- `onFoodClick(id)`:
  - Online-Item aus `onlineFoodItems` nehmen, sonst `foodRepository.getById`
    (mit `getSessionItem`-Fallback — Muster wie `makeAddFoodToDiary`).
  - `markUsed`/`rememberRemoteItem` bleibt wie heute.
  - Statt `NavigateToFoodDetail` → `_state.update { it.copy(detailFood = …) }`.
- `onRecipeClick(id)` analog → `detailRecipe`.
- Neue Actions: `OnDismissDetail` (cleart beides), `OnDetailAddClick` →
  dispatch auf `onAddClick(id)` / `onAddRecipeClick(id)`, damit alle vorhandenen
  Target-Pfade (AddToDiarySheet / AddAsIngredient) wiederverwendet werden.
- `_events.send(NavigateToFoodDetail)` bleibt nur für den „Vollbild"-Button,
  `FoodCreate` und Barcode-Flows erhalten.

**3. `FoodPickerSheet.kt` / `FoodPickerContent`:**

- Wenn `state.detailFood != null || state.detailRecipe != null` → statt
  TabRow/Content ein neues `FoodPickerDetailPanel` rendern (modular, private Composable
  oder eigene Datei im Picker-Package):
  - Kopfzeile mit Zurück-Button (X) + Name
  - Nährwerte: kcal + P/F/KH/B pro 100 g (mit `formatValue()`, WP 1)
  - Favoriten-Toggle (`OnToggleFavorite`)
  - Primär-Button „+ Hinzufügen" → `OnDetailAddClick`
  - Sekundär-Button „Vollbild öffnen" → `NavigateToFoodDetail`/`NavigateToRecipeDetail`
- Hinweis: `FoodPickerContent` wird auch als Vollbild-Host im Food-Tab
  (`FoodSearchScreen`) wiederverwendet — das Panel wirkt dort genauso.

**4. `RecipeEditorScreen.kt`:** Der Workaround-Charakter des
`rememberSaveable { showFoodPicker }` entfällt praktisch (kein Screen-Wechsel mehr);
Verhalten unverändert lassen.

**5. Cleanup:** `feature/recipes/.../editor/IngredientPickerDialog.kt` ist toter Code
(wird nirgends aufgerufen) — löschen.

### Betroffene Tests

- `feature/foodpicker/.../FoodPickerViewModelTest.kt`:
  - Alle `foodClick`/`recipeClick`-Tests, die `NavigateToFoodDetail`/
    `NavigateToRecipeDetail` erwarten (BROWSE/DIARY/RECIPE_INGREDIENT, ~6 Stück) →
    auf `state.detailFood`/`state.detailRecipe` umstellen.
  - Neu: `OnDetailAddClick`-Tests (DIARY → `AddToDiary`-Flow, RECIPE_INGREDIENT →
    `AddAsIngredient`), `OnDismissDetail`.
  - Barcode-Tests unverändert.
- `feature/recipes/.../editor/RecipeEditorViewModelTest.kt` — unverändert.

---

## WP 3 — Makros pro Zutat im Rezepteditor

### Ziel

Während der Rezepterstellung sieht der Nutzer die Nährwerte jeder Zutat (kcal,
P/F/KH/B) — auch live reagierend auf Mengenänderungen — sowie eine
Gesamtübersicht des Rezepts.

### Umsetzung

**1. `RecipeEditorState.kt` erweitern:**

```kotlin
val ingredientNutrition: List<NutritionValues> = emptyList(), // Index == Ingredient-Index
val totalNutrition: NutritionValues = NutritionValues.ZERO,
```

**2. `RecipeEditorViewModel`:**

- Private `recomputeNutrition()`:
  - Für jeden `IngredientDraft` über `resolveFoodIngredient`/
    `resolveRecipeIngredient` (bestehende Methoden) einen `RecipeIngredient` bauen
    und `RecipeCalculation.nutrition()` aufrufen (Domain-Extension existiert in
    `core/domain/.../calculation/RecipeCalculation.kt`).
  - Ungültige Drafts (keine positive Menge / fehlendes Food) → `NutritionValues.ZERO`
    für diesen Index.
  - `totalNutrition = Summe`.
  - `_state.update` mit `ingredientNutrition` + `totalNutrition`.
- Aufrufen nach jeder relevanten Mutation: `OnAddIngredientClick`,
  `OnAddRecipeIngredientClick`, `OnIngredientRawWeightChange`,
  `OnIngredientServingCountChange`, `OnRemoveIngredientClick`, sowie nach
  `loadExisting()` und nach Food-/Recipe-Flow-Emissionen (falls Zutaten dadurch
  aufgelöst werden).
- Keine Änderung an `IngredientDraft` (bleibt wie ist; Berechnung wird deriviert).

**3. `IngredientRow.kt`:**

- Parameter erweitern: `nutrition: NutritionValues?`.
- `IngredientMacroDetail` (expandiert): statt Platzhalter-Hint die realen Werte
  rendern — `kcal` + `P/KH/F/B` mit `formatValue()` („P 12,3 g · KH 40 g …").
- Kompakter Zustand (nicht expandiert): zusätzlich eine kcal-Zeile.

**4. `RecipeEditorScreen.kt`:**

- Direkt unter den Grundfeldern (Name/Kategorie/Portionen/Endgewicht/„g pro
  Portion") eine kompakte Gesamtübersicht einblenden: `totalNutrition` mit
  kcal + P/F/KH/B, live aktualisiert bei jeder Änderung.

### Betroffene Tests

- `feature/recipes/.../editor/RecipeEditorViewModelTest.kt`:
  - Bestehende Tests verifizieren (State bekommt zusätzliche Felder, Default
    `emptyList()`/`ZERO`).
  - Neu: Test, dass nach `OnAddIngredientClick + OnIngredientRawWeightChange("100")`
    `ingredientNutrition[0]` korrekt berechnet wird (z. B. Food 100 kcal/100g,
    10 g Protein → kcal=100, protein=10); Test für `totalNutrition`-Summe über
    mehrere Zutaten; Test für `ZERO` bei fehlender Menge.
  - Test: nach `OnRemoveIngredientClick` kollabieren die Indizes korrekt.

---

## WP 4 — Tagebuch: Alles auf einen Blick

### Ziel

- Der Tages-Header zeigt die Kalorien als großes Donut (links) und die 4 Makros
  als 2×2-Grid Mini-Donuts (rechts).
- Alle Meal-Slots sind beim Öffnen **standardmäßig expandiert** (Collapse bleibt
  erhalten, nur der Default ändert sich) → Plus-Buttons sofort sichtbar,
  Einträge sofort lesbar.

### Entscheidungen (fix)

- Farben aus dem bestehenden Theme (erste Wahl): Protein = `primary`, Fett =
  `tertiary`, Kohlenhydrate = `secondary`, Ballaststoffe = `error`.
- Der Scroll-Collapse des Headers bleibt erhalten; collapsed wird weiterhin die
  kompakte Makro-Balken-Variante gezeigt.

### Umsetzung

**4a) Donut-Komponenten** (`core/design-system/.../component/MacroProgressBar.kt`):

1. `CalorieRing` generalisieren: neue Parameter `size: Dp = 160.dp` und
   `color: Color? = null` (null → bisherige primary/error-Logik). Stroke
   proportional skalieren (heute 14dp/160dp ≈ 8.75 %).
2. Neue `MacroMiniRing`-Komponente (dünner Wrapper):
   `(label, centerText, progress, color, size: Dp = 56.dp)`.

**4b) `CollapsingSummaryHeader.kt` Expanded-Layout:**

```kotlin
Row(verticalAlignment = CenterVertically) {
    CalorieRing(size = ~120.dp, centerText = summary.remainingKcal,
                caption = "Restkalorien", progress = summary.kcalProgress,
                isOverGoal = summary.isOverGoal)          // links
    Spacer(16.dp)
    Column(weight(1f)) {                                  // rechts 2×2
        Row { MacroMiniRing(Protein) ; MacroMiniRing(Fett) }
        Spacer(8.dp)
        Row { MacroMiniRing(Kohlenhydrate) ; MacroMiniRing(Ballaststoffe) }
    }
}
```

- Datenquelle ist das bestehende `DiarySummaryUi` (`macros: List<MacroRowUi>`
  mit `consumed`/`goal`/`progress`; `kcalProgress` etc.). Keine neue Domain-Mathematik.
- „Verbraucht X / Y kcal"-Zeile unter dem Ring beibehalten.
- Collapsed-Grafik: bisherige kompakte Makro-`SummaryMacroRow`s behalten
  (Ring/Donut-Sektion kollabiert und blendet aus wie bisher).

**4c) Slots standardmäßig expandiert:**

- `feature/diary/.../day/DiaryDayState.kt`:
  `expandedSlots: Set<MealSlot> = MealSlot.entries.toSet()`.
- `feature/diary/.../day/DiaryDayViewModel.kt` (Zeile ~115): Beim Datumswechsel
  `expandedSlots` auf `MealSlot.entries.toSet()` statt `emptySet()` setzen.

### Betroffene Tests

- `feature/diary/.../day/DiaryDayViewModelTest.kt`:
  - `OnToggleSlot`-Tests anpassen: Start = alle Slots expandiert; erster Toggle
    entfernt den Slot; zweiter fügt ihn wieder hinzu.
  - Datumswechsel-Test: `expandedSlots` = alle Slots.
- `CollapsingSummaryHeaderTest.kt` — testet `collapseFractionOf` pur; bleibt
  gültig (nur verifizieren).
- `feature/diary/.../day/DiaryDayScreenTest.kt` (androidTest) — ggf.
  Erwartungen zu collapsed/expanded prüfen.

---

## Reihenfolge & Abschluss

1. WP 1 → 2 → 3 → 4, jeweils nach Fertigstellung `./gradlew build` +
   `./gradlew detekt` + `./gradlew ktlintCheck` und Unit-/Instrumented-Tests.
2. Jedes WP einzeln committen lassen (z. B. `Commit QoL WP 1`), nie von selbst.
3. Am Ende Gesamtdurchlauf des lokalen Gates.

## Abgrenzung / bewusst NICHT in diesem Plan

- Keine Änderung an Domain-Berechnungen (`DayNutrition`, `RecipeCalculation`) —
  nur Anzeige.
- Kein Nutzerwechsel der Donut-Farben / Einstellungen.
- Kein Verhalten von Favoriten-/Suchfiltern außer der Inline-Detail-Umstellung.

---

# Agent-Prompts (pro Arbeitspaket)

## Prompt WP 1

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lese zuerst `docs/QoL_Plan.md`,
> Abschnitt „WP 1 — Einheitliche Zahlenformatierung", sowie die relevanten
> Skills (`core/presentation-*` ist nicht nötig, aber `android-module-structure`,
> `android-testing`).
>
> Umsetzung:
> 1. Lege `core/presentation/src/main/java/com/example/ezmakro/core/presentation/util/Formatting.kt`
>    mit `fun Double.formatValue(maxDecimals: Int = 2): String` an (BigDecimal
>    aus `toString()`, `HALF_UP`, Trailing-Zeros trimmen; bei `maxDecimals == 0`
>    `toPlainString()`; `check(maxDecimals >= 0)`).
> 2. Ersetze alle im Plan gelisteten Duplikate (`formatMacro` in DiaryUi,
>    FoodUi, RecipeUi; `formatSigned`; `formatKcal` in SearchTab/MyFoodsTab)
>    durch `formatValue()`. Entferne die lokalen Helfer samt Konstanten.
> 3. Behebe die rohen `Double.toString()`- und `.toInt()`-Truncation-Stellen
>    (RecipeDetailScreen/ViewModel, IngredientRow, RecipeEditorScreen).
> 4. Normalisiere die Formular-Prefills (GoalsViewModel, RecipeEditorViewModel
>    `loadExisting`, FoodEditViewModel, EditPortionDialog).
> 5. Passe die betroffenen Tests an (FoodUiTest, RecipeEditorViewModelTest,
>    ggf. DiaryDayViewModelTest). Füge einen kleinen Test für `formatValue()`
>    hinzu (Rundung, Trailing-Zeros, ganze Zahlen).
> 6. Verifiziere: `./gradlew test detekt ktlintCheck`. Nur Testen + fixen, keine
>    Commits. Meldung: Liste der geänderten Dateien + Test-Ergebnis.

## Prompt WP 2

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lese zuerst `docs/QoL_Plan.md`,
> Abschnitt „WP 2 — FoodPicker: Inline-Detail", sowie
> `feature/foodpicker/` vollständig und `feature/recipes/.../editor/RecipeEditorScreen.kt`,
> `feature/diary/.../day/DiaryDayScreen.kt`, `feature/food/.../search/FoodSearchScreen.kt`
> und `app/src/main/java/com/example/ezmakro/MainActivity.kt`. Skill
> `android-presentation-mvi` laden.
>
> Umsetzung:
> 1. `FoodPickerState`: `detailFood: FoodItem?` + `detailRecipe: Recipe?` ergänzen.
> 2. `FoodPickerViewModel`: `onFoodClick`/`onRecipeClick` auf Inline-Detail
>    umstellen (Online-Item aus `onlineFoodItems`, sonst getById/getSessionItem;
>    `markUsed`/`rememberRemoteItem` beibehalten). Neue Actions `OnDismissDetail`
>    und `OnDetailAddClick` (dispatch auf `onAddClick`/`onAddRecipeClick`).
>    `NavigateToFoodDetail`/`NavigateToRecipeDetail`-Events bleiben für den
>    Vollbild-Button und Barcode-Flows erhalten (Barcode-Verhalten NICHT ändern).
> 3. `FoodPickerContent`: Wenn Detail gesetzt, stattdessen ein
>    `FoodPickerDetailPanel` rendern (Zurück, Nährwerte mit `formatValue()` aus
>    WP 1 — falls WP 1 noch nicht gemergt, direkt `core/presentation/.../util/Formatting.kt`
>    anlegen bzw. verwenden, sonst existiert sie bereits —, Favoriten-Toggle,
>    „+ Hinzufügen", „Vollbild öffnen").
> 4. Lösche toten Code `feature/recipes/.../editor/IngredientPickerDialog.kt`.
> 5. Passe `FoodPickerViewModelTest` an (foodClick/recipeClick → Detail-State;
>    neue Tests für OnDetailAddClick/OnDismissDetail; Barcode-Tests unverändert).
> 6. Verifiziere: `./gradlew test detekt ktlintCheck`. Keine Commits. Meldung:
>    Änderungsliste + Test-Ergebnis.

## Prompt WP 3

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lese zuerst `docs/QoL_Plan.md`,
> Abschnitt „WP 3 — Makros pro Zutat im Rezepteditor", sowie
> `feature/recipes/.../editor/` (State, ViewModel, Screen, IngredientRow) und
> `core/domain/.../calculation/RecipeCalculation.kt`. Skill
> `android-presentation-mvi` laden.
>
> Umsetzung:
> 1. `RecipeEditorState`: `ingredientNutrition: List<NutritionValues>` +
>    `totalNutrition: NutritionValues` ergänzen.
> 2. `RecipeEditorViewModel`: `recomputeNutrition()` implementieren (Drafts über
>    `resolveFoodIngredient`/`resolveRecipeIngredient` auflösen, `RecipeIngredient.nutrition()`
>    aus `RecipeCalculation` anwenden; ungültige Drafts → `ZERO`; Summe). Nach
>    allen Ingredient-Mutationen und nach `loadExisting()`/Food-Recipe-Flow-Emissionen
>    aufrufen. `IngredientDraft` NICHT verändern.
> 3. `IngredientRow`: `nutrition: NutritionValues?`-Parameter; expandierter
>    Bereich zeigt reale Makros (statt Platzhalter-Hint), kompakter Bereich eine
>    kcal-Zeile. Formatierung über `formatValue()` (WP 1).
> 4. `RecipeEditorScreen`: Live-Gesamtübersicht (`totalNutrition`) unter den
>    Grundfeldern einblenden.
> 5. Tests: bestehende verifizieren, neue für `ingredientNutrition`/`totalNutrition`
>    ergänzen (korrekt bei 100 g, ZERO ohne Menge, Summe über mehrere Zutaten,
>    Index-Kollaps nach Remove).
> 6. Verifiziere: `./gradlew test detekt ktlintCheck`. Keine Commits. Meldung:
>    Änderungsliste + Test-Ergebnis.

## Prompt WP 4

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lese zuerst `docs/QoL_Plan.md`,
> Abschnitt „WP 4 — Tagebuch: Alles auf einen Blick", sowie
> `core/design-system/.../component/MacroProgressBar.kt`,
> `feature/diary/.../day/CollapsingSummaryHeader.kt` (inkl.
> `CollapsingRingSection`, `SummaryMacroRow`), `DiaryDayState.kt`,
> `DiaryDayViewModel.kt`, `feature/diary/.../model/DiarySummaryUi.kt` und
> `feature/diary/.../day/DiaryDayScreen.kt`. Skills `android-compose-ui` und
> `android-presentation-mvi` laden.
>
> Umsetzung:
> 1. `CalorieRing` generalisieren: `size: Dp = 160.dp`, `color: Color? = null`
>    (null → primary/error wie bisher), Stroke proportional skalieren.
> 2. Neue `MacroMiniRing`-Komponente (Default 56.dp, Label + Progress + Color).
> 3. `CollapsingSummaryHeader` Expanded-Layout: links `CalorieRing` (~120.dp),
>    rechts 2×2-Grid der vier `MacroMiniRing`s (Daten aus `DiarySummaryUi.macros`).
>    Farben: Protein=primary, Fett=tertiary, Kohlenhydrate=secondary,
>    Ballaststoffe=error. „Verbraucht X / Y kcal" beibehalten. Collapsed-Zustand
>    (kompakte Makro-Balken) unverändert lassen.
> 4. Slots default-expandiert: `DiaryDayState.expandedSlots =
>    MealSlot.entries.toSet()`; `DiaryDayViewModel` setzt beim Datumswechsel
>    ebenfalls alle Slots.
> 5. Tests anpassen: `DiaryDayViewModelTest` (Toggle-Semantik mit neuem Default,
>    Datumswechsel), ggf. `DiaryDayScreenTest`; `CollapsingSummaryHeaderTest`
>    unverändert lassen. Optional kleiner Compose-Test für das 2×2-Grid (Verhalten
>    nach Bedarf; der bestehende Header-Test bleibt die Referenz).
> 6. Verifiziere: `./gradlew test detekt ktlintCheck` (Instrumented-Tests nur
>    wenn ein Gerät/Emulator verfügbar ist, sonst nicht). Keine Commits. Meldung:
>    Änderungsliste + Test-Ergebnis.