# EZ-Makro – Bugfix-/QoL-Plan: Rezept-Zutaten UX (Workpackages für Coding-Agents)

Vier zusammenhängende UX-Probleme in den Rezept-/Zutaten-Flows:

1. **Doppelte Gramm-Anzeige** beim „Zutat hinzufügen": einmal `Pro Portion (…g)`
   und zusätzlich `Anzahl (1 Portion (…g))`.
2. **Kein Entweder/oder** zwischen Rohgewicht und Portions-Anzahl: im
   Zutat-Dialog muss der Nutzer erst das vorbelegte Rohgewicht löschen, im
   Rezept-Editor sind beide Felder gleichzeitig sichtbar und Rohgewicht
   „gewinnt" still.
3. **Rezept-Detail** zeigt `Portionen: x`, aber nicht die Gramm pro Portion.
4. **Rezept-Detail** ist nicht scrollbar; bei langer Zutatenliste sind
   Bearbeiten/Duplizieren/Löschen nicht erreichbar.

## Grundprinzipien (wie `Bugfix_Plan_Workpackages.md`)

- **Nur lesen vor Schreiben.** Zuerst `Glob`/`Grep`/`Read`, dann exakt editieren.
- **Minimaler Diff.** Keine kosmetischen Änderungen.
- **ADR-konform.** Insbesondere ADR-001 (Layer), ADR-002/007 (Datenverträglichkeit).
- **MVI einhalten.** State immutable, Actions deterministisch, Events one-shot.
- **Nach jedem WP:** relevante Modul-Builds + Tests + `detekt` + `ktlintCheck`.
- **Commit erst nach expliziter User-Freigabe** (`Freigabe WP X`).

## Architektur-Entscheidung (verbindlich für alle WPs)

**Entweder/oder wird ausschließlich in der UI/Presentation erzwungen. Die Domain
bleibt tolerant.**

Begründung:
- ADR-001: „Gramm oder Portion" ist eine Eingabe-/UI-Entscheidung. Die
  Domain-Aufgabe (ADR-003) ist die `RawWeight`/`FinalWeight`-Trennung und bleibt
  unberührt.
- `RecipeIngredient` modelliert `rawWeight` **und** `servingCount` bewusst
  unabhängig; `effectiveRawWeight()` (`core/domain/.../RecipeIngredient.kt:32`)
  priorisiert `rawWeight` deterministisch → kein falsches Nährwertergebnis,
  nur eine verwirrende UI.
- Präzedenz im eigenen Code: `AddToDiarySheet` / `EditPortionDialog` bieten
  einen UI-Modus und übergeben *entweder* `amountGrams` *oder* `portionCount`
  (`DiaryEntry.consumedNutrition()` verzweigt).
- Bestehende persistierte Rezepte/Backups können beide Werte tragen. Ein
  Domain-XOR bräuchte Migration und gefährdet `BackupRoundTripTest` ohne
  fachlichen Nutzen.

**Konsequenzen:**
- Kein neues Feld in `RecipeIngredientDraft`, kein neuer Domain-`require`.
- Kein neuer `RecipeEditorAction`/`DiaryRecipeEditAction`-Typ nötig; das
  Umschalten leert das jeweils andere Feld über die **bestehenden** Callbacks.

**Einheitliche Begriffe (überall):**
- Rohgewicht-Feld: `Rohgewicht (g)` (`recipe_editor_ingredient_raw_weight`).
- Portions-Feld: **`Portionen`** (argumentlos).
- Gramm-pro-Portion: **`Pro Portion (X g)`** — immer dieselbe Formulierung/Quelle
  `ingredient_serving_amount_hint` (`core/presentation/res/values/strings.xml:36`)
  bzw. `GramsPerPortionHint` / `gramsPerPortionLabel`.
- Modus-Umschalter: `Gramm` / `Portionen` (bestehende Core-Strings
  `diary_add_mode_grams` / `diary_add_mode_portions`).

---

## WP 1: Gramm-pro-Portion vereinheitlichen + Doppel-Label entfernen (P0)

