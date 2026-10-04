# Plan — Rezept-Eintrag im Tagebuch bearbeiten

**Status:** Planung abgeschlossen, Freigabe für A1/A3/A4 durch den Nutzer.
**Stand:** 2026-10-04 (auf Basis commit `2824244`).
**Bezug:** `docs/domain-model.md`, `docs/architecture.md`, `docs/coding-style.md`,
`docs/Bugfix_Plan_Stale_Snapshots.md` (P4 abgeschlossen — die dort dokumentierte
Fehlerklasse „veraltete Snapshots im State" darf nicht neu erzeugt werden),
`docs/definition-of-done.md`.

Dieser Plan ist in sechs Arbeitspakete (WP1–WP6) zerlegt. **Jedes WP ist ein
eigenständig prüfbares, bearbeitbares und committbares Paket.** Reihenfolge
einhalten: WP2 baut auf WP1 auf, WP4 auf WP3, WP5 auf WP4, WP6 auf WP5.

---

## 1. Kontext und Befund

### 1.1 Was heute schon existiert

| Fähigkeit | Ort |
|---|---|
| Rezept ins Tagebuch hinzufügen | `core/domain/.../usecase/diary/AddToDiaryUseCases.kt:51` (`AddRecipeToDiaryUseCase`), verdrahtet in `app/.../MainActivity.kt:193` (aus dem Picker) und `:259` (aus dem Rezeptdetail) |
| Menge/Portionen eines Eintrags ändern | `core/presentation/.../diary/EditPortionDialog.kt` + `core/domain/.../usecase/diary/UpdateDiaryEntryUseCase.kt` |
| Rezept-Inhalte ändern (Rezept-Editor) | `feature/recipes/presentation/.../editor/RecipeEditorViewModel.kt`, `RecipeEditorScreen.kt` |

### 1.2 Der Schlüsselbefund

`diary_entries.recipe_snapshot` (`core/database/.../entity/DiaryEntryEntity.kt:63`)
serialisiert das **komplette `Recipe`-Objekt einschließlich `ingredients`**
(`core/data/.../diary/mapper/DiaryEntryEntityMapper.kt:34` →
`JsonConverters.encodeRecipe`, `core/database/.../converter/JsonConverters.kt:46`).
Jede `RecipeIngredient` trägt wiederum einen vollen `FoodItem`-Snapshot
(`core/domain/.../model/RecipeIngredient.kt:6-11`).

**Daraus folgt:** Eine Anpassung, die nur diesen Tag betrifft, braucht **keine
neue Datenspalte für die Zutaten** — `entry.copy(recipe = geändertesRezept)`
genügt. Eine zusätzliche Spalte ist nur für die *sichtbare Markierung*
(Nicht-Ziel-Kriterium 4.3) nötig.

`RecipeDao.upsertRecipeWithIngredients` (`core/database/.../dao/RecipeDao.kt:39`)
ersetzt Rezept + Zutatenzeilen in einer Room-Transaktion, ist also für die
dauerhafte Variante bereits passend.

### 1.3 Was fehlt

Zutaten eines geloggten Rezepts ändern (Rohgewicht/Portions, Zutat gegen eine
andere Marke tauschen, Zutat entfernen/hinzufügen) und die Wahl zwischen
„gilt nur für diesen Tag" und „ändert das Rezept dauerhaft".

---

## 2. Festgelegte Entscheidungen

Diese Entscheidungen sind vom Nutzer bestätigt und in den WPs **nicht**
neu zu verhandeln.

| # | Entscheidung |
|---|---|
| **A1** | Gemeinsame Editor-Bausteine werden nach `core:domain` bzw. `core:presentation` extrahiert und von Rezept-Editor und Tagebuch-Editor gemeinsam genutzt. Grundlage: `android-module-structure` — „Features **never** depend on each other" und „code lives in a feature module unless it is needed by more than one feature". Deckt die Vorgabe „Rezept-Editor-Erfahrung für den Nutzer immer gleich" ab. |
| **A2** | Eigener Vollbild-Screen `DiaryRecipeEditScreen` im Diary-Modul, Route `diary_recipe_edit/{entryId}`, registriert in `DiaryRoutes.kt`. Navigation bleibt im Feature — **kein Eingriff in `:app`/`MainActivity`**. |
| **A3** | `DiaryEntry` erhält `isRecipeOverridden: Boolean = false` (Default, damit alle bestehenden Konstruktionen kompilieren), persistiert als Spalte `diary_entries.recipe_overridden` (Migration 8→9). |
| **A4** | Schreibreihenfolge in `EditDiaryRecipeEntryUseCase`: **erst Rezept, dann Tagebuch**. Ein Tagebuch-Eintrag ist ein unveränderliches historisches Dokument — ein Eintrag mit altem Snapshot nach einem Rezept-Update ist korrekt; ein Eintrag, der eine dauerhaft gewollte Änderung zeigt, die im Rezept fehlt, wäre Datenverlust. Scheitert der Rezept-Write, wird das Tagebuch **nicht** angefasst und der Fehler propagiert. |
| **A5** | Beim Rezept-Update wird vom **Live-Rezept** ausgegangen (`live.copy(ingredients, portions, finalWeight, lastModifiedAt = now)`), nicht vom Diary-Snapshot. Sonst würden `category`, `isFavorite` und `lastUsedAt` aus dem Snapshot ins Rezept zurückgeschrieben und überschrieben. |
| **A6** | Kein Sub-Rezept als Zutat im Tagebuch-Override. Der `FoodPickerSheet` liefert `isRecipe = true`; das wird mit einer Meldung quittiert und nicht angewendet. |
| **A7** | `encodeRouteParam`/`decodeRouteParam` wandern nach `core/presentation/.../util/RouteParams.kt` (aktuell in `feature/food/.../navigation/FoodRoutes.kt:69`), damit Diary und Food eine gemeinsame Quelle haben. |
| **A8** | `RecipeRepository.markUsed` wird beim Bearbeiten **nicht** gestempelt. Der Add-Pfad stempelt bereits — `FoodPickerViewModel.kt:221`, `:333`, `:346` rufen `recipeRepository.markUsed(...)`; `AddRecipeToDiaryUseCase` selbst tut es nicht. Bearbeiten ist keine neue Verwendung, sonst rutscht das Rezept in `observeRecent` nach oben, obwohl es nicht gekocht wurde. |

---

## 3. Zielbild

### 3.1 Tagebuch-Zeile (Rezept-Eintrag)

```
Haferflocken-Müsli                    [Tagesanpassung]
420 kcal · P 18 g · KH 55 g · F 9 g · B 8 g
1 Portion
                              [Bearbeiten]  [Zutaten]
                              [Löschen]
```

- **„Zutaten"** → `DiaryRecipeEditScreen`
- **„Bearbeiten"** → bestehender `EditPortionDialog` (Menge/Portionen, schneller Pfad)
- **„Zutaten"-Button und Badge nur bei `isRecipe == true`**
- Badge **„Tagesanpassung"** wenn `isRecipeOverridden`
- Badge **„Rezept geändert"** wenn das Live-Rezept inzwischen einen neueren `lastModifiedAt` als der Snapshot hat

### 3.2 `DiaryRecipeEditScreen`

1. **Kopfzeile** — Rezeptname, Portionen, „ins Tagebuch: X Portionen / Y g" sowie
   eine Vergleichszeile `vorher → nachher` (kcal/P/KH/F/B). Die Auswirkung auf den
   Tag ist dadurch unmittelbar sichtbar.
2. **Hinweiszeile** bei inzwischen geändertem Rezept und bei gelöschtem Quellrezept.
3. **Summenzeile** der Zutatenernährung — identische Komponente wie im Rezept-Editor (A1).
4. **Zutatenliste** — pro Zeile: Name, Rohgewicht-Feld, Anzahl-Feld (wenn
   `servingLabel != null`), kcal, aufklappbar alle Makros. Pro Zeile: „Ersetzen"
   (andere Marke) und „Entfernen". Darunter „Zutat hinzufügen".
5. **„Erweitert"** (ausklappbar) — Portionen, finales Endgewicht.
6. **Menge im Tagebuch** — Umschalter Portionen/Gramm wie im Rezeptdetail.
7. **Speichern** → Dialog „Änderung übernehmen" mit zwei Optionen:
   - **„Nur für diesen Tag"** — Zutaten im Rezept bleiben unverändert.
   - **„Rezept dauerhaft ändern"** — gilt für alle folgenden Einträge; frühere
     Tage behalten ihre Werte. Deaktiviert, wenn das Quellrezept gelöscht wurde.

Name, Kategorie und Favorit sind **nicht** editierbar — Identität und Metadaten
des Rezepts bleiben der Rezept-Editor vorbehalten.

---

## 4. WP-Übersicht

| WP | Thema | Hängt ab von | Art |
|---|---|---|---|
| **WP1** | Zutaten-Draft + Auflösung + Snapshot-Fabrik nach `core:domain` | — | Refactor (Move) |
| **WP2** | Editor-Bausteine (`IngredientRow`, Nährwertsumme, Zutatenliste, Parser, RouteParams) nach `core:presentation` | WP1 | Refactor (Move) |
| **WP3** | `DiaryEntry.isRecipeOverridden` + Spalte + Migration 8→9 | — | Feature (Daten) |
| **WP4** | `EditDiaryRecipeEntryUseCase` | WP3 | Feature (Domain) |
| **WP5** | `DiaryRecipeEditScreen` + Route + Anbindung an die Tagebuch-Zeile | WP1, WP2, WP4 | Feature (Presentation) |
| **WP6** | Badges „Tagesanpassung" / „Rezept geändert" | WP5 | Feature (Presentation) |

**Arbeitsweise je WP:** Zuweisung an Agent → Agent implementiert → Agent meldet
Ergebnis und Testergebnis → Nutzer prüft (`git diff`) → Nutzer gibt Commit
frei → Commit. Erst danach das nächste WP.

---

## WP1 — Zutaten-Draft und Auflösung nach `core:domain`

**Ziel:** Die fachlich reine Rezept-Draft-Logik verlässt den ViewModel und wird
in `core:domain` verfügbar gemacht, damit zwei Features sie nutzen können (A1).
Reine Verschiebung — das Verhalten des Rezept-Editors bleibt identisch.

**Abhängigkeit:** keine.

### Zu lesen

- `feature/recipes/presentation/.../editor/RecipeEditorViewModel.kt` — insbesondere
  `:210-227` (`addRecipeIngredient`), `:239-284` (`loadExisting` → Draft-Bau),
  `:286-307` (`recomputeNutrition`), `:309-322` (`checkSnapshotChanges`),
  `:435-455` (`buildIngredients`), `:457-476` (`resolveRecipeIngredient`),
  `:478-496` (`resolveFoodIngredient`), `:498-517` (`createSnapshot`)
- `feature/recipes/presentation/.../editor/RecipeEditorState.kt:8-19` (`IngredientDraft`)
- `feature/recipes/presentation/.../editor/RecipeEditorParsers.kt` (ganz)
- `core/domain/.../calculation/RecipeCalculation.kt` (Namensraum für die Snapshot-Fabrik)
- `core/domain/.../model/RecipeIngredient.kt`, `Recipe.kt`, `RecipeSnapshot.kt`, `FoodItem.kt`, `Weight.kt`
- `core/domain/.../result/Result.kt` (Projekt-Result-Wrapper)

### Festgelegte Vorgaben

- **Keine Verhaltensänderung.** Jede verschobene Funktion wird 1:1 übernommen.
  Änderungen an Fehlermeldungen, Validierung oder Reihenfolge sind nicht Teil
  dieses WPs.
- `internal` wird zu `public`, weil die Symbole jetzt modulübergreifend sind.
- Die Nutzlast bleibt `String` (untypisierter Formularzustand); die Use Case
  liefert typisierte `RecipeIngredient`. Genau diese Grenze setzt ADR-003 durch.

### Schritte

1. **Parser verschieben.** `RecipeEditorParsers.kt` →
   `core/domain/src/main/java/com/example/ezmakro/core/domain/util/NumberParsing.kt`.
   Inhalt unverändert übernehmen (`parsePositiveInt`, `parsePositiveDouble`,
   `parseNonNegativeDouble`), `internal` entfernen. `core:domain` braucht dafür
   keine neue Abhängigkeit (reines Kotlin, JUnit5 + AssertK kommen bereits über
   `DomainModuleConventionPlugin`).
2. **Draft-Modell verschieben.** `IngredientDraft` →
   `core/domain/.../model/RecipeIngredientDraft.kt`, umbenannt in
   `RecipeIngredientDraft` (der generische Name `IngredientDraft` ist in einem
   geteilten Modul zu unspezifisch). Felder und `id`-Default `UUID.randomUUID()`
   bleiben identisch.
3. **Snapshot-Fabrik verschieben.** `createSnapshot` aus
   `RecipeEditorViewModel.kt:498-517` → `core/domain/.../calculation/RecipeSnapshotFactory.kt`
   als `fun createRecipeSnapshot(recipe: Recipe): RecipeSnapshot`. Doc-Kommentar
   mitnehmen.
4. **Snapshot-Vergleich verschieben.** Die Logik aus `:309-322` →
   `fun List<RecipeIngredientDraft>.markChangedRecipeSnapshots(recipes: List<Recipe>): List<RecipeIngredientDraft>`
   in `core/domain/.../model/RecipeIngredientDraft.kt`.
5. **Auflösungs-Use Case bauen.** Neu
   `core/domain/.../usecase/recipe/BuildRecipeFromDraftsUseCase.kt`:
   ```kotlin
   class BuildRecipeFromDraftsUseCase {
       operator fun invoke(
           drafts: List<RecipeIngredientDraft>,
           foods: List<FoodItem>,
           recipes: List<Recipe>,
       ): Result<List<RecipeIngredient>, RecipeDraftError>
   }

   sealed interface RecipeDraftError {
       /** Weder Rohgewicht noch Anzahl gesetzt (bzw. beides 0). */
       data object MissingAmount : RecipeDraftError

       /** Food-Id nicht in `foods` — z. B. wurde das Lebensmittel gelöscht. */
       data object UnknownFood : RecipeDraftError

       /** Sub-Rezept fehlt und es liegt kein älterer Snapshot im Draft. */
       data object MissingSnapshot : RecipeDraftError
   }
   ```
   Die Reihenfolge der Fälle aus `resolveFoodIngredient`/`resolveRecipeIngredient`
   beibehalten: erst `rawWeight`/`servingCount` auf > 0 prüfen, dann
   `servingUnit`-Bedingung, dann Lookup. Der Fallback auf den Draft-Snapshot bei
   gelöschtem Sub-Rezept (P2.2, `RecipeEditorViewModel.kt:467-469`) **muss**
   erhalten bleiben.
6. **ViewModel umstellen.** `RecipeEditorViewModel` importiert die neuen
   Symbole; `buildIngredients`, `resolveFoodIngredient`, `resolveRecipeIngredient`
   und `createSnapshot` werden durch `buildRecipeFromDraftsUseCase` bzw.
   `createRecipeSnapshot` ersetzt. `checkSnapshotChanges` ruft
   `markChangedRecipeSnapshots`. `addIngredient`/`addRecipeIngredient`/
   `loadExisting` erzeugen `RecipeIngredientDraft`.
   `recomputeNutrition` bleibt im ViewModel (er ist ViewModel-Zustand), nutzt aber
   `createRecipeSnapshot` aus der Domain.
7. **Koin ergänzen.** `factory { BuildRecipeFromDraftsUseCase() }` in
   `feature/recipes/presentation/.../di/RecipePresentationModule.kt` (aktuell
   `:18-37`) und als weiterer Konstruktor-Parameter in `RecipeEditorViewModel`
   aufnehmen. Das Modul nutzt **benannte** Argumente (`:23-28`), daher genügt ein
   zusätzliches `buildRecipeFromDraftsUseCase = get(),` — die Reihenfolge der
   bestehenden Parameter bleibt unberührt. Der `savedStateHandle` steht in
   `RecipeEditorViewModel.kt:40-44` an letzter Position und wird von Koin
   automatisch geliefert; keine Default-Werte im Konstruktor ergänzen.
8. **Aufräumen.** `RecipeEditorState.kt` enthält `IngredientDraft` nicht mehr;
   `RecipeEditorParsers.kt` wird gelöscht.

### Dateien

**Neu**
- `core/domain/.../util/NumberParsing.kt`
- `core/domain/.../model/RecipeIngredientDraft.kt`
- `core/domain/.../calculation/RecipeSnapshotFactory.kt`
- `core/domain/.../usecase/recipe/BuildRecipeFromDraftsUseCase.kt`
- `core/domain/src/test/.../usecase/recipe/BuildRecipeFromDraftsUseCaseTest.kt`
- `core/domain/src/test/.../calculation/RecipeSnapshotFactoryTest.kt`

**Geändert**
- `feature/recipes/presentation/.../editor/RecipeEditorViewModel.kt`
- `feature/recipes/presentation/.../editor/RecipeEditorState.kt`
- `feature/recipes/presentation/.../di/RecipePresentationModule.kt`

**Gelöscht**
- `feature/recipes/presentation/.../editor/RecipeEditorParsers.kt`

### Tests

`BuildRecipeFromDraftsUseCaseTest` (JUnit5 + AssertK, ohne Turbine — die
bestehenden Domain-Tests in `core/domain/src/test/.../usecase/diary/` nutzen
ebenfalls handgeschriebene Fakes):

- reines Rohgewicht → `RecipeIngredient` mit `rawWeight`, `servingCount == null`
- Anzahl + `servingUnit` → `servingCount` gesetzt, `rawWeight == null`
- Rohgewicht **und** Anzahl gesetzt → `rawWeight` gewinnt (wie im Original)
- unbekannte `foodId` → `RecipeDraftError.UnknownFood`
- Menge 0 bzw. leer → `MissingAmount`
- `servingCount` ohne `rawWeight` und ohne `servingUnit` → `MissingAmount`
- Sub-Rezept vorhanden → `recipeSnapshot` via `createRecipeSnapshot`, `recipeId` gesetzt
- **Sub-Rezept gelöscht, Draft trägt alten Snapshot** → Snapshot wird behalten (P2.2-Regression)
- **Sub-Rezept gelöscht, Draft ohne Snapshot** → `MissingSnapshot`
- gemischte Liste: ein fehlerhafter Draft → Ergebnis `Error`, keine Teil-Liste

`RecipeSnapshotFactoryTest`:

- mit `finalWeight` → `nutritionPer100g = total / endgewicht`, `gramsPerPortion = endgewicht / portions`
- ohne `finalWeight` → Fallback auf `totalRawWeight` (`nutritionPer100g` geschätzt, `gramsPerPortion` aus Rohgewicht)
- Rohgewicht 0 → `NutritionValues.ZERO`, kein `NaN` (P1.1-Regression)
- `originalLastModifiedAt` wird durchgereicht

`RecipeEditorViewModelTest` (bestehend) muss **ohne Anpassung der Testlogik**
weiterhin grün sein — das ist der Nachweis, dass WP1 ein reiner Move war. Nur
Fakes/Anpassungen zulassen, falls sich eine Signatur geändert hat.

### Verifikation

```
./gradlew :core:domain:test :feature:recipes:presentation:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] Kein Verhaltensunterschied im Rezept-Editor; `RecipeEditorViewModelTest` unverändert grün
- [ ] `RecipeEditorParsers.kt` existiert nicht mehr; keine Parser-Duplikate im Repo
- [ ] `core:domain` hat keine Android-Imports in den neuen Dateien
- [ ] `BuildRecipeFromDraftsUseCase` liefert `Result<List<RecipeIngredient>, RecipeDraftError>` und keine Exceptions im Fehlerfall
- [ ] Die Fehlermeldung „Zutat braucht Rohgewicht oder Anzahl" wird im Rezept-Editor weiterhin angezeigt (`RecipeEditorState.ingredientError` → `recipe_editor_error_ingredient_weight`)

**Commit-Vorschlag:** `refactor: Draft-Auflösung, Snapshot-Fabrik und Parser nach core:domain verschoben`

---

## WP2 — Editor-Bausteine nach `core:presentation`

**Ziel:** Zutaten-Zeile, Nährwertsumme, Zutatenliste und Route-Helper werden
geteilt. Danach sind alle UI-Bausteine des Rezept-Editors an einem Ort, der auch
vom Diary-Modus genutzt werden kann. Reine Verschiebung.

**Abhängigkeit:** WP1.

### Zu lesen

- `feature/recipes/presentation/.../editor/IngredientRow.kt` (ganz)
- `feature/recipes/presentation/.../editor/RecipeEditorScreen.kt:148-227`
  (`EditorForm`, `TotalNutritionSummary`)
- `feature/food/presentation/.../navigation/FoodRoutes.kt:62-137` (Kdoc,
  `encodeRouteParam` `:69`, `decodeRouteParam` `:93`, private Helfer/Konstanten
  `:120-137`)
- `core/presentation/.../util/Formatting.kt` (`formatValue`)
- `core/presentation/src/main/res/values/strings.xml`
- `feature/recipes/presentation/src/main/res/values/strings.xml`
- `app/.../MainActivity.kt:44` (Import von `encodeRouteParam`)

### Festgelegte Vorgaben

- **Keine Verhaltens- oder Darstellungsänderung.** Layout, Strings, Schriftgrößen,
  `contentDescription`-Verhalten und das `✕`-Entfernen bleiben identisch, damit die
  Rezept-Editor-Erfahrung unverändert bleibt (A1).
- `ComposeConventionPlugin` liefert `androidx-compose-material3` bereits an
  `core:presentation` — **keine Änderung an `core/presentation/build.gradle.kts`**
  nötig. Sollte sich das doch als nötig erweisen, im WP melden, nicht stillschweigend
  hinzufügen.
- Ressourcen, die von den verschobenen Komponenten genutzt werden, wandern mit in
  `core/presentation/src/main/res/values/strings.xml` mit `recipe_*`-Präfix
  unverändert erhalten. Zu verschieben sind **neun** Strings:
  `recipe_editor_ingredient_raw_weight`, `recipe_editor_ingredient_serving_count`,
  `recipe_editor_recipe_snapshot_changed`, `recipe_editor_add_ingredient`,
  `recipe_editor_portions`, `recipe_editor_final_weight`,
  `recipe_editor_final_weight_hint`, `recipe_editor_save`, `recipe_detail_total`.
  Die ersten vier werden von den verschobenen Komponenten selbst referenziert,
  die letzten fünf sind die Label-Menge, die der Diary-Editor in WP5 wiederverwendet
  (A1 — keine doppelten Strings).

### Schritte

1. **RouteParams verschieben.** `feature/food/.../navigation/FoodRoutes.kt` →
   `core/presentation/.../util/RouteParams.kt`. `encodeRouteParam`, `decodeRouteParam`
   und die byteweisen Hilfsfunktionen 1:1 mitnehmen. In `FoodRoutes.kt` und
   `MainActivity.kt` nur den Import anpassen.
2. **IngredientRow verschieben.** `feature/recipes/.../editor/IngredientRow.kt` →
   `core/presentation/.../recipe/RecipeIngredientRow.kt`. Umbenannt, weil der
   generische Name nun modulübergreifend ist. Parameter `ingredient: RecipeIngredientDraft`.
   `R.string`-Referenzen auf `core:presentation` umstellen.
3. **Nährwertsumme verschieben.** `TotalNutritionSummary`
   (`RecipeEditorScreen.kt:215-227`) → `core/presentation/.../recipe/RecipeNutritionTotal.kt`
   als `@Composable fun RecipeNutritionTotal(totalNutrition: NutritionValues)`.
   Der Label-String `recipe_detail_total` bleibt im Recipes-Modul — der Summen-
   Komponente wird der Label als **Parameter** übergeben, damit `core:presentation`
   keine Recipes-Ressource referenziert.
4. **Zutatenliste + Ergänzen-Button verschieben.** Der Block aus
   `RecipeEditorScreen.kt:184-201` → `core/presentation/.../recipe/RecipeIngredientList.kt`:
   ```kotlin
   @Composable
   fun RecipeIngredientList(
       ingredients: List<RecipeIngredientDraft>,
       ingredientNutrition: List<NutritionValues>,
       expandedIngredientIndex: Int?,
       onAddIngredient: () -> Unit,
       onRawWeightChange: (Int, String) -> Unit,
       onServingCountChange: (Int, String) -> Unit,
       onRemoveIngredient: (Int) -> Unit,
       onIngredientClick: (Int) -> Unit,
       addIngredientLabel: String,
       ingredientError: UiText?,
       modifier: Modifier = Modifier,
   )
   ```
   Der LazyColumn-Key bleibt `ingredient.id` (P3.3-Regression: keine Index-Keys).
5. **Rezept-Editor umstellen.** `RecipeEditorScreen.kt` importiert die neuen
   Komponenten; `EditorForm` wird schlanker, die privaten Funktionen
   `TotalNutritionSummary` entfallen.
6. **Ressourcen verschieben.** Die neun Strings aus Schritt „Festgelegte
   Vorgaben" nach `core/presentation/.../strings.xml` umziehen. Danach ist die
   `R`-Qualifizierung in genau diesen Dateien zu korrigieren:
   - `RecipeIngredientRow.kt` (neu in `core:presentation`) nutzt danach
     ausschließlich `com.example.ezmakro.core.presentation.R` — alle drei von ihr
     referenzierten Strings sind verschoben.
   - `RecipeEditorScreen.kt` hat 11 `R.string.*`-Referenzen (`:115-283`). Nach dem
     Umzug bleiben fünf im Recipes-Modul — `recipe_editor_title_edit` `:115`,
     `recipe_editor_title_new` `:116`, `recipe_editor_grams_per_portion` `:174`,
     `recipe_editor_name` `:237`, `recipe_editor_category` `:253` — und sechs
     kommen aus `core:presentation`: `recipe_editor_add_ingredient` `:186`,
     `recipe_editor_save` `:208`, `recipe_detail_total` `:216`,
     `recipe_editor_portions` `:267`, `recipe_editor_final_weight` `:282`,
     `recipe_editor_final_weight_hint` `:283`. Dafür ist ein Import-Alias nötig,
     z. B. `import com.example.ezmakro.core.presentation.R as CoreR`.
   - `RecipeDetailScreen.kt` hat 20 `R.string.recipe_detail_*`-Referenzen, davon
     eine die verschobene `recipe_detail_total` (`:175`, in `RecipeMacroSummary`)
     — dort ebenfalls ein Alias, z. B. `CoreR`.
   - `RecipeEditorViewModel.kt` braucht **keine** Änderung: `IngredientRow`,
     `EditorForm` und `TotalNutritionSummary` werden ausschließlich in
     `RecipeEditorScreen.kt` verwendet, der ViewModel referenziert nur
     `recipe_editor_error_*` (`:376`, `:407`, `:417`, `:448`), von denen keines
     verschoben wird.
7. **Backlog notieren.** In `docs/Bugfix_Plan.md` Abschnitt 3 eine Zeile
   ergänzen: bestehende Regelverletzung `:feature:diary → :feature:foodpicker`
   (`feature/diary/presentation/build.gradle.kts`) als bewusste Abweichung von der
   Skill-Regel „Features never depend on each other", nicht als Bug.

### Dateien

**Neu**
- `core/presentation/.../util/RouteParams.kt`
- `core/presentation/.../recipe/RecipeIngredientRow.kt`
- `core/presentation/.../recipe/RecipeNutritionTotal.kt`
- `core/presentation/.../recipe/RecipeIngredientList.kt`

**Geändert**
- `feature/recipes/presentation/.../editor/RecipeEditorScreen.kt`
- `feature/recipes/presentation/.../detail/RecipeDetailScreen.kt` (nur `R`-Alias für `recipe_detail_total`)
- `feature/recipes/presentation/.../navigation/FoodRoutes.kt`
- `app/.../MainActivity.kt` (nur Import)
- `core/presentation/src/main/res/values/strings.xml` (neun neue Strings)
- `feature/recipes/presentation/src/main/res/values/strings.xml` (dieselben neuen entfernt)
- `docs/Bugfix_Plan.md`

**Gelöscht**
- `feature/recipes/presentation/.../editor/IngredientRow.kt`

### Tests

- `core/presentation` hat eine Unit-Test-Quelle;
  `core/presentation/src/test/.../util/FormattingTest.kt` existiert bereits.
  Für `RouteParams` **neu**: `RouteParamsTest` mit Rundlauf `encode → decode`
  für `abc`, `a/b`, `a?b`, `a b`, Umlaute, Emoji (Mehrbyte), leerer String.
- Bestehende `RecipeEditorViewModelTest` unverändert grün (kein ViewModel-Eingriff).
- `RecipeEditorScreenTest`
  (`feature/recipes/presentation/src/androidTest/.../editor/RecipeEditorScreenTest.kt`)
  unverändert grün: die Tests suchen per `onNodeWithText` auf `"Name"`,
  `"Zutat hinzufügen"` (`:54`) und `"Endgewicht (g)"` (`:90`) — die Strings werden
  nur verschoben, nicht umbenannt, daher bleiben die Texte identisch.
- `./gradlew :app:assembleDebug` als Beweis, dass der `R`-Import in `:app` stimmt.

### Verifikation

```
./gradlew :core:presentation:test :feature:recipes:presentation:test
./gradlew :app:assembleDebug
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] `IngredientRow.kt` existiert nicht mehr in `feature/recipes`
- [ ] `core/presentation/build.gradle.kts` ist unverändert
- [ ] `encodeRouteParam` existiert genau einmal im Repo
- [ ] Rezept-Editor rendert optisch identisch (gleiche Strings, gleiches Layout)
- [ ] Kein Feature-Modul referenziert `R.string` eines anderen Feature-Moduls für die verschobenen Komponenten

