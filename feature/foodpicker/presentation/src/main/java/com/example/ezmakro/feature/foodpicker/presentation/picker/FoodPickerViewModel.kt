package com.example.ezmakro.feature.foodpicker.presentation.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.calculation.createRecipeSnapshot
import com.example.ezmakro.core.domain.calculation.gramsPerPortion
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.presentation.recipe.RECIPE_SERVING_LABEL
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.core.presentation.uitext.toUiText
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.core.presentation.util.servingAmountLabel
import com.example.ezmakro.feature.foodpicker.presentation.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@Suppress("TooManyFunctions")
class FoodPickerViewModel(
    target: FoodPickerTarget,
    targetDate: String?,
    private val foodRepository: FoodRepository,
    private val recipeRepository: RecipeRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val savedTab =
        savedStateHandle
            .get<String>(KEY_TAB)
            ?.let { raw ->
                if (raw == "MY_FOODS") {
                    FoodPickerTab.FAVORITES
                } else {
                    runCatching { FoodPickerTab.valueOf(raw) }.getOrNull()
                }
            }

    private val _state =
        MutableStateFlow(
            FoodPickerState(
                activeTab = savedTab ?: FoodPickerTab.SEARCH,
                searchQuery = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
                lastDiaryAmount = savedStateHandle.get<String>(KEY_LAST_DIARY_AMOUNT),
                lastDiaryIsGrams = savedStateHandle.get<Boolean>(KEY_LAST_DIARY_IS_GRAMS) ?: true,
                slot = savedStateHandle.get<String>(KEY_LAST_DIARY_SLOT)?.let(MealSlot::valueOf),
                lastIngredientRawWeight = savedStateHandle.get<String>(KEY_LAST_INGREDIENT_RAW_WEIGHT),
                lastIngredientServingCount = savedStateHandle.get<String>(KEY_LAST_INGREDIENT_SERVING_COUNT),
                targetContext = target,
                targetDate = targetDate,
            ),
        )
    val state = _state.asStateFlow()

    private val _events = Channel<FoodPickerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val queryFlow = MutableStateFlow(_state.value.searchQuery)

    private val handledBarcodeAt = HashMap<String, Long>()

    /** Raw rows behind the observed lists; the single resolution source for the open detail panel. */
    private var localFoods: List<FoodItem> = emptyList()

    private var localRecipes: List<Recipe> = emptyList()

    /** Remote items remembered for this session only (never persisted), mirrored from `rememberRemoteItem`. */
    private val sessionFoods = mutableMapOf<String, FoodItem>()

    init {
        observeRecent()
        observeRecentRecipes()
        observeAllFoods()
        observeAllRecipes()
        observeLocalResults()
        observeLocalRecipeResults()
        observeFavoriteFoods()
        observeFavoriteRecipes()
    }

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: FoodPickerAction) {
        when (action) {
            is FoodPickerAction.OnTabSelected -> {
                savedStateHandle[KEY_TAB] = action.tab.name
                _state.update { it.copy(activeTab = action.tab) }
            }

            is FoodPickerAction.OnSearchQueryChange -> onSearchQueryChange(action.query)

            FoodPickerAction.OnSearchOnlineClick -> searchOnline()

            is FoodPickerAction.OnFoodClick -> onFoodClick(action.foodId)

            is FoodPickerAction.OnRecipeClick -> onRecipeClick(action.recipeId)

            is FoodPickerAction.OnAddClick -> onAddClick(action.foodId)

            is FoodPickerAction.OnAddRecipeClick -> onAddRecipeClick(action.recipeId)

            FoodPickerAction.OnDismissSelection ->
                _state.update {
                    it.copy(
                        selectedItemReady = false,
                        selectedItemId = null,
                        selectedItemIsRecipe = false,
                        selectedItemHasServingUnit = false,
                        selectedItemGramsPerPortion = null,
                        selectedItemGramsPerPortionEstimated = false,
                        pendingIngredient = null,
                    )
                }

            FoodPickerAction.OnSheetDismissed ->
                _state.update { it.copy(addedItemIds = emptySet()) }

            FoodPickerAction.OnDismissDetail -> dismissDetail()

            FoodPickerAction.OnDetailAddClick -> onDetailAddClick()

            FoodPickerAction.OnDetailFullscreenClick -> openDetailFullscreen()

            is FoodPickerAction.OnConfirmAddToDiary -> confirmAddToDiary(action)

            is FoodPickerAction.OnConfirmAddAsIngredient -> confirmAddAsIngredient(action)

            FoodPickerAction.OnMyFoodsToggle ->
                _state.update { it.copy(showFoods = !it.showFoods) }

            is FoodPickerAction.OnBarcodeDetected -> lookupBarcode(action.barcode)

            is FoodPickerAction.OnPermissionResult ->
                _state.update { it.copy(isCameraPermissionGranted = action.granted) }

            FoodPickerAction.OnScanBarcodeClick ->
                _state.update { it.copy(activeTab = FoodPickerTab.BARCODE) }

            FoodPickerAction.OnCreateOwnClick ->
                viewModelScope.launch { _events.send(FoodPickerEvent.NavigateToFoodCreate(null)) }

            FoodPickerAction.OnQuickAddClick -> onQuickAddClick()

            is FoodPickerAction.OnSlotSet ->
                _state.update { it.copy(slot = action.slot) }

            is FoodPickerAction.OnDateSet ->
                _state.update { it.copy(targetDate = action.date) }

            is FoodPickerAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    val inUserRecipes = _state.value.userRecipes.any { it.id == action.foodId }
                    val inFavoriteRecipes = _state.value.favoriteRecipes.any { it.id == action.foodId }
                    if (inUserRecipes || inFavoriteRecipes) {
                        recipeRepository.toggleFavorite(RecipeId(action.foodId))
                    } else {
                        foodRepository.toggleFavorite(FoodId(action.foodId))
                    }
                }
            }
        }
    }

    /** Close the inline detail panel: the ids are the source of truth, the projections go with them. */
    private fun dismissDetail() {
        _state.update {
            it.copy(
                detailFoodId = null,
                detailRecipeId = null,
                detailFood = null,
                detailRecipe = null,
            )
        }
    }

    private fun onSearchQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
        queryFlow.value = query
        _state.update {
            it.copy(
                searchQuery = query,
                onlineError = null,
                selectionError = null,
                onlineSearchResults = emptyList(),
                onlineFoodItems = emptyList(),
            )
        }
    }

    private fun onQuickAddClick() {
        val s = _state.value
        val date = s.targetDate ?: return
        viewModelScope.launch {
            _events.send(FoodPickerEvent.NavigateToQuickAdd(date, s.slot ?: MealSlot.BREAKFAST))
        }
    }

    private fun onFoodClick(foodId: String) {
        val isLocal = localFoods.any { it.id.value == foodId }
        val isRemoteOnly = isRemoteOnlyFood(foodId)
        val resolved = resolveFood(foodId)
        _state.update { it.copy(selectionError = null, detailFoodId = foodId, detailRecipeId = null) }
        viewModelScope.launch {
            when {
                // A locally stored row is authoritative (it may be user-modified), only bump its usage.
                isLocal -> foodRepository.markUsed(FoodId(foodId))
                // A pure search hit has to be remembered so its id stays resolvable off the search result.
                isRemoteOnly && resolved != null -> {
                    sessionFoods[foodId] = resolved
                    foodRepository.rememberRemoteItem(resolved)
                }
                // Session-only: already remembered, nothing local to update and nothing to persist.
                else -> Unit
            }
            refreshDetail()
        }
    }

    private fun onRecipeClick(recipeId: String) {
        viewModelScope.launch {
            recipeRepository.markUsed(RecipeId(recipeId))
            _state.update { it.copy(detailRecipeId = recipeId, detailFoodId = null) }
            refreshDetail()
        }
    }

    /**
     * Resolve a food id against every source the picker knows about, local first:
     * a locally edited row must win over the frozen offline-search snapshot.
     */
    private fun resolveFood(id: String): FoodItem? =
        localFoods.firstOrNull { it.id.value == id }
            ?: sessionFoods[id]
            ?: _state.value.onlineFoodItems.firstOrNull { it.id.value == id }

    /**
     * True while [id] exists only as a frozen search snapshot: neither a stored
     * row nor a session entry. Such an item is not durable and must be cached
     * before the diary can reference it.
     */
    private fun isRemoteOnlyFood(id: String): Boolean =
        localFoods.none { it.id.value == id } && !sessionFoods.containsKey(id)

    private fun resolveRecipe(id: String): Recipe? = localRecipes.firstOrNull { it.id.value == id }

    /**
     * Re-project the open inline detail from the currently observed data, so an
     * edit made elsewhere (fullscreen detail → editor, favorite toggle, reset to
     * original, OFF refetch) is visible without closing and reopening the panel.
     * A source item that vanished closes the panel by projecting `null`.
     */
    private fun refreshDetail() {
        val s = _state.value
        if (s.detailFoodId == null && s.detailRecipeId == null) return
        _state.update {
            it.copy(
                detailFood = it.detailFoodId?.let(::resolveFood),
                detailRecipe = it.detailRecipeId?.let(::resolveRecipe),
            )
        }
    }

    private fun onDetailAddClick() {
        val s = _state.value
        when {
            s.detailFood != null -> onAddClick(s.detailFood.id.value)
            s.detailRecipe != null -> onAddRecipeClick(s.detailRecipe.id.value)
        }
    }

    private fun openDetailFullscreen() {
        val s = _state.value
        when {
            s.detailFood != null ->
                viewModelScope.launch {
                    _events.send(FoodPickerEvent.NavigateToFoodDetail(s.detailFood.id.value))
                }
            s.detailRecipe != null ->
                viewModelScope.launch {
                    _events.send(FoodPickerEvent.NavigateToRecipeDetail(s.detailRecipe.id.value))
                }
        }
    }

    private fun onAddClick(foodId: String) {
        if (_state.value.targetContext == FoodPickerTarget.RECIPE_INGREDIENT) {
            val food = findFood(foodId) ?: return
            val onlineItem = _state.value.onlineFoodItems.firstOrNull { it.id.value == foodId }
            viewModelScope.launch {
                if (onlineItem != null) {
                    when (val result = foodRepository.cacheRemoteItem(onlineItem)) {
                        is Result.Error -> {
                            _state.update { it.copy(selectionError = result.error.toUiText()) }
                            return@launch
                        }
                        is Result.Success -> { /* proceed */ }
                    }
                } else {
                    foodRepository.markUsed(FoodId(foodId))
                }
                _state.update {
                    it.copy(
                        selectionError = null,
                        pendingIngredient =
                            PendingIngredient(
                                itemId = foodId,
                                itemName = food.name,
                                servingLabel = food.servingLabel,
                                servingAmount = resolveFood(foodId)?.servingUnit?.servingAmountLabel(),
                                isRecipe = false,
                            ),
                    )
                }
            }
            return
        }
        val onlineItem = _state.value.onlineFoodItems.firstOrNull { it.id.value == foodId }
        if (onlineItem == null) {
            viewModelScope.launch { foodRepository.markUsed(FoodId(foodId)) }
        }
        val food = findFood(foodId)
        _state.update {
            it.copy(
                selectionError = null,
                selectedItemReady = true,
                selectedItemId = foodId,
                selectedItemIsRecipe = false,
                selectedItemHasServingUnit = food?.servingLabel != null,
                selectedItemGramsPerPortion = resolveFood(foodId)?.servingUnit?.gramsEquivalent,
                selectedItemGramsPerPortionEstimated = false,
            )
        }
    }

    private fun onAddRecipeClick(recipeId: String) {
        if (_state.value.targetContext == FoodPickerTarget.RECIPE_INGREDIENT) {
            val recipe = findRecipe(recipeId) ?: return
            viewModelScope.launch {
                recipeRepository.markUsed(RecipeId(recipeId))
                val gramsPerPortion =
                    resolveRecipe(recipeId)
                        ?.let { createRecipeSnapshot(it).gramsPerPortion }
                        ?.takeIf { it > 0.0 }
                _state.update {
                    it.copy(
                        pendingIngredient =
                            PendingIngredient(
                                itemId = recipeId,
                                itemName = recipe.name,
                                servingLabel = RECIPE_SERVING_LABEL,
                                servingAmount = gramsPerPortion?.let { g -> "${g.formatValue()} g" },
                                isRecipe = true,
                            ),
                    )
                }
            }
            return
        }
        viewModelScope.launch {
            recipeRepository.markUsed(RecipeId(recipeId))
        }
        _state.update {
            val recipe = resolveRecipe(recipeId)
            it.copy(
                selectedItemReady = true,
                selectedItemId = recipeId,
                selectedItemIsRecipe = true,
                selectedItemHasServingUnit = true,
                selectedItemGramsPerPortion = recipe?.gramsPerPortion()?.takeIf { grams -> grams > 0.0 },
                selectedItemGramsPerPortionEstimated = recipe?.finalWeight == null,
            )
        }
    }

    private fun confirmAddToDiary(action: FoodPickerAction.OnConfirmAddToDiary) {
        val s = _state.value
        val selectedId = s.selectedItemId ?: return
        val selectedName = findFood(selectedId)?.name ?: selectedId
        val date = s.targetDate ?: LocalDate.now().toString()
        val isRecipe = s.selectedItemIsRecipe
        val slot = action.slot
        savedStateHandle[KEY_LAST_DIARY_AMOUNT] = action.amount.toString()
        savedStateHandle[KEY_LAST_DIARY_IS_GRAMS] = action.isGrams
        savedStateHandle[KEY_LAST_DIARY_SLOT] = slot.name
        dismissDetail()
        _state.update {
            it.copy(
                selectedItemReady = false,
                selectedItemId = null,
                selectedItemIsRecipe = false,
                selectedItemHasServingUnit = false,
                selectedItemGramsPerPortion = null,
                selectedItemGramsPerPortionEstimated = false,
            )
        }
        viewModelScope.launch {
            // Resolve at use time, never from the snapshot captured before this launch:
            // a locally edited row must win over the online search result.
            val resolved = if (isRecipe) null else resolveFood(selectedId)
            // Only a not-yet-persisted remote hit needs caching; a stored row is
            // already durable and must not be overwritten (ADR-004).
            if (isRemoteOnlyFood(selectedId) && resolved != null) {
                when (val result = foodRepository.cacheRemoteItem(resolved)) {
                    is Result.Error -> {
                        _state.update { it.copy(selectionError = result.error.toUiText()) }
                        return@launch
                    }
                    is Result.Success -> { /* proceed */ }
                }
            }
            _state.update {
                it.copy(
                    slot = slot,
                    addedItemIds = it.addedItemIds + selectedId,
                )
            }
            _events.send(
                FoodPickerEvent.AddToDiary(
                    foodId = resolved?.id?.value ?: selectedId,
                    isRecipe = isRecipe,
                    date = date,
                    slot = slot,
                    amount = action.amount,
                    isGrams = action.isGrams,
                ),
            )
            _events.send(
                FoodPickerEvent.ShowMessage(
                    UiText.StringResource(R.string.foodpicker_added_message, arrayOf<Any>(selectedName)),
                ),
            )
        }
    }

    private fun confirmAddAsIngredient(action: FoodPickerAction.OnConfirmAddAsIngredient) {
        val pending = _state.value.pendingIngredient ?: return
        savedStateHandle[KEY_LAST_INGREDIENT_RAW_WEIGHT] = action.rawWeight
        savedStateHandle[KEY_LAST_INGREDIENT_SERVING_COUNT] = action.servingCount
        dismissDetail()
        _state.update {
            it.copy(
                pendingIngredient = null,
                addedItemIds = it.addedItemIds + pending.itemId,
            )
        }
        viewModelScope.launch {
            _events.send(
                FoodPickerEvent.AddAsIngredient(
                    itemId = pending.itemId,
                    itemName = pending.itemName,
                    servingLabel = pending.servingLabel,
                    isRecipe = pending.isRecipe,
                    rawWeight = action.rawWeight,
                    servingCount = action.servingCount,
                ),
            )
            _events.send(
                FoodPickerEvent.ShowMessage(
                    UiText.StringResource(R.string.foodpicker_added_message, arrayOf<Any>(pending.itemName)),
                ),
            )
        }
    }

    private fun lookupBarcode(barcode: String) {
        if (_state.value.isLookingUpBarcode) return
        val now = System.currentTimeMillis()
        val lastHandledAt = handledBarcodeAt[barcode] ?: 0L
        if (now - lastHandledAt < BARCODE_SETTLE_WINDOW_MS) return
        viewModelScope.launch {
            _state.update { it.copy(isLookingUpBarcode = true, barcodeError = null) }
            when (val result = foodRepository.lookupByBarcode(barcode)) {
                is Result.Success -> {
                    _state.update { it.copy(isLookingUpBarcode = false) }
                    handledBarcodeAt[barcode] = now
                    val food = result.data
                    when (_state.value.targetContext) {
                        FoodPickerTarget.DIARY -> {
                            if (food != null) {
                                _events.send(FoodPickerEvent.NavigateToFoodDetail(food.id.value))
                            } else {
                                _events.send(FoodPickerEvent.NavigateToFoodCreate(barcode))
                            }
                        }

                        FoodPickerTarget.BROWSE -> {
                            if (food != null) {
                                _events.send(FoodPickerEvent.NavigateToFoodDetail(food.id.value))
                            } else {
                                _events.send(FoodPickerEvent.NavigateToFoodCreate(barcode))
                            }
                        }

                        FoodPickerTarget.RECIPE_INGREDIENT -> {
                            if (food != null) {
                                _state.update {
                                    it.copy(
                                        pendingIngredient =
                                            PendingIngredient(
                                                itemId = food.id.value,
                                                itemName = food.name,
                                                servingLabel = food.servingUnit?.label,
                                                servingAmount = food.servingUnit?.servingAmountLabel(),
                                                isRecipe = false,
                                            ),
                                    )
                                }
                            } else {
                                _events.send(FoodPickerEvent.NavigateToFoodCreate(barcode))
                            }
                        }
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(isLookingUpBarcode = false, barcodeError = result.error.toUiText())
                    }
                }
            }
        }
    }

    private fun observeRecent() {
        foodRepository
            .observeRecent()
            .onEach { foods -> _state.update { it.copy(recentFoods = foods.map { f -> f.toPickerFoodUi() }) } }
            .launchIn(viewModelScope)
    }

    private fun observeAllFoods() {
        foodRepository
            .observeAll()
            .onEach { foods ->
                localFoods = foods
                _state.update { it.copy(userFoods = foods.map { f -> f.toPickerFoodUi() }) }
                refreshDetail()
            }.launchIn(viewModelScope)
    }

    private fun observeAllRecipes() {
        recipeRepository
            .observeAll()
            .onEach { recipes ->
                localRecipes = recipes
                _state.update { it.copy(userRecipes = recipes.map { r -> r.toPickerRecipeUi() }) }
                refreshDetail()
            }.launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLocalResults() {
        queryFlow
            .flatMapLatest { query -> foodRepository.searchLocal(query) }
            .onEach { foods ->
                _state.update { it.copy(localSearchResults = foods.map { f -> f.toPickerFoodUi() }) }
            }.launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLocalRecipeResults() {
        queryFlow
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf<List<PickerRecipeUi>>(emptyList())
                } else {
                    recipeRepository.observeAll().map { recipes: List<Recipe> ->
                        recipes
                            .filter { it.name.contains(query, ignoreCase = true) }
                            .map { it.toPickerRecipeUi() }
                    }
                }
            }.onEach { recipes -> _state.update { it.copy(localRecipeResults = recipes) } }
            .launchIn(viewModelScope)
    }

    private fun observeFavoriteFoods() {
        foodRepository
            .observeFavorites()
            .onEach { foods ->
                _state.update { it.copy(favoriteFoods = foods.map { f -> f.toPickerFoodUi() }) }
            }.launchIn(viewModelScope)
    }

    private fun observeFavoriteRecipes() {
        recipeRepository
            .observeFavorites()
            .onEach { recipes ->
                _state.update { it.copy(favoriteRecipes = recipes.map { r -> r.toPickerRecipeUi() }) }
            }.launchIn(viewModelScope)
    }

    private fun observeRecentRecipes() {
        recipeRepository
            .observeRecent()
            .onEach { recipes ->
                _state.update { it.copy(recentRecipes = recipes.map { r -> r.toPickerRecipeUi() }) }
            }.launchIn(viewModelScope)
    }

    private fun searchOnline() {
        val query = _state.value.searchQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update {
                it.copy(isSearchingOnline = true, onlineError = null, selectionError = null)
            }
            foodRepository
                .searchRemote(query)
                .onSuccess { foods ->
                    val deduped = foods.distinctBy { it.id }
                    _state.update {
                        it.copy(
                            isSearchingOnline = false,
                            onlineSearchResults = deduped.map { f -> f.toPickerFoodUi() },
                            onlineFoodItems = deduped,
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(isSearchingOnline = false, onlineError = error.toUiText())
                    }
                }
        }
    }

    private fun findFood(foodId: String): PickerFoodUi? {
        val s = _state.value
        return s.recentFoods.firstOrNull { it.id == foodId }
            ?: s.userFoods.firstOrNull { it.id == foodId }
            ?: s.localSearchResults.firstOrNull { it.id == foodId }
            ?: s.onlineSearchResults.firstOrNull { it.id == foodId }
    }

    private fun findRecipe(recipeId: String): PickerRecipeUi? =
        _state.value.userRecipes.firstOrNull { it.id == recipeId }

    companion object {
        private const val KEY_TAB = "foodpicker_tab"
        private const val KEY_QUERY = "foodpicker_query"
        private const val KEY_LAST_DIARY_AMOUNT = "foodpicker_last_diary_amount"
        private const val KEY_LAST_DIARY_IS_GRAMS = "foodpicker_last_diary_is_grams"
        private const val KEY_LAST_DIARY_SLOT = "foodpicker_last_diary_slot"
        private const val KEY_LAST_INGREDIENT_RAW_WEIGHT = "foodpicker_last_ingredient_raw_weight"
        private const val KEY_LAST_INGREDIENT_SERVING_COUNT = "foodpicker_last_ingredient_serving_count"

        /** Max age of a handled barcode-scan result that still suppresses a re-detected scan. */
        private const val BARCODE_SETTLE_WINDOW_MS = 3_000L
    }
}