### Ziele
- Das duplizierte Gramm-Vorkommen im Portions-Label entfernen.
- Gramm pro Portion erscheint nur noch als kanonischer Hinweis
  `Pro Portion (X g)` (Dialog, Detail, Editor-Kopf).
- Feldname für die Portions-Anzahl überall `Portionen`.

### Betroffene Dateien
- `core/presentation/src/main/res/values/strings.xml`
- `core/presentation/src/main/java/com/example/ezmakro/core/presentation/recipe/RecipeIngredientRow.kt`
- `feature/foodpicker/presentation/src/main/java/com/example/ezmakro/feature/foodpicker/presentation/picker/IngredientAmountDialog.kt`
- `feature/recipes/presentation/src/main/java/com/example/ezmakro/feature/recipes/presentation/editor/RecipeEditorScreen.kt`
- `feature/recipes/presentation/src/main/java/com/example/ezmakro/feature/recipes/presentation/detail/RecipeDetailScreen.kt` (nur Hinweistext, siehe auch WP 4)

### Schritte (Agent)
1. `Read` der o. g. Dateien, insbesondere
   `recipe_editor_ingredient_serving_count` (`strings.xml:35`) und
   `ingredient_serving_amount_hint` (`strings.xml:36`).
2. In `core/presentation/.../strings.xml`:
   `recipe_editor_ingredient_serving_count` von `Anzahl (%1$s)` zu
   **`Portionen`** ändern (kein Format-Argument mehr).
   `ingredient_serving_amount_hint` bleibt unverändert die einzige Quelle für
   „Pro Portion (X g)".
3. `RecipeIngredientRow.kt`: Am Portions-`OutlinedTextField` das Label auf
   `stringResource(R.string.recipe_editor_ingredient_serving_count)` ohne
   `servingLabel`-Argument umstellen.
4. `IngredientAmountDialog.kt`: `ServingCountField`-Label ebenfalls auf
   `recipe_editor_ingredient_serving_count` ohne Argument umstellen; den
   Hinweis (`ingredient_serving_amount_hint`, gespeist mit `servingAmount`)
   vor dem Feld beibehalten.
5. `RecipeEditorScreen.kt`: Die Kopfzeile
   `"~ ${gramsPerPortion.formatValue()} g / $label"`
   (`recipe_editor_grams_per_portion`, Zeilen ~181–189) auf die kanonische
   Darstellung umstellen, z. B. via `GramsPerPortionHint(gramsPerPortion, isEstimated = false)`.
   Import: `com.example.ezmakro.core.presentation.diary.GramsPerPortionHint`.
6. `RecipeDetailScreen.kt`: Portionen-Zeile erweitern (siehe WP 4 Schritt 2);
   Work-Aufteilung: WP 1 stellt nur den String/Helper bereit, WP 4 verdrahtet
   die Detail-Anzeige.
7. Prüfen, dass `servingLabel` weiterhin als **Verfügbarkeits-Flag** für die
   Portions-Option dient (nicht aus den Signaturen entfernen).

### Tests
- `./gradlew :core:presentation:test` (falls vorhanden) und `:feature:recipes:presentation:test`.
- `RecipeEditorScreenTest` (`feature/recipes/presentation/src/androidTest/.../RecipeEditorScreenTest.kt`):
  der Test `ingredientRowShowsFoodName` nutzt `servingLabel = null` → unverändert grün.
- Optional: neuer Test, dass bei `servingLabel = null` **kein** `Portionen`-Feld
  erscheint.

### Build/QA
```bash
./gradlew :core:presentation:build :feature:recipes:presentation:build :feature:foodpicker:presentation:build detekt ktlintCheck 2>&1 | tail -40
./gradlew :feature:recipes:presentation:test 2>&1 | tail -40
```

### DoD
- [ ] Kein `Anzahl (… (…g))` mehr vorhanden.
- [ ] Gramm pro Portion nur noch als `Pro Portion (X g)`.
- [ ] Portions-Feld heißt überall `Portionen`.
- [ ] Build/Detekt/Ktlint/Tests grün.