**Commit-Vorschlag:** `refactor: IngredientRow, Nährwertsumme, Zutatenliste und RouteParams nach core:presentation verschoben`

---

## WP3 — `DiaryEntry.isRecipeOverridden` inkl. Migration 8→9

**Ziel:** Das Domain-Modell und das persistierte Schema können eine
Tagesanpassung von einem unveränderten Snapshot unterscheiden.

**Abhängigkeit:** keine.

### Zu lesen

- `core/domain/.../model/DiaryEntry.kt` (ganz)
- `core/database/.../entity/DiaryEntryEntity.kt` (ganz)
- `core/database/.../EZDatabase.kt` (KDoc-Versionshistory, `version = 8`)
- `core/database/.../migration/Migrations.kt:225-246`
- `core/data/.../diary/mapper/DiaryEntryEntityMapper.kt` (ganz)
- `core/database/src/test/.../MigrationTest.kt` (Paket `.../core/database/`)
- `core/database/src/test/.../SchemaValidationTest.kt`
- `core/database/schemas/com.example.ezmakro.core.database.EZDatabase/8.json` (als Vorlage)

### Festgelegte Vorgaben

- `isRecipeOverridden: Boolean = false` **mit Default** (A3). Bestehende
  Konstruktionen von `DiaryEntry` in Tests und Produktion kompilieren dadurch
  unverändert weiter.
