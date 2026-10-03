# Bugfix-Plan P4 — Stale Snapshots (UI beobachtet nichts)

Stand: 2026-10-03. Folgeplan zu `docs/Bugfix_Plan.md` (P1–P3 abgeschlossen).
**Status: abgeschlossen** — alle fünf Pakete sind implementiert und committed
(siehe „Abschluss — Umsetzungsnotizen"). Auslöser des Plans war der gemeldete Bug:

> Lebensmittel Ansicht → Vollbild → lokale Änderung → zurück zur Lebensmittel
> Ansicht (Änderung noch nicht da). Erst wenn die Lebensmittel Ansicht schließt
> und dann nochmal auf Lebensmittel gedrückt wird, ist die Änderung sichtbar.

Bei der Root-Cause-Analyse haben sich **sechs Instanzen derselben Fehlerklasse**
gefunden: Ein ViewModel schreibt ein Domain-Objekt als einmaligen Snapshot
(`getById`, `observeAll().first()`, `getSessionItem`, Netzwerk-Response) in den
Compose-State und beobachtet die Quelle nicht weiter. Sichtbar wird das immer
dann, wenn das Item während der Lebenszeit des Back-Stack-Eintrags geändert
wird — also typischerweise: öffnen → Vollbild/Editor → ändern → zurück.

Jedes Arbeitspaket wird durch einen eigenen Agent-Prompt abgearbeitet (siehe
letzter Abschnitt). Gültig ist immer das lokale Gate: `./gradlew build`,
`./gradlew detekt`, `./gradlew ktlintCheck`. Kein Commit ohne explizite
Freigabe (`Commit P4.x`).

---

## Überblick

| ID | Ort | Kernproblem | Lösung |
|----|-----|-------------|--------|
| **P4.1** | `FoodPickerViewModel` | Inline-Detail-Panel hält `detailFood`/`detailRecipe` als eingefrorenen Snapshot; nach Änderung im Vollbild-Editor zeigt das Panel alte Werte | State hält nur `detailFoodId`/`detailRecipeId`; `detailFood`/`detailRecipe` werden aus den beobachteten Flows projiziert |
| **P4.2** | `FoodPickerViewModel` + `MainActivity` | `onFoodClick` prüft `onlineFoodItems` **zuerst** → lokal editierter OFF-Treffer zeigt uneditierte Werte; Tagebuch-Eintrag nimmt die editierte Zeile → Panel und Eintrag widersprechen sich | Auflösung lokal → Session → Online; `makeAddToDiaryFromPicker` an `makeAddFoodToDiary` angleichen |
| **P4.3** | `RecipeEditorViewModel` | `loadExisting()` sammelt `observeById` dauerhaft und überschreibt das Formular bei **jedem** Write auf `recipes` → hinzugefügte Zutaten verschwinden, getippte Portionen werden zurückgesetzt | Formular einmalig seeden (`take(1)`), Draft danach vom Editor besessen |
| **P4.4** | `HistoryViewModel` | History lädt die Tagesliste genau einmal (kein Flow) → nach Tagebuch-Änderungen falsche kcal/Anzahl | Neuer Range-Flow `observeByDateRange` durch DAO → DataSource → Repository → UseCase |
| **P4.5** | `FoodEditViewModel`, `GoalsViewModel` | `.first()`-Prefill; Rückschreiben überschreibt zwischenzeitliche Änderungen (Favorit) | `FoodRepository.observeById` + Dirty-Guard; Goals analog |

---

## Root Cause des gemeldeten Bugs

Der „Lebensmittel"-Tab **ist** der FoodPicker im Vollbild — es gibt keine
separate Lebensmittel-Liste. Kette:

```
MainActivity.kt:117   BottomNav „Lebensmittel" → navigate("food")
FoodRoutes.kt:153     foodGraph, startDestination = food_search
FoodSearchScreen.kt   FoodSearchRoot → FoodPickerContent(FoodPickerTarget.BROWSE)
FoodPickerSheet.kt:128  if (state.detailFood != null || state.detailRecipe != null) → nur Panel
```

Ablauf des Bugs:

1. Tippen auf eine Zeile → `FoodPickerViewModel.onFoodClick()` liest das Item per
   `foodRepository.getById(...)` **einmalig** und legt es als `state.detailFood`
   ab (`FoodPickerViewModel.kt:196`).
2. „Vollbild öffnen" → `NavigateToFoodDetail` → `food_detail` → `FoodDetailViewModel`.
   Der beobachtet `observeAll()` korrekt und zeigt daher **frische** Werte.
3. „Bearbeiten" → `food_edit` → `FoodEditViewModel.save()` → `saveUserEdit()` →
   Room-Write.
4. Zurück zu `food_search`: Die **Listen** (`recentFoods`, `favoriteFoods`,
   `localSearchResults`) aktualisieren sich, weil sie Room-Flows beobachten. Aber
   `FoodPickerContent` blendet die Liste komplett aus, solange `detailFood != null`
   (`FoodPickerSheet.kt:128-139`) — sichtbar ist nur das **Inline-Panel mit dem
   alten Snapshot**.
5. `state.detailFood` wird ausschließlich von `FoodPickerAction.OnDismissDetail`
   zurückgesetzt (`FoodPickerViewModel.kt:109`). Deshalb hilft nur: Panel
   schließen (id neu laden) oder Ansicht komplett schließen und neu öffnen
   (neuer ViewModel).

**Nebenwirkungen desselben Feldes (mitfixen in P4.1):**

- Der Favoriten-Stern im Panel (`FoodPickerDetailPanel.kt:46`) leuchtet nach
  `OnToggleFavorite` nie auf, obwohl der Write in Room landet.
- Nach `resetToOriginal` / `refetchFromOff` auf der Vollbild-Seite zeigt das
  Panel weiter die Werte vor der Aktion.

**Gegenprobe (kein Bug):** Die Offline-/FTS-Suche ist nicht betroffen.
`searchByName` mappt über `dao.observeAll()` (`RoomFoodLocalDataSource.kt:42-60`)
und die FTS-Synchronisation läuft über Room-Trigger
(`room_fts_content_sync_food_items_fts_AFTER_UPDATE`, Schema v8). Auch ein
umbenanntes Lebensmittel ist danach sofort unter dem neuen Namen auffindbar.

---

## P4.1 — FoodPicker: Inline-Detail aus dem Flow projizieren

**Status:** erledigt — Commit `9e77166`.

### Ziel

Nährwerte, Favoriten-Status und Name im Inline-Panel des FoodPickers müssen sich
aktualisieren, ohne dass der Nutzer das Panel schließen und neu öffnen muss —
egal ob die Änderung über Vollbild → Editor, über Favoriten-Toggle, über
Zurücksetzen auf Original oder über OFF-Refetch entstanden ist.

### Umsetzung

**1. `FoodPickerState.kt`**

- Neu: `detailFoodId: String? = null`, `detailRecipeId: String? = null`.
- `detailFood: FoodItem?` / `detailRecipe: Recipe?` bleiben im State, werden aber
  zu **abgeleiteten Projektionen**. KDoc ergänzen:
  „Read-only-Projektion, gepflegt ausschließlich durch
  `FoodPickerViewModel.refreshDetail()`; Actions schreiben nur die IDs."
- Reihenfolge im Konstruktor so wählen, dass die neue Non-Default-Property
  (`savedTab`-analog unkritisch, da alle Defaults) bestehende Named-Argument-
  Aufrufer nicht bricht. Bestehende Tests, die `FoodPickerState(...)` positional
  oder mit Named Args bauen, prüfen.

**2. `FoodPickerViewModel.kt`**

- Neue private Rohdaten-Felder, die die bereits laufenden Collector speisen:

```kotlin
private var localFoods: List<FoodItem> = emptyList()
private var localRecipes: List<Recipe> = emptyList()
private val sessionFoods = mutableMapOf<String, FoodItem>()
```

- `observeAllFoods()` / `observeAllRecipes()`: zusätzlich `localFoods` /
  `localRecipes` setzen und am Ende `refreshDetail()` aufrufen.
- Neu: `refreshDetail()` (siehe unten).
- Neu: `resolveFood(id)` (siehe P4.2; wird hier schon eingeführt, damit
  `refreshDetail()` eine einzige Auflösungslogik hat).
- `onFoodClick()`: schreibt nur noch `detailFoodId = foodId`; `rememberRemoteItem`
  zusätzlich in `sessionFoods` spiegeln, dann `refreshDetail()`.
- `onRecipeClick()`: schreibt nur noch `detailRecipeId = recipeId`.
- `FoodPickerAction.OnDismissDetail`: löscht die **IDs**; `refreshDetail()`
  setzt die Projektionen dadurch automatisch auf `null`.
- `openDetailFullscreen()` / `onDetailAddClick()`: weiterhin über die IDs bzw.
  über die Projektion — Verhalten bleibt identisch.

```kotlin
/**
 * Resolve a food id against every source the picker knows about, local first:
 * a locally edited row must win over the frozen offline-search snapshot.
 */
private fun resolveFood(id: String): FoodItem? =
    localFoods.firstOrNull { it.id.value == id }
        ?: sessionFoods[id]
        ?: _state.value.onlineFoodItems.firstOrNull { it.id.value == id }

/** Re-project the open inline detail from the currently observed data. */
private fun refreshDetail() {
    val s = _state.value
    if (s.detailFoodId == null && s.detailRecipeId == null) return
    _state.update {
        it.copy(
            detailFood = it.detailFoodId?.let(::resolveFood),
            detailRecipe = it.detailRecipeId?.let { id -> localRecipes.firstOrNull { it.id.value == id } },
        )
    }
}
```

**Bewusst NICHT gemacht:** kein `stateIn`/`combine`-Refactor der Projektion und
keine Änderung an den vier `FoodPickerContent`-Aufrufern
(`FoodPickerSheet.kt`, `FoodSearchScreen.kt`, `RecipeEditorScreen.kt`,
`DiaryDayScreen.kt`). Der Diff bleibt auf `FoodPickerState.kt` +
`FoodPickerViewModel.kt` begrenzt.

**Verhalten bei gelöschtem Eintrag:** `refreshDetail()` setzt die Projektion auf
`null`, das Panel schließt sich damit selbst — das ist die gewünschte Reaktion.

### Betroffene Tests

`feature/foodpicker/presentation/src/test/java/.../FoodPickerViewModelTest.kt`
(dort liegen die lokalen `FakeFoodRepository`/`FakeRecipeRepository`):

- Die Fake-Repositories müssen Writes auch wirklich emittieren, sonst sind die
  neuen Tests nicht aussagekräftig: `toggleFavorite` dreht `isFavorite` in der
  emittierten Liste, `saveUserEdit`/`cacheRemoteItem` schreiben in die Liste.
- Bestehende `foodClick`/`recipeClick`-Tests bleiben gültig (sie prüfen
  `detailFood`/`detailRecipe`), ggf. auf die neuen Felder umstellen.
- Neu:
  - `detail panel shows edited nutrition without reopening` — Item öffnen,
    `emitFoods(...)` mit geänderten Nährwerten, `state.value.detailFood`
    enthält die neuen Werte.
  - `detail panel reflects a favorite toggle` — Stern im Panel wird gesetzt.
  - `recipe detail panel reflects recipe edits` — analog über `FakeRecipeRepository`.
  - `detail panel closes when the food disappears from the source`.
  - `onDismissDetail clears the selection ids`.

`feature/food/presentation/src/androidTest/.../FoodSearchScreenTest.kt`: prüfen,
ob es Annahmen über `detailFood` gibt.

---

## P4.2 — FoodPicker: lokale Version schlägt Online-Snapshot

**Status:** erledigt — Commits `36e66ca` (Hauptbug) und `386900b` (Nachzug: bedingter `popBackStack` im Rezeptpfad).

### Ziel

Ein OFF-Treffer, der lokal bereits gecacht **und editiert** ist, muss überall
dieselben Werte zeigen. Heute zeigt das Inline-Panel den unveränderten
OFF-Snapshot, während `MainActivity` beim Tagebuch-Eintrag die editierte Zeile
persistiert.

### Umsetzung

**1. `FoodPickerViewModel.onFoodClick()`** (`:178-199`): die Vorrang-Regel
`onlineFoodItems` → `getById` → `getSessionItem` ersetzen durch
`resolveFood(id)` (lokal → Session → Online). `rememberRemoteItem` nur noch
aufrufen, wenn das Item tatsächlich nur aus `onlineFoodItems` stammt
(d. h. kein lokaler und kein Session-Treffer).

**2. `FoodPickerViewModel.confirmAddToDiary()`** (`:310-344`): die
`cacheRemoteItem`-Entscheidung und die übergebene ID aus dem **auflösten**
Item ableiten statt aus dem eingefrorenen `s.onlineFoodItems`-Snapshot. Aktuell
captured der Handler `val s = _state.value` vor dem `launch` und entscheidet
danach anhand dieses Moments.

**3. `MainActivity.makeAddToDiaryFromPicker()`** (`:189-220`): an
`makeAddFoodToDiary` angleichen —

- `getSessionItem`-Fallback nach `getById`,
- `cacheRemoteItem(food)` vor `addUseCase(...)`, damit session-only Items
  dauerhaft persistiert werden,
- `null`-Ergebnis darf nicht stillschweigend den Drop verursachen (der
  `navController.popBackStack(ROUTE_DIARY, ...)` läuft aktuell in jedem Fall).

**Praxis-Detail zum `popBackStack`:** der liegt nicht in
`makeAddToDiaryFromPicker`, sondern in den beiden Geschwistern
`makeAddFoodToDiary` und `makeAddRecipeToDiary` — dort sprang er auch dann ins
Tagebuch, wenn die ID nicht auflösbar war. P4.2 zieht ihn in **beiden**
Funktionen hinter das `addUseCase` und bricht bei `null` per `return@launch`
ab, damit der Screen stehen bleibt, statt als ob der Eintrag existierte zu
springen. Beim Food-Pfad ist der Fall real (session-only Items, die weder in
Room noch in der Session liegen), beim Rezept-Pfad praktisch nur über ein
Delete-Race erreichbar (die IDs stammen aus Room-Flows, Rezepte haben keinen
Session-/Remote-Pfad) — die Änderung wird trotzdem symmetrisch durchgezogen.
Ein Test ist für beide Lambdas nicht möglich: `MainActivity` hat keine
Test-Infrastruktur.

**ADR-004-Hinweis:** `cacheRemoteItem` überschreibt eine `UserModified`-Zeile
nicht (`OfflineFirstFoodRepository.kt:130-133`). Das bleibt bei P4.2 gültig.

### Betroffene Tests

- `FoodPickerViewModelTest`:
  - `onFoodClick prefers the locally edited food over the online snapshot`
  - `onFoodClick on a session-only item persists nothing and shows the snapshot`
  - `confirmAddToDiary uses the resolved item, not the frozen online list`
- `OfflineFirstFoodRepositoryTest`: bestehende ADR-004-Tests laufen unverändert
  weiter und sind der Beweis, dass die Reihenfolgeänderung safe ist.

---

## P4.3 — Rezept-Editor: Formular einmalig seeden (Datenverlust)

**Status:** erledigt — Commit `88c4fc5`.

### Ziel

Im Rezepteditor dürfen hinzugefügte Zutaten und getippte Werte nicht
verschwinden, nur weil irgendwo ein Rezept angefasst wurde.

### Root Cause

`RecipeEditorViewModel.loadExisting()` (`:228-272`) sammelt
`recipeRepository.observeById()` **dauerhaft**. Die Implementierung ist
`dao.observeAll().map { … }` (`RoomRecipeLocalDataSource.kt:31-34`), und
`observeAll()` beobachtet die **gesamte** `recipes`-Tabelle. Damit feuert der
Collector bei **jedem** Write auf `recipes` — also auch bei
`markUsed` (`RecipeDao.kt:63-67`) und `setFavorite` (`RecipeDao.kt:57-61`).

Genau die schreibt der Zutaten-Picker im Editor:
`FoodPickerViewModel.onRecipeClick()` → `markUsed` (`:285`),
`onAddRecipeClick()` → `markUsed` (`:297`), `OnToggleFavorite` →
`recipeRepository.toggleFavorite` (`:147`). Die `onEach` in `loadExisting`
überschreibt danach unbedingt `category`, `portions`, `finalWeight`,
`isFavorite` und die komplette `ingredients`-Liste aus der DB-Zeile
(`:233-268`). Weil der Room-Write asynchron invalidiert, kann die Rückstellung
**nach** dem `AddAsIngredient`-Event landen → die gerade hinzugefügte Zuat
verschwindet wieder.

### Umsetzung

`RecipeEditorViewModel.loadExisting()`: den Collector auf eine einmalige
Seed-Operation umstellen —

```kotlin
recipeRepository
    .observeById(RecipeId(id))
    .take(1)
    .onEach { recipe -> /* bestehender State-Seed-Block, unverändert */ }
    .launchIn(viewModelScope)
```

Der bestehende `observeAll()`-Collector (`:81-98`) bleibt **unangetastet** — er
versorgt `availableRecipes`, `checkSnapshotChanges()` (Warnung „Unter-Rezept
geändert") und `recomputeNutrition()` weiterhin live.

**Nicht** zu tun: `checkSnapshotChanges()` nicht aus dem `loadExisting`-Collector
entfernen, sondern an den `observeAll()`-Collector hängen, damit der
Snapshot-Staleness-Hinweis nicht verloren geht.

### Betroffene Tests

`feature/recipes/presentation/src/test/java/.../RecipeEditorViewModelTest.kt`:

- Neu: `ingredient added via picker survives a recipes table emission` — Rezept
  seeden, `OnAddRecipeIngredientClick`/`OnAddIngredientClick`, danach eine
  `recipes`-Emission (`emitRecipes(...)`) simulieren (steht für `markUsed`).
  Erwartung: `state.ingredients` enthält den Draft weiterhin.
- Neu: `typed portions are not reset by a recipes table emission`.
- Bestehende Tests, die mehrfaches `emitRecipes` nutzen, gegenprüfen — das sind
  die Tests, die vorher vom ungewollten Überschreiben abhingen.

---

## P4.4 — History: echter Range-Flow

**Status:** erledigt — Commit `305e1c8`.

### Ziel

Der History-Screen muss nach Änderungen im Tagebuch von selbst aktualisieren.

### Root Cause

`HistoryViewModel.load()` (`:42-52`) ist **einmalig** (nur aus `init`) und ruft
`getHistory(range)` → `suspend` → `DiaryEntryDao.getByDateRange(...): List`
(noch ohne Flow). `History → Tag` pusht einen neuen Back-Stack-Eintrag
(`DiaryRoutes.kt:147`); Änderungen dort → zurück → der History-ViewModel lebt
noch, zeigt aber alte kcal-Summen und alte `entryCount`, neue Tage fehlen ganz.

### Umsetzung

Durchstich durch vier Layer, je eine Signatur + KDoc (keine zusätzliche
Fehlerbehandlung — dieselbe Form wie das existierende `observeByDate`, das
ebenfalls kein `Result` um den Flow wickelt):

1. `core/database/.../dao/DiaryEntryDao.kt` —
   `getByDateRange(from, to): List<DiaryEntryEntity>` →
   `observeByDateRange(from, to): Flow<List<DiaryEntryEntity>>`
   (Query bleibt `ORDER BY date, slot`).
2. `core/domain/.../datasource/DiaryLocalDataSource.kt` —
   `suspend getByDateRange(...): Result<...>` →
   `observeByDateRange(from, to): Flow<List<DiaryEntry>>`.
3. `core/domain/.../repository/DiaryRepository.kt` —
   `suspend getHistory(range): Result<...>` →
   `observeByDateRange(range: LocalDateRange): Flow<List<DiaryEntry>>`.
4. `core/domain/.../usecase/diary/GetHistoryUseCase.kt` —
   `suspend operator fun invoke(range): Result<...>` →
   `operator fun invoke(range): Flow<List<DiaryEntry>>`.

`core/data/.../diary/RoomDiaryLocalDataSource.kt`: die neue Methode analog zu
`observeByDate` implementieren (`dao.observeByDateRange(...).map { it.map(DiaryEntryEntity::toDiaryEntry) }`).
Der bisherige `runCatchingLocal`-Block für `getByDateRange` entfällt.

`feature/diary/.../history/HistoryViewModel.kt`:

```kotlin
init {
    val end = LocalDate.now()
    getHistory(LocalDateRange(from = end.minusDays(HISTORY_DAYS), to = end))
        .onEach { entries -> _state.update { it.copy(isLoading = false, days = group(entries)) } }
        .launchIn(viewModelScope)
}
```

`isLoading` bleibt als „noch keine Emission" erhalten (`HistoryState` initial
`isLoading = true`).

### Betroffene Tests

- `core/database/src/test/java/.../dao/DiaryEntryDaoTest.kt`: `observeByDateRange`
  liefert nur Einträge im Range, slot-geordnet.
- `core/data/src/test/java/.../diary/RoomDiaryRepositoryTest.kt`: Flow emittiert
  erneut nach `upsert`/`deleteById`.
- Neu `feature/diary/presentation/src/test/java/.../history/HistoryViewModelTest.kt`
  mit der vorhandenen `FakeDiaryRepository`
  (`feature/diary/presentation/src/test/java/.../fake/FakeDiaryRepository.kt`):
  `history re-emits when entries change`.

---

## P4.5 — `.first()`-Prefill in FoodEdit und Goals

**Status:** erledigt — Commit `a66f7a2`.

### Ziel

Kein `.first()`-Snapshot mehr im Produktionscode (aktuell genau zwei Stellen:
`FoodEditViewModel.kt:87` und `GoalsViewModel.kt:35`). Ein Editor darf
Fremdänderungen spiegeln, ohne die eigene Eingabe zu überschreiben.

### Umsetzung

**1. Neue Beobachtungs-API für ein einzelnes Food**

Analog zu `RecipeRepository.observeById`, drei Dateien + eine Zeile KDoc:

- `FoodItemDao`: `@Query("SELECT * FROM food_items WHERE id = :id") fun observeById(id: String): Flow<FoodItemEntity?>`
  (die bestehende `getById` als `suspend` **beibehalten** — `saveUserEdit` und
  `resetToOriginal` brauchen sie).
- `FoodLocalDataSource`: `fun observeById(id: FoodId): Flow<FoodItem?>`.
- `FoodRepository`: `fun observeById(id: FoodId): Flow<FoodItem?>` +
  `OfflineFirstFoodRepository`: Delegation.

Für session-only Items (nicht in Room) existiert weiterhin keine
Beobachtungs-API (`getSessionItem` liest eine `ConcurrentHashMap`). Der
FoodEditor fällt dort einmalig auf den Session-Cache zurück — für den
FoodPicker ist das über den `sessionFoods`-Map in P4.1 gelöst.

**2. `FoodEditViewModel.loadExisting()`** — `observeAll().first()` → `observeById`:

- Formularfelder (`name`, `barcode`, `kcal` … `portionGrams`) werden beim
  **ersten** Emit geseedet und danach nicht mehr überschrieben (Draft-Ownership).
  Umgesetzt als `isDirty`-Flag, das in allen `On*Change`-Actions gesetzt wird
  (dort wird bereits `savedStateHandle` geschrieben).
- `isFavorite` und `cachedStatus` werden bei **jeder** Emission reflektiert —
  dafür gibt es im Formular einen expliziten Stern-Button
  (`FoodEditScreen.kt:88`).

**3. `FoodEditViewModel` — Favorit nicht mehr über den Save-Payload
zurückschreiben**

`FoodEditAction.OnToggleFavorite` persistiert sofort
(`repository.toggleFavorite(FoodId(foodId))`) statt nur den lokalen State zu
flippen. Im `save()`-Pfad fliegt `isFavorite` für Existing-Foods **nicht** mehr
in `existingFood.copy(...)` (`:136`) — genau das ist der Datenverlust. Für neu
angelegte Foods (`foodId == null`) bleibt `isFavorite` im Draft.

**4. `FoodEditState`/`FoodEditScreen` (optional, eigener Commit)**

`cachedStatus: CachedFoodItemStatus?` ergänzen und das ADR-004-Badge auch im
Editor rendern (die AGENTS.md verlangt sichtbare Status-Kommunikation; heute
zeigt nur die Vollbild-Detailseite den Badge).

**5. `GoalsViewModel.init`** — `observeGoal().first()` → `observeGoal().onEach{…}.launchIn(viewModelScope)`
mit **identischem** `isDirty`-Muster wie P4.5.2, damit Tippen nicht
überschrieben wird. Heute praktisch folgenlos (Save/Reset navigieren zurück),
aber dieselbe Fehlerklasse.

### Betroffene Tests

- `feature/food/presentation/src/test/java/.../FoodEditViewModelTest.kt`:
  - `favorite toggled elsewhere is reflected in the form`
  - `typed nutrition is not overwritten by a later emission`
  - `OnToggleFavorite persists immediately`
  - `save does not overwrite the favorite flag for an existing food`
- `feature/food/presentation/src/main/java/.../fake/FakeFoodRepository.kt`:
  `observeById` ergänzen, `toggleFavorite` muss die emittierte Liste
  tatsächlich ändern.
- `feature/diary/presentation/src/test/java/.../goals/GoalsViewModelTest.kt`:
  - `a later goal emission fills a pristine form`
  - `typing is not clobbered by a goal emission`
- `core/data/src/test/java/.../food/OfflineFirstFoodRepositoryTest.kt`:
  `observeById` delegiert korrekt.

---

## Abschluss — Umsetzungsnotizen

Ausgeführt in der Reihenfolge P4.1 → P4.2 → P4.3 → P4.4 → P4.5, jedes Paket mit
Regressionstest zuerst rot und eigenem Commit. Gate nach jedem Paket:
`./gradlew build detekt ktlintCheck` (Stand P4.5: 326 Tests, 0 Fehler).

| Paket | Commit(s) |
|-------|-----------|
| P4.1 | `9e77166` |
| P4.2 | `36e66ca`, `386900b` |
| P4.3 | `88c4fc5` |
| P4.4 | `305e1c8` |
| P4.5 | `a66f7a2` |
| Nachtrag zu P4.5 (optional) | `4402234` |

### Abweichungen von der Planung (bewusst)

1. **`CopyYesterdayUseCase` war ein zweiter Consumer von `DiaryRepository.getHistory`
   (P4.4).** Der Plan nennt nur `HistoryViewModel`. Das UseCase nutzte denselben
   One-Shot-Read für einen Single-Tag-Range. Statt eine zweite Range-API
   (One-Shot *und* Flow) am Leben zu halten, liest es jetzt
   `observeByDate(sourceDate).first()` — für einen Einmal-Kopiervorgang genügt ein
   Snapshot, der Fehlerpfad bleibt über `save()` erhalten. Damit ist `getHistory`
   aus dem Interface entfernt.
2. **Draft-Ownership braucht ein persistiertes Dirty-Flag (P4.5).** `isDirty` allein
   wäre nach Process Death verloren: `SavedStateHandle` stellt `name`/`barcode`
   wieder her, der Flag-Zustand nicht — der Seed hätte den wiederhergestellten
   Draft überschrieben. Deshalb zusätzlich `KEY_DIRTY` im Handle, das in allen
   `On*Change`-Actions gesetzt wird (Initialwert über `hasRestoredDraft`).
3. **`FoodEditState.cachedStatus` kam mit P4.5** (server-owned, wird bei jeder
   Emission gespiegelt), das Badge-Rendering ist der optionale Folgecommit `4402234`
   (mit dem Badge wurde die Status-Projektion `CachedFoodItemStatus.toFoodEditStatusUi()`
   aus `toFoodUi` herausgelöst, damit Editor und Detailseite dieselbe Quelle nutzen).
4. **Testname-Korrektur in `RoomDiaryRepositoryTest`:** `getHistory returns entries
   within range newest first` behauptete eine absteigende Sortierung, die Query
   sortiert aber aufsteigend nach `date, slot` (der ViewModel sortiert selbst
   absteigend pro Tag). Umbenannt in `ordered by date and slot`.
5. **Detekt `TooManyFunctions` (Grenze 11) in `FoodEditViewModel`:** Die
   Merge-Logik liegt deshalb als file-private Extension
   `FoodEditState.mergedWith(food)` oberhalb der Klasse statt als private Methode.

### Bewusst offen (Backlog)

- Session-Cache als Flow (`observeSessionItem`) — der FoodPicker nutzt in P4.1
  weiterhin den `sessionFoods`-Map-Spiegel.
- `DiaryDayViewModel.date`-Startwert (Startwert aus dem `CurrentDateProvider`).
- Totes Feld `latestRecipe` in `RecipeDetailViewModel.kt:33` (kosmetisch).
- (erledigt in `4402234`) ADR-004-Badge im Food-Editor: `EditStatusBadge` ist von
  `FoodDetailScreen` in `presentation/components/` gewandert und wird jetzt auch im
  Editor aus `FoodEditState.cachedStatus` gerendert.

---

## Reihenfolge & Abschluss

1. P4.1 → P4.2 → P4.3 → P4.4 → P4.5, jeweils mit Regressionstest **zuerst rot**.
2. Nach jedem Paket: `./gradlew build` + `./gradlew detekt` + `./gradlew ktlintCheck`.
3. Jedes Paket einzeln committen lassen (`Commit P4.1` …), nie von selbst.
4. Am Ende Gesamtdurchlauf des lokalen Gates.

P4.1 und P4.2 teilen sich `FoodPickerViewModel.kt` und die neue `resolveFood()`;
sie bleiben trotzdem zwei Commits, damit der Hauptbug isoliert revertierbar ist.

---

## Abgrenzung / bewusst NICHT in diesem Plan

- **Keine** Änderung an Domain-Berechnungen (`RecipeCalculation`,
  `DayNutrition`, `RawWeight`/`FinalWeight`).
- **Keine** Umstellung des FoodPickers auf echte `FoodRepository.observeById`
  statt der bereits vorhandenen `observeAll()`-Liste (P4.1 reicht für die
  Panel-Projektion; die API aus P4.5 kommt erst danach).
- **Kein** Session-Cache als Flow (`observeSessionItem`) — der Map-Spiegel in
  `FoodPickerViewModel` reicht; die Session-Cache-Frage nach Process Death
  bleibt Backlog.
- **Keine** Änderung an `DiaryDayViewModel.date` (Startwert aus dem
  Koin-`CurrentDateProvider`-Singleton). Da die Bottom-Nav `saveState`/
  `restoreState` nutzt und `updateDate()` das Datum in den `SavedStateHandle`
  schreibt, ist das Verhalten "gemerktes Datum bleibt erhalten" beabsichtigt —
  erst bei konkretem Fehlreport nacharbeiten.
- **Keine** Bereinigung des toten Felds `latestRecipe` in
  `RecipeDetailViewModel.kt:33` (kosmetisch, eigener Commit).

---

# Agent-Prompts (pro Arbeitspaket)

## Prompt P4.1

> Du arbeitest im Android/Kotlin-Projekt EZMakro
> (`/home/deniz/AndroidStudioProjects/EZMakro`). Lade zuerst die Skills
> `android-presentation-mvi` und `android-testing` sowie
> `docs/Bugfix_Plan_Stale_Snapshots.md`, Abschnitt „P4.1".
>
> Kontext: Der FoodPicker (Lebensmittel-Tab) hält das Inline-Detail-Panel als
> eingefrorenen Snapshot in `state.detailFood`/`state.detailRecipe`
> (`feature/foodpicker/presentation/src/main/java/com/example/ezmakro/feature/foodpicker/presentation/picker/FoodPickerViewModel.kt:178-213`).
> Nach einer Änderung über Vollbild → Editor zeigt das weiterhin offene Panel
> alte Werte, weil das Feld nur von `OnDismissDetail` (`:109`) zurückgesetzt
> wird.
>
> Umsetzung:
> 1. Schreibe zuerst Regressionstests in
>    `feature/foodpicker/presentation/src/test/java/.../FoodPickerViewModelTest.kt`
>    (rot stellen): Panel zeigt Nährwertänderungen aus `emitFoods(...)` ohne
>    Neuanlage, Panel spiegelt `OnToggleFavorite`, Rezept-Panel analog über
>    `FakeRecipeRepository`, Panel schließt bei verschwundener Quelle.
>    Erweitere die lokalen Fakes so, dass `toggleFavorite`, `saveUserEdit` und
>    `cacheRemoteItem` die emittierte Liste wirklich mutieren.
> 2. `FoodPickerState.kt`: `detailFoodId: String?` / `detailRecipeId: String?`
>    ergänzen; `detailFood`/`detailRecipe` als read-only-Projektion
>    dokumentieren.
> 3. `FoodPickerViewModel.kt`: private Felder `localFoods`, `localRecipes`,
>    `sessionFoods`; `resolveFood(id)` (lokal → Session → Online) und
>    `refreshDetail()` exakt wie im Plan; `observeAllFoods`/
>    `observeAllRecipes` speisen die Rohdaten und rufen `refreshDetail()`;
>    `onFoodClick`/`onRecipeClick` schreiben nur noch die IDs; `rememberRemoteItem`
>    spiegelt zusätzlich in `sessionFoods`; `OnDismissDetail` löscht die IDs.
> 4. Bestehende Tests auf die neuen Felder nachziehen; Call-Sites von
>    `FoodPickerContent` (`FoodPickerSheet.kt`, `FoodSearchScreen.kt`,
>    `RecipeEditorScreen.kt`, `DiaryDayScreen.kt`) müssen unverändert
>    kompilieren.
> 5. Verifiziere: `./gradlew build detekt ktlintCheck`. Keine Commits.
>    Melde: geänderte Dateien, Testresultat, und ob der Test „Panel zeigt
>    Nährwertänderung ohne Neuanlage" vorher rot war.

## Prompt P4.2

> EZMakro (`/home/deniz/AndroidStudioProjects/EZMakro`), Skills
> `android-presentation-mvi` + `android-testing`. Plan:
> `docs/Bugfix_Plan_Stale_Snapshots.md`, Abschnitt „P4.2". Voraussetzung: P4.1
> ist committed, `resolveFood()` existiert in `FoodPickerViewModel`.
>
> Kontext: `onFoodClick` prüft `onlineFoodItems` **zuerst** (`:179-187`). Ein OFF-
> Treffer, der lokal gecacht und editiert wurde, zeigt dadurch die unveränderten
> OFF-Werte, während `MainActivity` beim Tagebuch-Eintrag die editierte Zeile
> nimmt. Zusätzlich fängt `confirmAddToDiary` (`:310-344`) `val s = _state.value`
> vor dem `launch` ab und entscheidet über den eingefrorenen Snapshot.
>
> Umsetzung:
> 1. Tests zuerst rot: `onFoodClick prefers the locally edited food over the
>    online snapshot`, `onFoodClick on a session-only item shows the snapshot`,
>    `confirmAddToDiary uses the resolved item`.
> 2. `onFoodClick`: Auflösung auf `resolveFood(id)` umstellen;
>    `rememberRemoteItem` nur noch aufrufen, wenn das Item weder lokal noch in
>    der Session vorhanden ist.
> 3. `confirmAddToDiary`: `cacheRemoteItem`-Entscheidung und ID aus dem
>    aufgelösten Item ableiten, nicht aus dem eingefrorenen `onlineFoodItems`.
> 4. `app/src/main/java/com/example/ezmakro/MainActivity.kt`,
>    `makeAddToDiaryFromPicker` (`:189-220`): an `makeAddFoodToDiary` angleichen —
>    `getSessionItem`-Fallback, `cacheRemoteItem` vor `addUseCase`, und den
>    `popBackStack` nicht mehr bei stillem `null` auslösen.
> 5. Verifiziere: `./gradlew build detekt ktlintCheck`. Keine Commits.
>    Melde: geänderte Dateien + Testresultat.

## Prompt P4.3

> EZMakro (`/home/deniz/AndroidStudioProjects/EZMakro`), Skills
> `android-presentation-mvi` + `android-testing`. Plan:
> `docs/Bugfix_Plan_Stale_Snapshots.md`, Abschnitt „P4.3".
>
> Kontext: `RecipeEditorViewModel.loadExisting()`
> (`feature/recipes/presentation/src/main/java/com/example/ezmakro/feature/recipes/presentation/editor/RecipeEditorViewModel.kt:228-272`)
> sammelt `recipeRepository.observeById()` dauerhaft. Da dieses
> `dao.observeAll().map{…}` ist (`core/data/.../recipe/RoomRecipeLocalDataSource.kt:31-34`),
> feuert es bei **jedem** Write auf `recipes` — auch bei `markUsed` aus dem
> Zutaten-Picker (`FoodPickerViewModel.kt:285,297`). Der `onEach` überschreibt
> dann unbedingt `category`, `portions`, `finalWeight`, `isFavorite` und die
> komplette `ingredients`-Liste: hinzugefügte Zutaten verschwinden, getippte
> Portionen werden zurückgesetzt.
>
> Umsetzung:
> 1. Test zuerst rot in
>    `feature/recipes/presentation/src/test/java/.../RecipeEditorViewModelTest.kt`:
>    Zutat hinzufügen, danach eine `recipes`-Emission (steht für `markUsed`),
>    dann prüfen, dass `state.ingredients` den Draft noch enthält und
>    `portions` nicht zurückgesetzt wurde.
> 2. `loadExisting()`: Collector auf `.take(1)` umstellen, Seed-Block
>    unverändert lassen.
> 3. Sicherstellen, dass `checkSnapshotChanges()` und `recomputeNutrition()`
>    weiterhin vom separaten `recipeRepository.observeAll()`-Collector (`:81-98`)
>    versorgt werden — die Warnung „Unter-Rezept geändert" darf nicht
>    verloren gehen.
> 4. Bestehende Tests prüfen, die mehrfaches `emitRecipes` nutzen (sie
>    könnten vom alten Überschreib-Verhalten abhingen) und ggf. korrigieren.
> 5. Verifiziere: `./gradlew build detekt ktlintCheck`. Keine Commits.
>    Melde: geänderte Dateien + Testresultat.

## Prompt P4.4

> EZMakro (`/home/deniz/AndroidStudioProjects/EZMakro`), Skills
> `android-data-layer`, `android-presentation-mvi`, `android-testing`. Plan:
> `docs/Bugfix_Plan_Stale_Snapshots.md`, Abschnitt „P4.4".
>
> Kontext: `HistoryViewModel.load()`
> (`feature/diary/presentation/src/main/java/com/example/ezmakro/feature/diary/presentation/history/HistoryViewModel.kt:42-52`)
> läuft einmalig in `init` und nutzt eine `suspend`-Query. Nach Änderungen im
> Tagebuch (History → Tag pusht einen eigenen Back-Stack-Eintrag) zeigt der
> History-Screen beim Zurückkommen veraltete kcal-Summen und `entryCount`.
>
> Umsetzung (Durchstich durch vier Layer, je eine Signatur + Kdoc, Form analog
> zum existierenden `observeByDate`):
> 1. `core/database/src/main/java/com/example/ezmakro/core/database/dao/DiaryEntryDao.kt`:
>    `getByDateRange(...): List<DiaryEntryEntity>` → `observeByDateRange(...): Flow<List<DiaryEntryEntity>>`
>    (Query `ORDER BY date, slot` bleibt).
> 2. `core/domain/.../datasource/DiaryLocalDataSource.kt`: `suspend getByDateRange(...): Result<...>`
>    → `observeByDateRange(from, to): Flow<List<DiaryEntry>>`.
> 3. `core/domain/.../repository/DiaryRepository.kt`: `suspend getHistory(range): Result<...>`
>    → `observeByDateRange(range): Flow<List<DiaryEntry>>`.
> 4. `core/domain/.../usecase/diary/GetHistoryUseCase.kt`: `suspend operator fun invoke(range): Result<...>`
>    → `operator fun invoke(range): Flow<List<DiaryEntry>>`.
> 5. `core/data/.../diary/RoomDiaryLocalDataSource.kt`: neue Methode analog zu
>    `observeByDate` implementieren, den `runCatchingLocal`-Block entfernen.
>    `RoomDiaryRepository` delegieren.
> 6. `HistoryViewModel`: `init` sammelt den Flow, `isLoading` bedeutet „noch
>    keine Emission"; `load()` entfernen (kein Aufrufer außer `init`).
> 7. Tests: `DiaryEntryDaoTest` (nur Range, slot-geordnet),
>    `RoomDiaryRepositoryTest` (re-emittiert nach `upsert`/`deleteById`), neu
>    `feature/diary/presentation/src/test/java/.../history/HistoryViewModelTest.kt`
>    mit der vorhandenen `FakeDiaryRepository`.
> 8. Verifiziere: `./gradlew build detekt ktlintCheck`. Keine Commits.
>    Melde: geänderte Dateien + Testresultat.

## Prompt P4.5

> EZMakro (`/home/deniz/AndroidStudioProjects/EZMakro`), Skills
> `android-data-layer`, `android-presentation-mvi`, `android-testing`. Plan:
> `docs/Bugfix_Plan_Stale_Snapshots.md`, Abschnitt „P4.5". Voraussetzung: P4.1–P4.4
> sind committed.
>
> Kontext: Zwei verbleibende `.first()`-Stellen im Produktionscode.
> `FoodEditViewModel.loadExisting()` (`:83-104`) liest das Formular einmalig;
> `save()` schreibt `isFavorite` aus dem veralteten Draft (`:127,136`) zurück und
> überschreibt damit einen zwischenzeitlich auf der Detailseite gesetzten
> Favoriten. `GoalsViewModel.init` (`:33-46`) nutzt ebenfalls `.first()`.
>
> Umsetzung:
> 1. Neue Beobachtungs-API (analog `RecipeRepository.observeById`):
>    `FoodItemDao.observeById(id): Flow<FoodItemEntity?>` (die suspend-`getById`
>    behalten — `saveUserEdit`/`resetToOriginal` brauchen sie),
>    `FoodLocalDataSource.observeById`, `FoodRepository.observeById`,
>    Delegation in `OfflineFirstFoodRepository`.
> 2. Tests zuerst rot: `FavoriteEditViewModelTest` — extern gesetzter Favorit
>    erscheint im Formular; getippte Nährwerte werden von späteren Emissions
>    nicht überschrieben; `OnToggleFavorite` persistiert sofort; `save`
>    überschreibt den Favoriten bei Existing-Foods nicht.
> 3. `FoodEditViewModel.loadExisting()` → `observeById(...).onEach`: Formularfelder
>    einmalig seeden und danach nicht mehr überschreiben (`isDirty`-Flag, in allen
>    `On*Change`-Actions gesetzt); `isFavorite` und `cachedStatus` bei jeder
>    Emission reflektieren.
> 4. `FoodEditAction.OnToggleFavorite` persistiert sofort via
>    `repository.toggleFavorite`; im `save()`-Pfad `isFavorite` für Existing-Foods
>    aus `existingFood.copy(...)` entfernen.
> 5. `GoalsViewModel.init` → `observeGoal().onEach{…}.launchIn(viewModelScope)` mit
>    demselben `isDirty`-Muster.
> 6. `feature/food/presentation/src/main/java/.../fake/FakeFoodRepository.kt`:
>    `observeById` ergänzen, `toggleFavorite`/`saveUserEdit` die emittierte Liste
>    wirklich mutieren lassen.
> 7. Verifiziere: `./gradlew build detekt ktlintCheck`. Keine Commits.
>    Melde: geänderte Dateien + Testresultat.
>
> Optional als eigener Commit danach (nicht in diesem Prompt): ADR-004-Badge im
> Food-Editor ergänzen (`FoodEditState.cachedStatus` + `FoodEditScreen`).