### Commit (nach Freigabe)
`Commit WP 1: Unify grams-per-portion display and remove duplicated ingredient count label`

### User-Abnahme
Diff + Screenshot/Test → `Freigabe WP 1`

---

## WP 2: Entweder/oder im Zutat-Dialog + geteilter Modus-Selektor (P0)

### Ziele
- Im Picker-Dialog `IngredientAmountDialog` nur **ein** Mengenfeld:
  `Gramm` **oder** `Portionen`.
- Portions-Modus nur anbieten, wenn das Item eine Portion kennt
  (`servingLabel != null`).
- Beim Bestätigen genau ein Feld setzen, das andere `""`.
- Den wiederholten privaten `AmountModeSelector` durch **ein** geteiltes
  Composable in `core/presentation` ersetzen (genutzt vom neuen Dialog;
  Refactoring der bestehenden Dialoge optional, siehe Schritt 6).

### Betroffene Dateien
- `feature/foodpicker/presentation/src/main/java/com/example/ezmakro/feature/foodpicker/presentation/picker/IngredientAmountDialog.kt`
- **neu:** `core/presentation/src/main/java/com/example/ezmakro/core/presentation/components/AmountModeSelector.kt` (Paketname an bestehende Core-Struktur anpassen)
- optional: `core/presentation/.../diary/AddToDiarySheet.kt`, `core/presentation/.../diary/EditPortionDialog.kt`

### Schritte (Agent)
1. `Read` `IngredientAmountDialog.kt`, `FoodPickerSheet.kt:164-178`
   (Aufruf), `FoodPickerViewModel.kt:452-480` (`confirmAddAsIngredient`) und
   `FoodPickerState.kt:91-97` (`PendingIngredient`) — Signaturen verstehen:
   `onConfirm(rawWeight, servingCount)`.
2. Neues geteiltes Composable anlegen:
   - `enum class AmountMode { GRAMS, PORTIONS }`
   - `@Composable fun AmountModeSelector(mode: AmountMode, onModeChange: (AmountMode) -> Unit)`
   - Labels aus Core-Strings `diary_add_mode_grams` / `diary_add_mode_portions`,
     Überschrift `diary_add_mode_label`; Radio-Button-Optik wie
     `AddToDiarySheet.AmountModeSelector` (Zeilen 119–148).
3. In `IngredientAmountDialog`:
   - `showModeSelector = servingLabel != null`
   - Startmodus: `PORTIONS`, wenn `showModeSelector && initialServingCount.isNotBlank()`,
     sonst `GRAMS`.
   - Bei `GRAMS`: nur `Rohgewicht (g)`-Feld (Default `initialRawWeight`).
   - Bei `PORTIONS`: Hinweis `ingredient_serving_amount_hint` (mit
     `servingAmount`) + Feld `Portionen` (Default `initialServingCount`, sonst `"1"`).
   - `canConfirm`/Validierung auf das jeweils aktive Feld beziehen.
   - `onConfirm`: im `GRAMS`-Modus `onConfirm(rawWeight, "")`, im `PORTIONS`-Modus
     `onConfirm("", servingCount)`.
4. `FoodPickerViewModel`: unverändert lassen. `confirmAddAsIngredient` speichert
   bereits beide Werte in `SavedStateHandle` und emittiert `AddAsIngredient`.
   Da der Dialog jetzt genau ein Feld liefert, sind die „last used"-Werte
   automatisch exklusiv.
5. `FoodPickerSheet`: Aufruf unverändert (Parameter `servingLabel`/`servingAmount`
   bleiben).
6. Optional (empfohlen, separater kleiner Diff im selben WP): `AddToDiarySheet`
   und `EditPortionDialog` auf das geteilte `AmountModeSelector`/`AmountMode`
   umstellen und ihre privaten Enums/Selektoren entfernen. Wenn das Risiko
   gescheut wird: offen lassen und im Commit-Text als „noch nicht dedupliziert"
   vermerken.

### Tests
- `feature/foodpicker/presentation` hat **keine** Unit-Tests für den Dialog;
  keine Regression in `FoodPickerViewModelTest` (Zeilen ~1090–1160 prüfen
  `pending.servingAmount`, bleibt unverändert).