- Rein additive Migration im Stil von `MIGRATION_7_8`. **Kein**
  Table-Rebuild, kein Daten-Umbau.
- `RecipeSnapshot`-/`FoodItem`-JSON wird in diesem WP **nicht** angefasst.

### Schritte

1. **Domain-Modell.** In `DiaryEntry` das Feld ergänzen:
   ```kotlin
   /**
    * true, wenn die Zutaten des geloggten Rezepts für diesen Tag vom Rezept
    * abweichen (Nutzeranpassung im Tagebuch). Bei `false` entspricht der
    * Snapshot exakt dem Stand beim Loggen.
    */
   val isRecipeOverridden: Boolean = false,
   ```
   Kdoc-Tendenz: Zeile 2-3 des bestehenden Kdoc um den immutbaren
   Snapshot-Charakter ergänzen.
2. **Entity.** Nach `portionCount` und vor den Snapshot-Spalten einfügen:
   ```kotlin
   @ColumnInfo(name = "recipe_overridden", defaultValue = "0")
   val recipeOverridden: Boolean = false,
   ```
   Room mappt `Boolean` ⇄ `INTEGER` nativ. `defaultValue = "0"` ist wichtig, weil
   die bestehenden Spalten `defaultValue` explizit setzen (`"NULL"`) und Room in
   `SchemaValidationTest` den Spalten-Default prüft. Die Deklarationsposition im
   Kotlin-Data-Class ist frei — Room matcht per Spaltenname, die physische Position
   der per `ALTER TABLE` angehängten Spalte ist unerheblich.
