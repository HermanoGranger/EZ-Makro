package com.example.ezmakro.feature.foodpicker.presentation.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.presentation.uitext.toUiText
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
                targetContext = target,
                targetDate = targetDate,
            ),
        )
    val state = _state.asStateFlow()

    private val _events = Channel<FoodPickerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val queryFlow = MutableStateFlow(_state.value.searchQuery)

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

    fun onAction(action: FoodPickerAction) {
        when (action) {
            is FoodPickerAction.OnTabSelected -> {
                savedStateHandle[KEY_TAB] = action.tab.name
                _state.update { it.copy(activeTab = action.tab) }
            }

            is FoodPickerAction.OnSearchQueryChange -> {
                savedStateHandle[KEY_QUERY] = action.query
                queryFlow.value = action.query
                _state.update {
                    it.copy(searchQuery = action.query, onlineError = null, selectionError = null)
                }
            }

            FoodPickerAction.OnSearchOnlineClick -> searchOnline()

            is FoodPickerAction.OnFoodClick -> onFoodClick(action.foodId)

            is FoodPickerAction.OnRecipeClick -> onRecipeClick(action.recipeId)

            FoodPickerAction.OnDismissSelection ->
                _state.update {
                    it.copy(
                        selectedItemReady = false,
                        selectedItemId = null,
                        selectedItemIsRecipe = false,
                        selectedItemHasServingUnit = false,
                    )
                }

            is FoodPickerAction.OnConfirmAddToDiary -> confirmAddToDiary(action)

            FoodPickerAction.OnMyFoodsToggle ->
                _state.update { it.copy(showFoods = !it.showFoods) }

            is FoodPickerAction.OnBarcodeDetected -> lookupBarcode(action.barcode)

            is FoodPickerAction.OnPermissionResult ->
                _state.update { it.copy(isCameraPermissionGranted = action.granted) }

            FoodPickerAction.OnDismiss ->
                viewModelScope.launch { _events.send(FoodPickerEvent.Dismissed) }

            FoodPickerAction.OnScanBarcodeClick ->
                _state.update { it.copy(activeTab = FoodPickerTab.BARCODE) }

            FoodPickerAction.OnCreateOwnClick ->
                viewModelScope.launch { _events.send(FoodPickerEvent.NavigateToFoodCreate(null)) }

            FoodPickerAction.OnQuickAddClick -> onQuickAddClick()

            is FoodPickerAction.OnSlotSet ->
                _state.update { it.copy(slot = action.slot) }

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

    private fun onQuickAddClick() {
        val s = _state.value
        val date = s.targetDate ?: return
        viewModelScope.launch {
            _events.send(FoodPickerEvent.NavigateToQuickAdd(date, s.slot ?: MealSlot.BREAKFAST))
        }
    }

    private fun onFoodClick(foodId: String) {
        val onlineItem = _state.value.onlineFoodItems.firstOrNull { it.id.value == foodId }

        if (_state.value.targetContext == FoodPickerTarget.RECIPE_INGREDIENT) {
            val food = findFood(foodId) ?: return
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
                _state.update { it.copy(selectionError = null) }
                _events.send(
                    FoodPickerEvent.AddAsIngredient(
                        itemId = foodId,
                        itemName = food.name,
                        servingLabel = food.servingLabel,
                        isRecipe = false,
                    ),
                )
            }
        } else if (_state.value.targetContext == FoodPickerTarget.BROWSE) {
            if (onlineItem == null) {
                viewModelScope.launch { foodRepository.markUsed(FoodId(foodId)) }
            }
            viewModelScope.launch {
                _events.send(FoodPickerEvent.NavigateToFoodDetail(foodId))
            }
        } else {
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
                )
            }
        }
    }

    private fun onRecipeClick(recipeId: String) {
        viewModelScope.launch {
            recipeRepository.markUsed(RecipeId(recipeId))
        }

        if (_state.value.targetContext == FoodPickerTarget.RECIPE_INGREDIENT) {
            val recipe = findRecipe(recipeId) ?: return
            viewModelScope.launch {
                _events.send(
                    FoodPickerEvent.AddAsIngredient(
                        itemId = recipeId,
                        itemName = recipe.name,
                        servingLabel = null,
                        isRecipe = true,
                    ),
                )
            }
        } else if (_state.value.targetContext != FoodPickerTarget.BROWSE) {
            _state.update {
                it.copy(
                    selectedItemReady = true,
                    selectedItemId = recipeId,
                    selectedItemIsRecipe = true,
                    selectedItemHasServingUnit = true,
                )
            }
        }
    }

    private fun confirmAddToDiary(action: FoodPickerAction.OnConfirmAddToDiary) {
        val s = _state.value
        val itemId = s.selectedItemId ?: return
        val date = s.targetDate ?: return
        _state.update {
            it.copy(
                selectedItemReady = false,
                selectedItemId = null,
                selectedItemIsRecipe = false,
                selectedItemHasServingUnit = false,
            )
        }
        viewModelScope.launch {
            val onlineItem = s.onlineFoodItems.firstOrNull { it.id.value == itemId }
            if (onlineItem != null) {
                when (val result = foodRepository.cacheRemoteItem(onlineItem)) {
                    is Result.Error -> {
                        _state.update { it.copy(selectionError = result.error.toUiText()) }
                        return@launch
                    }
                    is Result.Success -> { /* proceed */ }
                }
            }
            _events.send(
                FoodPickerEvent.AddToDiary(
                    foodId = itemId,
                    isRecipe = s.selectedItemIsRecipe,
                    date = date,
                    slot = s.slot ?: action.slot,
                    amount = action.amount,
                    isGrams = action.isGrams,
                ),
            )
        }
    }

    private fun lookupBarcode(barcode: String) {
        if (_state.value.isLookingUpBarcode) return
        viewModelScope.launch {
            _state.update { it.copy(isLookingUpBarcode = true, barcodeError = null) }
            when (val result = foodRepository.lookupByBarcode(barcode)) {
                is Result.Success -> {
                    _state.update { it.copy(isLookingUpBarcode = false) }
                    val food = result.data
                    when (_state.value.targetContext) {
                        FoodPickerTarget.DIARY -> {
                            if (food != null) {
                                _state.update {
                                    it.copy(
                                        selectedItemReady = true,
                                        selectedItemId = food.id.value,
                                        selectedItemIsRecipe = false,
                                        selectedItemHasServingUnit = food.servingUnit != null,
                                    )
                                }
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
                                _events.send(
                                    FoodPickerEvent.AddAsIngredient(
                                        itemId = food.id.value,
                                        itemName = food.name,
                                        servingLabel = food.servingUnit?.label,
                                        isRecipe = false,
                                    ),
                                )
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
            .onEach { foods -> _state.update { it.copy(userFoods = foods.map { f -> f.toPickerFoodUi() }) } }
            .launchIn(viewModelScope)
    }

    private fun observeAllRecipes() {
        recipeRepository
            .observeAll()
            .onEach { recipes ->
                _state.update { it.copy(userRecipes = recipes.map { r -> r.toPickerRecipeUi() }) }
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
    }
}