- Optional: neuer Compose-Test (falls Test-Setup im Modul vorhanden) der bei
  `servingLabel != null` genau ein Feld + Umschalter zeigt.

### Build/QA
```bash
./gradlew :core:presentation:build :feature:foodpicker:presentation:build detekt ktlintCheck 2>&1 | tail -40
./gradlew :feature:foodpicker:presentation:test 2>&1 | tail -40
```

### DoD
- [ ] Dialog bietet nur Gramm **oder** Portionen an.
- [ ] Portions-Option nur bei bekannter Portion.
- [ ] Bestätigung setzt genau ein Feld.
- [ ] Kein doppeltes Gramm-Vorkommen (WP 1 + WP 2 zusammen).
- [ ] Build/Detekt/Ktlint/Tests grün.

### Commit (nach Freigabe)
`Commit WP 2: Make recipe-ingredient amount dialog an explicit grams-or-portions choice`

### User-Abnahme
Diff + manueller Flow (Zutat mit/ohne Portion hinzufügen) → `Freigabe WP 2`

---

## WP 3: Entweder/oder in der Editor-Zutatenzeile (P0)

### Ziele
- In `RecipeIngredientRow` nur noch **ein** Mengenfeld sichtbar.
- Umschalter `Gramm`/`Portionen` als kompakter Segmented-Button, nur wenn
  `servingLabel != null`.
- Umschalten leert das jeweils andere Feld (bestehende Callbacks).
- Gilt automatisch für **beide** Nutzer: Rezept-Editor und
  Tagebuch-Rezept-Bearbeitung (gemeinsame `RecipeIngredientList`).

### Betroffene Dateien
- `core/presentation/src/main/java/com/example/ezmakro/core/presentation/recipe/RecipeIngredientRow.kt`
- ggf. `core/presentation/src/main/java/com/example/ezmakro/core/presentation/recipe/RecipeIngredientList.kt` (nur falls Signatur nötig; Ziel: **keine** neue Parameter)
- Wiederverwendung: `feature/foodpicker/.../picker/myfoods/MyFoodsTab.kt:47-60`
  (Muster `SingleChoiceSegmentedButtonRow`)

### Schritte (Agent)
1. `Read` `RecipeIngredientRow.kt` (komplett), `RecipeIngredientList.kt`,
   `RecipeEditorScreen.kt:195-212`, `DiaryRecipeEditScreen.kt:185-202`.
2. In `RecipeIngredientRow` lokalen UI-State einführen:
   ```kotlin
   val initialUsesServing =
       ingredient.servingCount.isNotBlank() ||
           (ingredient.rawWeight.isBlank() && ingredient.servingLabel != null)
   var usesServing by remember(ingredient.id) { mutableStateOf(initialUsesServing) }
   ```
   (Import `remember`, `mutableStateOf`, `getValue`, `setValue`.)
3. `IngredientInputRow` ersetzen:
   - `servingLabel == null` → nur `Rohgewicht (g)`-Feld (wie bisher).
   - sonst: `SingleChoiceSegmentedButtonRow` mit `SegmentedButton`
     `Gramm` / `Portionen` (Labels aus Core `diary_add_mode_grams/portions`),
     danach **genau ein** Feld:
     - `usesServing == false` → `Rohgewicht (g)`
     - `usesServing == true` → `Portionen`
   - Beim Umschalten:
     - auf `GRAMS`: `usesServing = false; onServingCountChange("")`
     - auf `PORTIONS`: `usesServing = true; onRawWeightChange("")`
   - Optional im Portions-Modus: `ingredient_serving_amount_hint` nur wenn
     Gramm pro Portion bekannt ist. Da der Draft die Gramm nicht direkt trägt,
     **nicht** einführen (kein neues Plumping in diesem WP); der kanonische
     Hinweis bleibt Dialog/Detail/Kopf (WP 1/2/4).