3. **Mapper.** `DiaryEntryEntity.toDiaryEntry()` setzt `isRecipeOverridden = recipeOverridden`;
   `DiaryEntry.toEntity()` setzt `recipeOverridden = isRecipeOverridden`.
4. **Migration.**
   ```kotlin
   val MIGRATION_8_9 =
       object : Migration(8, 9) {
           override fun migrate(db: SupportSQLiteDatabase) {
               db.execSQL(
                   "ALTER TABLE diary_entries " +
                       "ADD COLUMN recipe_overridden INTEGER NOT NULL DEFAULT 0",
               )
           }
       }
   ```
   In `ALL_MIGRATIONS` anhängen. Kdoc mit Begründung (A3: nur Markierung, keine
   Datenumstellung) und dem Hinweis, dass bestehende Zeilen `false` erhalten.
5. **Datenbank.** `EZDatabase`: `version = 9`, KDoc-Versionshistory um
   `- 9: Tagebuch-Rezept-Einträge können eine Tagesanpassung markieren
   (diary_entries.recipe_overridden).` ergänzen.
6. **Schema-Export.** `core/database/schemas/com.example.ezmakro.core.database.EZDatabase/9.json`
   neu generieren (Room/KSP erzeugt sie beim Kompilieren von `:core:database`).
   Anschließend prüfen, dass nur die eine Spalte hinzugekommen ist und dass
   `identityHash` aktualisiert wurde.
7. **Tests.** `MigrationTest`: Fall 8→9 ergänzen — Spalte existiert,
   Bestandszeile erhält `0`, `toEntity/toDiaryEntry`-Roundtrip erhält das Flag.
   Zusätzlich ein Roundtrip-Test in `core/database/src/test/.../dao/DiaryEntryDaoTest.kt`,
   der das Flag über ein Update (`upsert`) erhält.
8. **Doku.** `docs/domain-model.md`: Feld in die `DiaryEntry`-Klasse des
   Mermaid-Diagramms ergänzen und im `DiaryEntry`-Abschnitt erläutern.

### Dateien

**Neu**
- `core/database/schemas/com.example.ezmakro.core.database.EZDatabase/9.json`

**Geändert**
- `core/domain/.../model/DiaryEntry.kt`
- `core/database/.../entity/DiaryEntryEntity.kt`
- `core/database/.../EZDatabase.kt`
- `core/database/.../migration/Migrations.kt`
- `core/data/.../diary/mapper/DiaryEntryEntityMapper.kt`
- `core/database/src/test/.../MigrationTest.kt` (Paket `.../core/database/`)
- `core/database/src/test/.../dao/DiaryEntryDaoTest.kt`
- `docs/domain-model.md`

### Tests

- `MigrationTest`: 8→9; Default `0`; kein Datenverlust
- `SchemaValidationTest`: grün durch neues `9.json` (kein manuelles Editieren der JSON)
- `DiaryEntryDaoTest`: `upsert` mit `recipe_overridden = 1` → Read zurück liefert `true`; erneutes `upsert` mit `false` überschreibt (Flag ist überschreibbar, kein Erstsetz-Flag)
- `RoomDiaryRepositoryTest`: `save()` mit `isRecipeOverridden = true` persistiert das Flag
- Bestehende `DiaryEntry`-Tests (`CopyYesterdayUseCaseTest`,
  `QuickAddUseCaseTest`, `AddRecipeToDiaryUseCaseTest`) bleiben unverändert grün —
  Beweis für den Defaultwert

### Verifikation

```
./gradlew :core:database:test :core:data:test :core:domain:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] Migration 8→9 ist rein additiv und getestet
- [ ] `9.json` wurde generiert, nicht handgeschrieben
- [ ] Bestehende Einträge bleiben semantisch unverändert (`false`)
- [ ] Kein bestehender Test musste logisch angepasst werden (nur Signaturen/Imports)

**Commit-Vorschlag:** `feat: Tagebuch-Rezept-Eintrag um isRecipeOverridden erweitert (Migration 8→9)`

---

## WP4 — `EditDiaryRecipeEntryUseCase`

**Ziel:** Die Entscheidung „nur dieser Tag" vs. „Rezept dauerhaft" als eine
domainseitige Operation, die beide Schreibvorgänge in der richtigen Reihenfolge
ausführt (A4) und das Tagebuch-Flag korrekt setzt (A3).

**Abhängigkeit:** WP3.

### Zu lesen

- `core/domain/.../usecase/diary/UpdateDiaryEntryUseCase.kt` (Muster:
  `getById` → `Result` → `copy` → `save`)
- `core/domain/.../usecase/diary/AddToDiaryUseCases.kt:51-72`
  (`AddRecipeToDiaryUseCase`: `EmptyResult<DataError.Local>` als Rückgabetyp)
  und `:85-87` (`DiaryEntryNutritionUseCase`: `entry.consumedNutrition()`)
- `core/domain/.../repository/DiaryRepository.kt`, `RecipeRepository.kt`
- `core/domain/.../result/Result.kt`, `ResultExtensions.kt`
- `core/domain/.../model/DiaryEntry.kt` (Invarianten `:34-63`)
- `core/domain/src/test/.../usecase/diary/AddRecipeToDiaryUseCaseTest.kt` (Fakes-Muster)

### Festgelegte Vorgaben

- **A4:** Rezept zuerst, Tagebuch danach. Bei Rezept-Fehler: **kein** Diary-Write,
  Fehler propagiert.
- **A5:** Das zu schreibende Rezept entsteht aus dem **Live-Rezept**
  (`live.copy(ingredients, portions, finalWeight, lastModifiedAt = now)`).
  `name`, `category`, `isFavorite`, `lastUsedAt` bleiben vom Live-Rezept erhalten.
- Bei `ENTRY_ONLY` behält der Snapshot seinen bisherigen `lastModifiedAt`, damit der
  „Rezept inzwischen geändert"-Vergleich in WP6 weiterhin stimmt.
- `ENTRY_ONLY` setzt `isRecipeOverridden = true`, `RECIPE_AND_ENTRY` setzt `false`.
- Die Menge (`amountGrams`/`portionCount`) gehört in denselben Aufruf, damit genau
  **ein** Diary-Write pro Speichern stattfindet. Die Invariante „genau einer von
  beiden gesetzt" wird von `DiaryEntry.init` (`DiaryEntry.kt:53-62`) durchgesetzt —
  wie schon in `UpdateDiaryEntryUseCase`. Der ViewModel validiert vorher.
- Kein Android-Import, kein eigener Dispatcher.

### Schritte

1. **Datei anlegen.** `core/domain/.../usecase/diary/EditDiaryRecipeEntryUseCase.kt`:
   ```kotlin
   enum class DiaryRecipeEditScope { ENTRY_ONLY, RECIPE_AND_ENTRY }

   class EditDiaryRecipeEntryUseCase(
       private val diaryRepository: DiaryRepository,
       private val recipeRepository: RecipeRepository,
   ) {
       suspend operator fun invoke(
           entryId: DiaryEntryId,
           editedRecipe: Recipe,
           amountGrams: Double?,
           portionCount: Double?,
           scope: DiaryRecipeEditScope,
       ): EmptyResult<DataError.Local>
   }
   ```
2. **Ablauf implementieren.**
   1. `diaryRepository.getById(entryId)` → `Result.Error` durchreichen,
      `null` → `Result.Error(DataError.Local.NOT_FOUND)`.
   2. `entry.recipe == null` → `Result.Error(DataError.Local.NOT_FOUND)`
      (der Eintrag ist kein Rezept-Eintrag).
   3. `ENTRY_ONLY`: Snapshot-Zeitstempel erhalten, damit der
      „Rezept inzwischen geändert"-Vergleich in WP6 stimmt:
      ```kotlin
      recipe = editedRecipe.copy(lastModifiedAt = entry.recipe.lastModifiedAt)
      overridden = true
      ```
      `recipeRepository` wird in diesem Zweig **nicht** angefasst.
   4. `RECIPE_AND_ENTRY`: `recipeRepository.getById(entry.recipe.id)` →
      - `null` → `Result.Error(DataError.Local.NOT_FOUND)` (Rezept gelöscht,
        A4/A5: kein Anlegen eines neuen Rezepts aus dem Tagebuch heraus)
      - Fehler → durchreichen

      Danach **einen** Zeitstempel `now = System.currentTimeMillis()` berechnen und
      für beide Schreibvorgänge verwenden:
      ```kotlin
      val saved =
          live.copy(
              ingredients = editedRecipe.ingredients,
              portions = editedRecipe.portions,
              finalWeight = editedRecipe.finalWeight,
              lastModifiedAt = now,
          )
      ```
      `recipeRepository.save(saved)`; bei Fehler **Rückgabe ohne** Diary-Write
      (A4). `recipe = saved`, `overridden = false`.
   5. `diaryRepository.save(entry.copy(recipe = recipe, amountGrams = amountGrams, portionCount = portionCount, isRecipeOverridden = overridden))`.
3. **Koin.** `factory { EditDiaryRecipeEntryUseCase(get(), get()) }` in
   `feature/diary/presentation/.../di/DiaryPresentationModule.kt:38-44` — dort
   sind die übrigen Diary-Use-Cases registriert (`AddFoodToDiaryUseCase` …
   `UpdateDiaryEntryUseCase`). `core/data/.../di/DiaryDataModule.kt` verdrahtet
   ausschließlich Repository und DataSource und bleibt unverändert.
4. **Tests.** `core/domain/src/test/.../usecase/diary/EditDiaryRecipeEntryUseCaseTest.kt`
   mit handgeschriebenen Fakes, die **die Reihenfolge der Writes protokollieren**.

### Dateien

**Neu**
- `core/domain/.../usecase/diary/EditDiaryRecipeEntryUseCase.kt`
- `core/domain/src/test/.../usecase/diary/EditDiaryRecipeEntryUseCaseTest.kt`

**Geändert**
- `feature/diary/presentation/.../di/DiaryPresentationModule.kt`

### Tests

Fake `DiaryRepository`/`RecipeRepository` mit `MutableList<String>` als Write-Log.

| Fall | Erwartung |
|---|---|
| `ENTRY_ONLY` | Log = `["diary"]`; `isRecipeOverridden == true`; Snapshot-`lastModifiedAt` unverändert gegenüber dem ursprünglichen Entry |
| `ENTRY_ONLY` mit geänderter Menge | Diary-Write enthält neue `amountGrams`/`portionCount` |
| `RECIPE_AND_ENTRY` | Log = `["recipe", "diary"]` (**A4**); `isRecipeOverridden == false`; Rezept-`lastModifiedAt > 0`; Entry-Snapshot trägt denselben neuen Zeitstempel |
| `RECIPE_AND_ENTRY`, Rezept-Write scheitert | Log = `["recipe"]`; `Result.Error`; Entry unverändert |
| `RECIPE_AND_ENTRY`, Quellrezept gelöscht (`getById` → null) | Log **leer**; `DataError.Local.NOT_FOUND` |
| `getById(entryId)` → null | Log leer; `NOT_FOUND` |
| Entry ohne `recipe` (Food-Eintrag) | Log leer; `NOT_FOUND` |
| `getById` liefert `Result.Error` | Fehler unverändert propagiert |
| **A5:** Live-Rezept hat `category`, `isFavorite`, `lastUsedAt`, anderen `name` | Diese Felder erscheinen unverändert im gespeicherten Rezept; nur `ingredients`/`portions`/`finalWeight`/`lastModifiedAt` sind überschrieben |
| Sub-Rezept-Snapshot in `editedRecipe` enthalten | bleibt erhalten |

### Verifikation

```
./gradlew :core:domain:test :core:data:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] `ENTRY_ONLY` berührt das Rezept-Repository **überhaupt nicht**
- [ ] `RECIPE_AND_ENTRY` schreibt zuerst das Rezept (per Write-Log belegt)
- [ ] Kein Pfad legt ein neues Rezept an, wenn das Quellrezept fehlt
- [ ] `name`/`category`/`isFavorite`/`lastUsedAt` des Live-Rezepts werden nie überschrieben
- [ ] Genau ein Diary-Write pro Aufruf

**Commit-Vorschlag:** `feat: EditDiaryRecipeEntryUseCase - Tagesanpassung oder dauerhaftes Rezept-Update`

---

## WP5 — `DiaryRecipeEditScreen`

**Ziel:** Der eigentliche Editor-Screen mit Live-Nährwertvorschau,
Save-Scope-Dialog und Anbindung an die Tagebuch-Zeile.

**Abhängigkeit:** WP1, WP2, WP4.

### Zu lesen

- `feature/recipes/presentation/.../editor/RecipeEditorScreen.kt` und
  `RecipeEditorViewModel.kt` (Muster für Formular, `recomputeNutrition`, Live-Makros,
  FoodPicker-Einbindung)
- `feature/recipes/presentation/.../editor/RecipeEditorAction.kt`
- `feature/diary/presentation/.../day/DiaryDayScreen.kt` (ganz, besonders
  `:61-144` Root-Verdrahtung und `:362-388` `mealSlotSection`)
- `feature/diary/presentation/.../day/DiaryDayState.kt`, `DiaryDayAction.kt`
- `feature/diary/presentation/.../navigation/DiaryRoutes.kt` (ganz)
- `feature/diary/presentation/.../di/DiaryPresentationModule.kt`
- `core/presentation/.../diary/CollapsibleMealSlot.kt`, `DiaryEntryDetail.kt`,
  `DiaryEntryDetailData.kt`
- `core/domain/.../calculation/RecipeCalculation.kt:49-70` (`DiaryEntry.consumedNutrition`)
- `feature/foodpicker/presentation/.../picker/FoodPickerSheet.kt` (Signatur,
  `FoodPickerTarget.RECIPE_INGREDIENT`, `onAddAsIngredient`)
- `core/presentation/.../util/ObserveAsEvents.kt`, `uitext/UiText.kt`, `uitext/DataErrorUiText.kt`

### Festgelegte Vorgaben

- **P4-Lehre:** Der ViewModel beobachtet `foodRepository.observeAll()` und
  `recipeRepository.observeAll()` (Flows, kein `.first()`), damit Zutaten-Lookup und
  `markChangedRecipeSnapshots` live bleiben. Das Formular wird **einmal** aus dem
  geladenen Entry geseedet (Muster `RecipeEditorViewModel.loadExisting:239-284`) —
  spätere Tabellen-Emissionen überschreiben das Formular nicht.