4. `IngredientMacroDetail` (`RecipeIngredientRow.kt:164-199`) auf einen Zweig
   reduzieren: Da nur noch ein Feld gesetzt ist, reicht
   `rawWeight != null -> "… g"` / `servingCount != null -> "… Portionen"`.
   Die `usesGenericServingLabel`-Sonderfälle können vereinfacht werden, aber
   nur wenn dabei bestehende Ausgaben nicht fachlich falsch werden.
5. Prüfen, dass `key(ingredient.id)` in `RecipeIngredientList.kt` erhalten
   bleibt (lokaler State pro Zutat, kein Bleed beim Entfernen).
6. Keine Änderungen an `RecipeEditorViewModel`, `DiaryRecipeEditViewModel`,
   Actions oder `RecipeIngredientDraft` (Domain bleibt tolerant).

### Tests
- `feature/recipes/presentation/src/androidTest/.../RecipeEditorScreenTest.kt`
  erweitern: Zeile mit `servingLabel != null` zeigt genau ein Feld + Umschalter;
  Klick auf den anderen Modus ruft den Clear-Callback.
- `RecipeEditorViewModelTest` und `DiaryRecipeEditViewModelTest` bleiben grün
  (Actions unverändert).
- `DiaryRecipeEditScreenTest` (androidTest) prüfen.

### Build/QA
```bash
./gradlew :core:presentation:build :feature:recipes:presentation:build :feature:diary:presentation:build detekt ktlintCheck 2>&1 | tail -40
./gradlew :feature:recipes:presentation:test :feature:diary:presentation:test 2>&1 | tail -60
```

### DoD
- [ ] Editor-Zeile zeigt nur Gramm **oder** Portionen.
- [ ] Portions-Umschalter nur bei bekannter Portion.
- [ ] Umschalten leert das andere Feld persistent (kein stiller Vorrang).
- [ ] Rezept-Editor und Tagebuch-Bearbeitung konsistent.
- [ ] Build/Detekt/Ktlint/Tests grün.

### Commit (nach Freigabe)
`Commit WP 3: Enforce grams-or-portions choice in recipe ingredient editor rows`

### User-Abnahme
Diff + manueller Editor-Flow (mit/ohne Portion, umschalten) → `Freigabe WP 3`

---

## WP 4: Rezept-Detail scrollbar + Gramm pro Portion (P1)

### Ziele
- Rezept-Detail bei langer Zutatenliste scrollbar; Buttons bleiben erreichbar.
- Oben zusätzlich Gramm pro Portion in kanonischer Formulierung.

### Betroffene Dateien
- `feature/recipes/presentation/src/main/java/com/example/ezmakro/feature/recipes/presentation/detail/RecipeDetailScreen.kt`

### Schritte (Agent)
1. `Read` `RecipeDetailScreen.kt` (Zeilen 161–225) und
   `feature/recipes/presentation/model/RecipeUi.kt` (`gramsPerPortion`,
   `isEstimated`).
2. Portionen-Zeile ergänzen (Zeile ~175):
   - bei `recipe.gramsPerPortion != null` zusätzlich
     `GramsPerPortionHint(recipe.gramsPerPortion, recipe.isEstimated)` rendern
     oder äquivalent
     `Text(stringResource(CoreR.string.ingredient_serving_amount_hint, gramsPerPortionLabel(recipe.gramsPerPortion, recipe.isEstimated)))`.
   - bei `null`: keine zusätzliche Zeile (Hinweis für fehlendes Endgewicht
     existiert bereits weiter unten).
3. Content-`Column` (Zeile ~169) um
   `Modifier.verticalScroll(rememberScrollState())` erweitern.
   Imports: `androidx.compose.foundation.rememberScrollState`,
   `androidx.compose.foundation.verticalScroll`.
4. `RecipeDetailRoot`/Dialoge/Sheet unverändert lassen (AddToDiarySheet ist ein
   Dialog und wird nicht von der Column gescrollt).
5. Der frühe `return@Scaffold` bei `recipe == null` bleibt.