- **`consumedNutrition` wird nicht nachgebaut.** Der ViewModel bildet temporär
  `entry.copy(recipe = edited, amountGrams = …, portionCount = …)` und ruft die
  bestehende Extension `DiaryEntry.consumedNutrition()`
  (`core/domain/.../calculation/RecipeCalculation.kt:49`). Keine zweite
  Nährwertformel im Presentation-Layer (ADR-003).
- Der Screen bietet **nur** Zutaten, Portionen, Endgewicht und die Menge. **Kein**
  Name, keine Kategorie, kein Favorit.
- Kein Sub-Rezept als Zutat (A6): `isRecipe == true` im Picker-Event →
  Meldung, keine Änderung.
- `koinViewModel { parametersOf(entryId) }`; der Screen ist damit im `DiaryRoutes`
  per Route erreichbar — **keine Änderung an `:app`/`MainActivity`**.
- Badges kommen in **WP6**, nicht hier.

### Schritte

1. **Package anlegen.** `feature/diary/presentation/src/main/java/com/example/ezmakro/feature/diary/presentation/recipeentry/`
   mit `DiaryRecipeEditState.kt`, `DiaryRecipeEditAction.kt`, `DiaryRecipeEditEvent.kt`,
   `DiaryRecipeEditViewModel.kt`, `DiaryRecipeEditScreen.kt`.
2. **State definieren** (Namen exakt so, damit WP6 andocken kann):
   ```kotlin
   enum class DiaryAmountMode { PORTIONS, GRAMS }

   data class DiaryRecipeEditState(
       val isLoading: Boolean = true,
       val entryId: String = "",
       val recipeName: String = "",
       val date: String = "",
       val sourceRecipeId: String? = null,
       val canUpdateRecipe: Boolean = false,
       val isSourceRecipeOutdated: Boolean = false,
       val isRecipeOverridden: Boolean = false,
       val amountMode: DiaryAmountMode = DiaryAmountMode.PORTIONS,
       val amountText: String = "",
       val portions: String = "",
       val finalWeight: String = "",
       val ingredients: List<RecipeIngredientDraft> = emptyList(),
       val ingredientNutrition: List<NutritionValues> = emptyList(),
       val totalNutrition: NutritionValues = NutritionValues.ZERO,
       val originalConsumedNutrition: NutritionValues = NutritionValues.ZERO,
       val consumedNutrition: NutritionValues = NutritionValues.ZERO,
       val expandedIngredientIndex: Int? = null,
       val replaceIngredientIndex: Int? = null,
       val isAddPickerOpen: Boolean = false,
       val isSaving: Boolean = false,
       val amountError: UiText? = null,
       val numberError: UiText? = null,
       val ingredientError: UiText? = null,
       val saveError: UiText? = null,
       val isSaveScopeDialogOpen: Boolean = false,
   )
   ```
3. **Action/Event definieren.** Action:
   `OnIngredientRawWeightChange(index, value)`,
   `OnIngredientServingCountChange(index, value)`,
   `OnRemoveIngredient(index)`, `OnReplaceIngredient(index)`,
   `OnAddIngredientClick`, `OnIngredientClick(index)`,
   `OnAmountModeChange(mode)`, `OnAmountChange(value)`,
   `OnPortionsChange(value)`, `OnFinalWeightChange(value)`,
   `OnSaveClick`, `OnConfirmSave(scope)`, `OnDismissSaveScopeDialog`,
   `OnDismissPicker`, `OnBackClick`.
   Event: `Saved`, `NavigateBack`, `ShowMessage(UiText)`.
4. **ViewModel.** Konstruktor: `entryId: String`, `SavedStateHandle`,
   `DiaryRepository`, `RecipeRepository`, `FoodRepository`,
   `BuildRecipeFromDraftsUseCase`, `EditDiaryRecipeEntryUseCase`.
   - `init`: `savedStateHandle[KEY_PORTIONS/KEY_FINAL_WEIGHT/KEY_AMOUNT]` als
     Startwerte; `diaryRepository.getById(entryId)` → State aus `entry.recipe`:
     Drafts aus `ingredients`, `portions`/`finalWeight` aus dem Snapshot,
     `amountText` aus `portionCount` bzw. `amountGrams` (Modus entsprechend),
     `originalConsumedNutrition = entry.consumedNutrition()`.
     `sourceRecipeId = entry.recipe?.id?.value`; `canUpdateRecipe` und
     `isSourceRecipeOutdated` aus `recipeRepository`.
   - `foodRepository.observeAll()` und `recipeRepository.observeAll()` in
     `viewModelScope` beobachten → `foodsState`/`recipesState`; jede Emission ruft
     `markChangedRecipeSnapshots` + `recompute()`.
   - `recompute()`: `buildRecipeFromDraftsUseCase(drafts, foods, recipes)`;
     `ingredientNutrition` je Draft über `RecipeIngredient.nutrition()`;
     `totalNutrition` als Summe; temporären Entry bauen und `consumedNutrition()`
     aufrufen; `totalNutrition` auf `NutritionValues.ZERO` bei
     `RecipeDraftError`, `ingredientError` setzen.
   - `save(scope)`: `buildRecipeFromDraftsUseCase` → bei `Error` passenden
     `ingredientError` (`diary_recipe_edit_error_ingredient_weight` für
     `MissingAmount`, `diary_recipe_edit_error_unknown_food` für `UnknownFood`,
     `diary_recipe_edit_error_missing_snapshot` für `MissingSnapshot`),
     abbrechen. Sonst
     `editDiaryRecipeEntryUseCase(entryId, editedRecipe, amountGrams, portionCount, scope)`
     aufrufen — der Live-Lookup des Rezepts passiert **im Use Case** (A5), nicht
     im ViewModel. Bei `Success` Event `Saved`, bei `Error` `saveError` via
     `DataError.toUiText()`. Vor `save` Amount validieren (`amountText > 0`,
     Modus-Konsistenz).
   - `showSaveScopeDialog` nur öffnen, wenn `ingredients` nicht leer ist und die
     Menge gültig ist.
5. **Screen.** `DiaryRecipeEditRoot` (ViewModel, `ObserveAsEvents`,
   `FoodPickerSheet`) + `DiaryRecipeEditScreen` (nur `state`/`onAction`, mit
   `@Preview`) in **einer** Datei (Muster Skill `android-presentation-mvi`).
   Aufbau im `LazyColumn`:
   1. Kopf: Rezeptname (`titleLarge`), darunter „ins Tagebuch: X" und die
      Vergleichszeile `originalConsumedNutrition → consumedNutrition`.
   2. Hinweis `isSourceRecipeOutdated` → `diary_recipe_edit_info_recipe_outdated`.
   3. Hinweis `!canUpdateRecipe` → `diary_recipe_edit_info_recipe_deleted`.
   4. `RecipeNutritionTotal(totalNutrition = state.totalNutrition, label = stringResource(CoreR.string.recipe_detail_total))`.
   5. `RecipeIngredientList(...)` aus `core:presentation`; `onAddIngredient` öffnet
      den Picker im Modus „hinzufügen". Für „Ersetzen" zusätzlich einen
      `TextButton` „Ersetzen" in der Zeile — falls `RecipeIngredientList` aus WP2
      keinen Ersetzen-Hook hat, wird die Zeile **nicht** umgebaut, sondern das
      Ersetzen über Entfernen + Hinzufügen bedient und im UI-Text
      (`diary_recipe_edit_replace_hint`) erklärt. Diese Abweichung im WP melden.
   6. Portionen-Feld, Endgewicht-Feld.
   7. Amount-Umschalter (Portionen/Gramm) + Feld.
   8. Speichern-Button (`enabled = !state.isSaving`), `saveError` darunter.
   - `SaveScopeDialog`: zwei `RadioButton`-Zeilen mit Titel + erklärendem
     Untertext; „Rezept dauerhaft ändern" `enabled = state.canUpdateRecipe`
     (Farbe/Alpha entsprechend deaktiviert). OK bestätigt `OnConfirmSave(scope)`.
   - `FoodPickerSheet` mit `FoodPickerTarget.RECIPE_INGREDIENT` und
     `onAddAsIngredient = { id, name, servingLabel, isRecipe -> … }`:
     `isRecipe == true` → `ShowMessage(diary_recipe_edit_picker_recipe_not_allowed)`
     und Picker schließen; sonst `OnAddIngredientClick`-Analogon für
     `replaceIngredientIndex` (ersetzen) bzw. anhängen.
6. **Shared-UI-Erweiterung (Einstiegspunkt).**
   - `DiaryEntryDetailData`: `val isRecipe: Boolean = false`
   - `DiaryEntryDetail`: neuer optionaler Parameter
     `onEditIngredients: (() -> Unit)? = null`; wenn != null, zusätzlicher
     `TextButton` „Zutaten" (String `diary_recipe_edit_ingredients_action`)
   - `CollapsibleMealSlot` (`core/presentation/.../diary/CollapsibleMealSlot.kt:37-51`):
     `onEditIngredientsEntry: ((String) -> Unit)? = null` als weiterer Parameter
     durchreichen und im `entries.forEach` (`:71-77`) an `DiaryEntryDetail` geben;
     `isRecipe` aus `DiaryEntryDetailData` mitgeben
   - `DiaryUi.kt`: `DiaryEntryUi.isRecipe` existiert bereits (Felddeklaration
     `:18`, Zuweisung `:60`) — im `toDetailData()`-Mapping
     (`DiaryDayScreen.kt:390`) durchreichen
   - `DiaryDayScreen`: `mealSlotSection`/`DiaryDayCallbacks` um
     `onRequestEditIngredients` erweitern; `DiaryDayRoot` erhält den Parameter
     `onEditRecipeEntry: (String) -> Unit`
7. **Navigation.** In `DiaryRoutes.kt`:
   `internal const val ROUTE_DIARY_RECIPE_EDIT = "diary_recipe_edit"`,
   `$ROUTE_DIARY_RECIPE_EDIT_PARAM_ENTRY_ID = "entryId"`, Pattern
   `"$ROUTE_DIARY_RECIPE_EDIT/{entryId}"` mit `NavType.StringType`, Aufruf von
   `DiaryRecipeEditRoot(onSaved = { popBackStack() }, onNavigateBack = { popBackStack() })`.
   In `dayComposable` `onNavigateToQuickAdd`-Analogon ergänzen:
   `navController.navigate("$ROUTE_DIARY_RECIPE_EDIT/${encodeRouteParam(entryId)}")`.
   `encodeRouteParam` kommt aus `core:presentation` (A7).
8. **Koin.** In `DiaryPresentationModule` (aktuell `:21-45`) nach dem
   `DiaryDayViewModel`-Block:
   ```kotlin
   viewModel {
       DiaryRecipeEditViewModel(
           entryId = get(),
           diaryRepository = get(),
           editDiaryRecipeEntryUseCase = get(),
           recipeRepository = get(),
           foodRepository = get(),
           buildRecipeFromDraftsUseCase = get(),
           savedStateHandle = get(),
       )
   }
   factory { EditDiaryRecipeEntryUseCase(get(), get()) }
   factory { BuildRecipeFromDraftsUseCase() }
   ```
   Das Modul nutzt durchgängig **benannte** Argumente (`:24-32`), daher ist die
   Parameterreihenfolge des ViewModels unkritisch. `BuildRecipeFromDraftsUseCase`
   wird in **jedem** Modul, das den Editor nutzt, lokal per `factory`
   registriert (WP1 registriert sie in `recipePresentationModule`) — Koin-Dubletten
   vermeiden: pro Modul nur einmal deklarieren.