### Tests
- `RecipeDetailScreenTest.kt` (`androidTest`) erweitern/anpassen:
  - Test, dass bei gesetztem `gramsPerPortion` `Pro Portion (…g)` erscheint.
  - Bestehende Tests (`showsComputedNutrition`, `showsHint_whenNoFinalWeight`,
    `clickingDuplicate_triggersAction`, Add-Sheet-Tests) müssen grün bleiben.
    Bei Bedarf vor Klick `performScrollTo()` ergänzen, falls ein Button durch
    das Scrollen außerhalb des sichtbaren Bereichs liegt.
- `recipeUi(...)`-Helper um `gramsPerPortion`/`isEstimated` erweitern.

### Build/QA
```bash
./gradlew :feature:recipes:presentation:build detekt ktlintCheck 2>&1 | tail -40
./gradlew :feature:recipes:presentation:test 2>&1 | tail -40
./gradlew :feature:recipes:presentation:connectedDebugAndroidTest 2>&1 | tail -60   # falls Emulator/Gerät
```

### DoD
- [ ] Detail-Seite scrollbar; Buttons bei langer Zutatenliste erreichbar.
- [ ] `Portionen: x` + `Pro Portion (X g)` oben konsistent.
- [ ] Bestehende Detail-Tests grün.
- [ ] Build/Detekt/Ktlint grün.

### Commit (nach Freigabe)
`Commit WP 4: Make recipe detail scrollable and show grams per portion`

### User-Abnahme
Diff + Screenshot/Flow mit langer Zutatenliste → `Freigabe WP 4`

---

## Abhängigkeiten & Reihenfolge

- WP 1 zuerst (Strings/Helper = gemeinsame Basis für alle anderen).
- WP 2 und WP 3 sind unabhängig voneinander, aber beide brauchen WP 1.
- WP 4 nutzt den kanonischen Hinweis aus WP 1.

`WP 1 → WP 2 → WP 3 → WP 4`

## Arbeitsanweisung für Coding-Agents (pro WP)

1. **Lokalisieren**: `Glob/Grep/Read` (nie raten). Liste betroffener Dateien bestätigen.
2. **Implementieren**: minimaler Diff, bestehende Contracts nicht brechen.
3. **Testen lokal**: relevante Modultests laut WP.
4. **Qualitätscheck**: `./gradlew build detekt ktlintCheck` (Module + Gesamt).
5. **Vor-Abnahme**: Diff, Testoutput und DoD-Checklist kurz zusammenfassen.
6. **Freigabe einholen**: **NICHT commiten.** User muss explizit `Freigabe WP X` geben.
7. **Commit erst nach Freigabe**: exaktes Commit-Message aus dem WP nutzen.

## Gesamt-DoD

- [ ] WP 1–4 abgearbeitet.
- [ ] `./gradlew build detekt ktlintCheck` grün.
- [ ] Relevante Unit-/Compose-Tests grün (keine Regression).
- [ ] ADR-konform; Domain und persistierte Modelle unverändert.
- [ ] Working Tree sauber; ein Commit pro WP nach jeweiliger Freigabe.

## Offene Punkte / Risiken

- **Segmented-Button-Verfügbarkeit**: `SingleChoiceSegmentedButtonRow` wird
  bereits in `MyFoodsTab.kt` verwendet → im Recipes-Modul verfügbar. Falls
  Material3-Version im Modul abweicht, Fallback: `FilterChip`-Paar.
- **Legacy-Drafts mit beiden Werten**: Beim Laden einer bestehenden Zutat mit
  gesetztem Rohgewicht wird `usesServing = false`; der alte `servingCount`
  bleibt im Draft, bis der Nutzer auf `Portionen` umschaltet (dann wird
  `rawWeight` geleert) — kein stiller Vorrang in der UI. Wenn gewünscht, kann
  WP 3 in `RecipeEditorViewModel.loadExisting` zusätzlich normalisieren
  (Rohgewicht behalten, `servingCount = ""`); dies ist **optional** und dann im
  selben WP umzusetzen + testen.
- **Dedup des Modus-Selektors**: `AmountModeSelector` existiert 3× privat.
  WP 2 extrahiert eine geteilte Variante; das Refactoring der zwei bestehenden
  Dialoge ist optional und darf in einen Folge-WP wandern.