9. **Strings.** Neu in `feature/diary/presentation/src/main/res/values/strings.xml`:
   `diary_recipe_edit_title`, `diary_recipe_edit_logged_amount`,
   `diary_recipe_edit_amount_mode_label`, `diary_recipe_edit_save_scope_title`,
   `diary_recipe_edit_scope_entry_only`, `diary_recipe_edit_scope_entry_only_hint`,
   `diary_recipe_edit_scope_recipe`, `diary_recipe_edit_scope_recipe_hint`,
   `diary_recipe_edit_ingredients_action`, `diary_recipe_edit_replace_hint`,
   `diary_recipe_edit_info_recipe_outdated`, `diary_recipe_edit_info_recipe_deleted`,
   `diary_recipe_edit_picker_recipe_not_allowed`,
   `diary_recipe_edit_error_unknown_food`, `diary_recipe_edit_error_missing_snapshot`,
   `diary_recipe_edit_error_no_ingredients`, `diary_recipe_edit_error_ingredient_weight`,
   `diary_recipe_edit_error_save_failed`,
   `diary_recipe_edit_compare_label`.
   **Keine** neuen Labels für Portionen, Endgewicht, Speichern oder die
   Nährwert-Summe: `recipe_editor_portions`, `recipe_editor_final_weight`,
   `recipe_editor_final_weight_hint`, `recipe_editor_save`, `recipe_detail_total`,
   `recipe_editor_add_ingredient` sowie die Rohgewicht-/Portionslabels liegen nach
   WP2 in `core:presentation` und werden von dort referenziert — keine Duplikate.
   Die Familie `recipe_editor_error_*` bleibt dagegen bewusst im Recipes-Modul
   (dort wird sie ausschließlich verwendet, siehe WP2 Schritt 6), deshalb legt
   `feature:diary` eigene Fehlerstrings an — der Inhalt von
   `recipe_editor_error_ingredient_weight` („Zutat braucht Rohgewicht oder Anzahl")
   wird dabei wortgleich übernommen, damit die Meldung identisch bleibt.

### Dateien

**Neu** (alle unter `feature/diary/presentation/src/main/java/com/example/ezmakro/feature/diary/presentation/recipeentry/`)
- `DiaryRecipeEditState.kt`, `DiaryRecipeEditAction.kt`, `DiaryRecipeEditEvent.kt`
- `DiaryRecipeEditViewModel.kt`, `DiaryRecipeEditScreen.kt`

**Geändert**
- `feature/diary/presentation/.../navigation/DiaryRoutes.kt`
- `feature/diary/presentation/.../di/DiaryPresentationModule.kt`
- `feature/diary/presentation/.../day/DiaryDayScreen.kt`
- `feature/diary/presentation/.../model/DiaryUi.kt`
- `core/presentation/.../diary/DiaryEntryDetailData.kt`, `DiaryEntryDetail.kt`, `CollapsibleMealSlot.kt`
- `feature/diary/presentation/src/main/res/values/strings.xml`

**Tests neu**
- `feature/diary/presentation/src/test/.../recipeentry/DiaryRecipeEditViewModelTest.kt`
- `feature/diary/presentation/src/test/.../fake/FakeRecipeRepository.kt` (diarylokal)
- `feature/diary/presentation/src/test/.../fake/FakeFoodRepository.kt` (diarylokal)

**Hinweis:** Die Fakes in `feature/recipes/presentation/src/test/.../fake/FakeRepositories.kt`
sind modul-lokal und für `feature:diary` nicht sichtbar. Für das Diary-Testsource-Set
sind eigene Fakes nötig. `FakeDiaryRepository` (`.../fake/FakeDiaryRepository.kt`)
existiert bereits und wird um das Flag erweitert.

### Tests

`DiaryRecipeEditViewModelTest` (JUnit5 + Turbine + AssertK + `UnconfinedTestDispatcher`):

| Fall | Erwartung |
|---|---|
| Laden | `ingredients` entspricht dem Snapshot, `portions`/`finalWeight` aus dem Rezept, `amountText` aus `portionCount`, Modus `PORTIONS` |
| Laden bei `amountGrams` | Modus `GRAMS`, `amountText` = Gramm |
| `OnIngredientRawWeightChange` | `totalNutrition` **und** `consumedNutrition` steigen entsprechend |
| `OnIngredientServingCountChange` | `consumedNutrition` ändert sich |
| `OnSaveClick` | `isSaveScopeDialogOpen == true`, **kein** Write |
| `OnConfirmSave(ENTRY_ONLY)` | nur Diary geschrieben, `canUpdateRecipe` unberührt |
| `OnConfirmSave(RECIPE_AND_ENTRY)` | Recipe + Diary geschrieben |
| Quellrezept gelöscht | `canUpdateRecipe == false`, `sourceRecipeId` bleibt gesetzt |
| Live-Rezept neuer als Snapshot | `isSourceRecipeOutdated == true` |
| Draft mit unbekannter Food-Id, `OnSaveClick` | `ingredientError` gesetzt, `isSaveScopeDialogOpen == false`, kein Write |
| Draft ohne Menge | `ingredientError` = `diary_recipe_edit_error_ingredient_weight` |
| Picker-Event `isRecipe = true` | `ShowMessage`, `ingredients` unverändert |
| Picker-Event `isRecipe = false` | Zutat angehängt bzw. ersetzt |
| Rezept-Write-Fehler | `saveError` gesetzt, `isSaving == false` |

AndroidTest `DiaryRecipeEditScreenTest` (analog `RecipeEditorScreenTest`):
Save-Dialog öffnet, „Rezept dauerhaft ändern" ist deaktiviert wenn
`canUpdateRecipe == false`, Bestätigen löst `OnConfirmSave` mit dem richtigen
Scope aus.

### Verifikation

```
./gradlew :feature:diary:presentation:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] `app/.../MainActivity.kt` ist unverändert
- [ ] Kein `import com.example.ezmakro.feature.recipes` in `feature:diary`
- [ ] „Zutaten"-Button erscheint nur bei Rezept-Einträgen
- [ ] Keine hartkodierten Strings; alle über `stringResource`/`UiText`
- [ ] `consumedNutrition` stammt aus `DiaryEntry.consumedNutrition()`, nicht aus eigener Formel
- [ ] `@Preview` für `DiaryRecipeEditScreen` vorhanden
- [ ] Koin-Graph startet ohne `NoDefinitionFoundException` ( Smoke-Test: App startet, Diary → Rezept-Eintrag → Zutaten)

**Commit-Vorschlag:** `feat: DiaryRecipeEditScreen - Rezept-Eintrag im Tagebuch mit Tagesanpassung oder Rezept-Update bearbeiten`

---

## WP6 — Badges „Tagesanpassung" und „Rezept geändert"

**Ziel:** Der Nutzer sieht auf einen Blick, ob ein Tagebuch-Eintrag vom Rezept
abweicht und ob das Rezept seit dem Loggen verändert wurde.

**Abhängigkeit:** WP5.

### Zu lesen

- `feature/diary/presentation/.../day/DiaryDayViewModel.kt` (ganz, insbesondere
  `:60-78` der `combine`-Block)
- `feature/diary/presentation/.../model/DiaryUi.kt`
- `core/presentation/.../diary/DiaryEntryDetail.kt`, `DiaryEntryDetailData.kt`
- `feature/diary/presentation/src/test/.../fake/FakeDiaryRepository.kt`
- `core/domain/.../model/DiaryEntry.kt`, `Recipe.kt`

### Festgelegte Vorgaben

- **Kein zusätzlicher Flow pro Eintrag.** Der `DiaryDayViewModel` beobachtet
  `recipeRepository.observeAll()` **einmal** im bestehenden `combine`-Block und
  baut daraus eine Map `recipeId → lastModifiedAt`. `observeById` je Eintrag wäre
  N-Flows und verstößt gegen das P4-Muster.
- `isSourceRecipeOutdated` nur für Recipe-Einträge, und nur wenn das Live-Rezept
  einen **echt neueren** Zeitstempel hat (`live.lastModifiedAt > snapshot.lastModifiedAt`).
- Beide Flags werden im `DiaryEntryUi` geführt, nicht im Domain-Modell.

### Schritte

1. **`DiaryEntryUi` erweitern:** `val isRecipeOverridden: Boolean = false`,
   `val isSourceRecipeOutdated: Boolean = false`. Zusätzlich in
   `toDiaryEntryUi()` (`DiaryUi.kt:60`, direkt neben `isRecipe = recipe != null`)
   `isRecipeOverridden = recipe?.isRecipeOverridden == true` setzen — das Feld
   kommt aus WP3 über `DiaryEntry` mit.
2. **`DiaryDayState`:** keine Änderung nötig — die Listen
   `breakfast`/`lunch`/`dinner`/`snacks` enthalten die erweiterten `DiaryEntryUi`.
3. **`DiaryDayViewModel`:** `recipeRepository` als Konstruktor-Parameter aufnehmen
   (`DiaryDayViewModel.kt:40-48` und `DiaryPresentationModule.kt:24-32`, dort
   benannte Argumente — bestehende Zeilen bleiben unverändert). Der
   `flatMapLatest`-Block (`:61-65`) bleibt bestehen und wird **einmal** um eine
   dritte Quelle ergänzt; die Reihenfolge ist wichtig, weil `observeByDate(d)`
   erst innerhalb von `flatMapLatest` verfügbar ist:
   ```kotlin
   currentDateProvider.date
       .flatMapLatest { d ->
           repository
               .observeByDate(d)
               .combine(goalRepository.observeGoal()) { entries, goal -> entries to goal }
               .combine(recipeRepository.observeAll()) { (entries, goal), recipes ->
                   Triple(entries, goal, recipes)
               }
       }.onEach { (entries, goal, recipes) -> /* … */ }
   ```
   Der Map-Build (`recipes.associate { it.id.value to it.lastModifiedAt }`) und die
   Flag-Berechnung passieren **nach** `entries.map { it.toDiaryEntryUi() }`, weil
   `toDiaryEntryUi()` (`DiaryUi.kt:60`) keinen Zugriff auf die Map hat:
   ```kotlin
   val modifiedAt = recipes.associate { it.id.value to it.lastModifiedAt }
   val ui = entries.map { entry ->
       val base = entry.toDiaryEntryUi()
       val liveAt = entry.recipe?.id?.value?.let { modifiedAt[it] }
       base.copy(
           isSourceRecipeOutdated = liveAt != null && liveAt > entry.recipe.lastModifiedAt,
       )
   }
   ```
   `entry.recipe` ist das **vollständige** `Recipe` (der Tagebuch-Snapshot
   serialisiert `Recipe`, nicht `RecipeSnapshot` — siehe `JsonConverters.encodeRecipe`
   `core/database/.../converter/JsonConverters.kt:46`), deshalb steht der
   Snapshot-Zeitstempel direkt in `entry.recipe.lastModifiedAt`
   (`Recipe.kt:13`) und ist nicht nullable — das entspricht dem etablierten
   Vergleich in `RecipeEditorViewModel.kt:315-317`. Fehlt das Rezept in der Map
   (gelöscht), bleibt das Flag `false`.
4. **`DiaryEntryDetailData`:** `val isRecipeOverridden: Boolean = false`,
   `val isSourceRecipeOutdated: Boolean = false`.
5. **`DiaryEntryDetail`:** unter dem Namen ein `Row` mit optionalen Badges:
   - `isRecipeOverridden` → `diary_recipe_edit_badge_day_override` („Tagesanpassung")
   - `isSourceRecipeOutdated` → `diary_recipe_edit_badge_recipe_changed` („Rezept geändert")
   Darstellung als kleine `Text` mit `labelSmall` in `onSurfaceVariant`, optional
   mit Surface-Container-Hintergrund. Beide Badges können gleichzeitig stehen.
6. **`DiaryDayScreen`:** Flags im `toDetailData()`-Mapping durchreichen.
7. **Strings** in `feature/diary/presentation/.../strings.xml`:
   `diary_recipe_edit_badge_day_override`, `diary_recipe_edit_badge_recipe_changed`.
8. **Docs.** `docs/roadmap.md`: im Absatz unter der Phasen-Tabelle (aktuell
   Zeilen 26-29, beginnend mit „Details zu umgesetzten Phasen") einen Satz mit
   dem Verweis auf `docs/Plan_Diary_Rezept_Eintrag.md` ergänzen.

### Dateien

**Geändert**
- `feature/diary/presentation/.../day/DiaryDayViewModel.kt`
- `feature/diary/presentation/.../day/DiaryDayScreen.kt`
- `feature/diary/presentation/.../model/DiaryUi.kt`
- `feature/diary/presentation/.../di/DiaryPresentationModule.kt`
- `core/presentation/.../diary/DiaryEntryDetailData.kt`, `DiaryEntryDetail.kt`
- `feature/diary/presentation/src/main/res/values/strings.xml`
- `docs/roadmap.md`

### Tests

`DiaryDayViewModelTest` (bestehend, erweitern):

| Fall | Erwartung |
|---|---|
| Recipe-Eintrag mit `isRecipeOverridden = true` | Flag im `DiaryEntryUi` der passenden Slot-Liste `true` |
| Recipe-Eintrag mit Flag `false`, Live-Rezept unverändert | beide Flags `false` |
| Live-Rezept mit neuerem `lastModifiedAt` | `isSourceRecipeOutdated == true` |
| Live-Rezept mit **älterem** `lastModifiedAt` | `isSourceRecipeOutdated == false` |
| Live-Rezept fehlt in der Map (gelöscht) | `isSourceRecipeOutdated == false`, kein Crash |
| Food-Eintrag | beide Flags `false`, unabhängig von `modifiedAt` |
| Recipe-Update in der DB → neue Emission | Flag kippt ohne ViewModel-Neuanlage auf `true` (**P4-Regressionstest**) |

AndroidTest `DiaryDayScreenTest` erweitern: Badge sichtbar bei
`isRecipeOverridden`, nicht bei Food-Einträgen.

### Verifikation

```
./gradlew :feature:diary:presentation:test
./gradlew build detekt ktlintCheck
```

### Akzeptanzkriterien

- [ ] Genau ein `observeAll()`-Aufruf für Rezepte im `DiaryDayViewModel`
- [ ] Food-Einträge zeigen nie ein Rezept-Badge
- [ ] Ein Rezept-Update im Rezept-Editor flippt das Badge **live**, ohne den
      DiaryDay-Screen zu verlassen (P4-Regression)
- [ ] `docs/roadmap.md` verweist auf den Plan

**Commit-Vorschlag:** `feat: Badges "Tagesanpassung" und "Rezept geändert" im Tagebuch`

---

## 5. Gemeinsame Verifikation am Ende

Nach WP6 zusätzlich:

```
./gradlew clean build detekt ktlintCheck
```

Manueller Smoke-Test:

1. Rezept anlegen → ins Tagebuch (Portionen) → Zeile „Zutaten"
2. Rohgewicht einer Zutat erhöhen → Vergleichszeile zeigt vorher/nachher
3. „Nur für diesen Tag" → Tagebuchwert geändert, Rezept in der Liste unverändert
4. Erneut „Zutaten" → andere Marke per Picker einsetzen → „Rezept dauerhaft
   ändern" → Tagebuchwert und Rezeptliste geändert
5. Rezeptliste: das Rezept zeigt die neuen Zutaten
6. Zurück im Tagebuch: Badge „Tagesanpassung" beim Eintrag aus Schritt 3
7. Rezept erneut im Rezept-Editor ändern → Tagebuch-Badge „Rezept geändert"
   erscheint ohne Navigation
8. Rezept löschen → alter Eintrag rendert weiter, Bearbeiten bietet nur noch
   „Nur für diesen Tag"

## 6. Nicht-Ziele

- Bestehende Tagebuch-Einträge desselben Rezepts werden bei „dauerhaft ändern"
  **nicht** nachgezogen. Kein „auf andere Tage übernehmen".
- Kein Override-Faktor und kein Hochrechnen: die Tagesanpassung ist eine
  vollwertige Zutatenliste im Snapshot.
- Keine nachträgliche Änderung von Name, Kategorie oder Favorit aus dem Tagebuch.
- Kein Sub-Rezept als Zutat im Tagebuch-Editor (A6).
- Kein Skalieren des Rezepts.
- `DiaryUi.sumNutrition()` (`DiaryUi.kt:73`) mit seinem String-Round-Trip wird
  nicht angefasst — eigenes Backlog-Thema, im Plan nur vermerkt.

## 7. Dokumentations-Pflege

| Dokument | WP | Änderung |
|---|---|---|
| `docs/domain-model.md` | WP3 | `DiaryEntry.isRecipeOverridden` + Diagramm |
| `docs/Bugfix_Plan.md` | WP2 | Backlog-Zeile zu `diary → foodpicker` |
| `docs/roadmap.md` | WP6 | Verweis auf diesen Plan |
| `docs/Plan_Diary_Rezept_Eintrag.md` | laufend | Statuszeilen je WP nach dem Commit ergänzen |