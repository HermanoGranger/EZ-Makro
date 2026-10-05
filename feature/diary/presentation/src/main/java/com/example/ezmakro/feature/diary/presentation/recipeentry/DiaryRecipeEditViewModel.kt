package com.example.ezmakro.feature.diary.presentation.recipeentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.calculation.consumedNutrition
import com.example.ezmakro.core.domain.calculation.nutrition
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.domain.model.markChangedRecipeSnapshots
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.usecase.diary.DiaryRecipeEditScope
import com.example.ezmakro.core.domain.usecase.diary.EditDiaryRecipeEntryUseCase
import com.example.ezmakro.core.domain.usecase.recipe.BuildRecipeFromDraftsUseCase
import com.example.ezmakro.core.domain.usecase.recipe.RecipeDraftError
import com.example.ezmakro.core.domain.util.parseNonNegativeDouble
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.core.presentation.uitext.toUiText
import com.example.ezmakro.feature.diary.presentation.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_PORTIONS = "diary_recipe_edit_portions"
private const val KEY_FINAL_WEIGHT = "diary_recipe_edit_final_weight"
private const val KEY_AMOUNT = "diary_recipe_edit_amount"
private const val GENERIC_SERVING_LABEL = "×1"

class DiaryRecipeEditViewModel(
    private val entryId: String,
    private val savedStateHandle: SavedStateHandle,
    private val diaryRepository: DiaryRepository,
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository,
    private val buildRecipeFromDraftsUseCase: BuildRecipeFromDraftsUseCase,
    private val editDiaryRecipeEntryUseCase: EditDiaryRecipeEntryUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryRecipeEditState(entryId = entryId))
    val state: StateFlow<DiaryRecipeEditState> = _state.asStateFlow()

    private val _events = Channel<DiaryRecipeEditEvent>()
    val events = _events.receiveAsFlow()

    private var hasSeeded = false
    private var foodsState: List<com.example.ezmakro.core.domain.model.FoodItem> = emptyList()
    private var recipesState: List<Recipe> = emptyList()
    private var loadedEntry: DiaryEntry? = null

    init {
        loadEntry()
        observeSources()
    }

    fun onAction(action: DiaryRecipeEditAction) {
        when (action) {
            is DiaryRecipeEditAction.OnIngredientRawWeightChange -> onIngredientRawWeightChange(action.index, action.value)
            is DiaryRecipeEditAction.OnIngredientServingCountChange -> onIngredientServingCountChange(action.index, action.value)
            is DiaryRecipeEditAction.OnRemoveIngredient -> onRemoveIngredient(action.index)
            is DiaryRecipeEditAction.OnReplaceIngredient -> onReplaceIngredient(action.index)
            is DiaryRecipeEditAction.OnAddIngredientClick -> onAddIngredientClick()
            is DiaryRecipeEditAction.OnIngredientClick -> onIngredientClick(action.index)
            is DiaryRecipeEditAction.OnAmountModeChange -> onAmountModeChange(action.mode)
            is DiaryRecipeEditAction.OnAmountChange -> onAmountChange(action.value)
            is DiaryRecipeEditAction.OnPortionsChange -> onPortionsChange(action.value)
            is DiaryRecipeEditAction.OnFinalWeightChange -> onFinalWeightChange(action.value)
            is DiaryRecipeEditAction.OnSaveClick -> onSaveClick()
            is DiaryRecipeEditAction.OnConfirmSave -> onConfirmSave(action.scope)
            is DiaryRecipeEditAction.OnDismissSaveScopeDialog -> onDismissSaveScopeDialog()
            is DiaryRecipeEditAction.OnDismissPicker -> onDismissPicker()
            is DiaryRecipeEditAction.OnBackClick -> viewModelScope.launch { _events.send(DiaryRecipeEditEvent.NavigateBack) }
        }
    }

    private fun loadEntry() {
        viewModelScope.launch {
            val result = diaryRepository.getById(DiaryEntryId(entryId))
            when (result) {
                is Result.Success -> {
                    val entry = result.data ?: return@launch
                    loadedEntry = entry
                    seedFromEntry(entry)
                }
                is Result.Error -> {
                    _state.update { it.copy(isLoading = false, saveError = result.error.toUiText()) }
                }
            }
        }
    }

    private fun seedFromEntry(entry: DiaryEntry) {
        if (hasSeeded) return
        hasSeeded = true

        savedStateHandle.get<String>(KEY_PORTIONS)?.let { saved ->
            if (saved.isNotEmpty()) {
                // keep saved? - but we want initial from recipe; spec says init uses savedStateHandle start values
                // but seed from entry takes precedence for form; we'll still allow savedStateHandle to override? spec: "init: savedStateHandle[KEY_PORTIONS/KEY_FINAL_WEIGHT/KEY_AMOUNT] as Startwerte; diaryRepository.getById(entryId) → State aus entry.recipe"
                // - we'll apply saved state after seeding if non-empty? but keep simple: seed first, then saved handles override specific fields
            }
        }

        val recipe = entry.recipe
        val (mode, amount) = when {
            entry.portionCount != null -> DiaryAmountMode.PORTIONS to entry.portionCount.toString()
            entry.amountGrams != null -> DiaryAmountMode.GRAMS to entry.amountGrams.toString()
            else -> DiaryAmountMode.PORTIONS to ""
        }

        _state.update { current ->
            current.copy(
                isLoading = false,
                entryId = entryId,
                recipeName = recipe?.name ?: entry.food?.name.orEmpty(),
                date = entry.date.toString(),
                sourceRecipeId = recipe?.id?.value,
                amountMode = mode,
                amountText = amount,
                portions = recipe?.portions?.toString() ?: "",
                finalWeight = recipe?.finalWeight?.grams?.toString() ?: "",
                ingredients = recipe?.ingredients?.map { it.toDraft() } ?: emptyList(),
                originalConsumedNutrition = entry.consumedNutrition(),
                isRecipeOverridden = entry.isRecipeOverridden,
            )
        }
        applySavedStateHandle()
        recompute()
    }

    private fun applySavedStateHandle() {
        val savedPortions = savedStateHandle.get<String>(KEY_PORTIONS)
        val savedFinal = savedStateHandle.get<String>(KEY_FINAL_WEIGHT)
        val savedAmount = savedStateHandle.get<String>(KEY_AMOUNT)
        _state.update {
            it.copy(
                portions = savedPortions.takeIf { !it.isNullOrEmpty() } ?: it.portions,
                finalWeight = savedFinal.takeIf { !it.isNullOrEmpty() } ?: it.finalWeight,
                amountText = savedAmount.takeIf { !it.isNullOrEmpty() } ?: it.amountText,
            )
        }
    }

    private fun observeSources() {
        foodRepository.observeAll().onEach { foods ->
            foodsState = foods
            val drafts = _state.value.ingredients.markChangedRecipeSnapshots(recipesState)
            _state.update { it.copy(ingredients = drafts) }
            recompute()
        }.launchIn(viewModelScope)

        recipeRepository.observeAll().onEach { recipes ->
            recipesState = recipes
            val sourceId = _state.value.sourceRecipeId
            val canUpdate = sourceId != null && recipes.any { it.id.value == sourceId }
            val outdated = sourceId != null && recipes.any { r ->
                r.id.value == sourceId && loadedEntry?.recipe?.lastModifiedAt != r.lastModifiedAt
            }
            val drafts = _state.value.ingredients.markChangedRecipeSnapshots(recipes)
            _state.update { it.copy(canUpdateRecipe = canUpdate, isSourceRecipeOutdated = outdated, ingredients = drafts) }
            recompute()
        }.launchIn(viewModelScope)
    }

    private fun onIngredientRawWeightChange(index: Int, value: String) {
        val list = _state.value.ingredients.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(rawWeight = value)
            _state.update { it.copy(ingredients = list, ingredientError = null) }
            recompute()
        }
    }

    private fun onIngredientServingCountChange(index: Int, value: String) {
        val list = _state.value.ingredients.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(servingCount = value)
            _state.update { it.copy(ingredients = list, ingredientError = null) }
            recompute()
        }
    }

    private fun onRemoveIngredient(index: Int) {
        val list = _state.value.ingredients.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _state.update { it.copy(ingredients = list, ingredientError = null, expandedIngredientIndex = null) }
            recompute()
        }
    }

    private fun onReplaceIngredient(index: Int) {
        _state.update { it.copy(replaceIngredientIndex = index, isAddPickerOpen = true) }
    }

    private fun onAddIngredientClick() {
        _state.update { it.copy(replaceIngredientIndex = null, isAddPickerOpen = true) }
    }

    private fun onIngredientClick(index: Int) {
        _state.update { it.copy(expandedIngredientIndex = if (it.expandedIngredientIndex == index) null else index) }
    }

    private fun onAmountModeChange(mode: DiaryAmountMode) {
        savedStateHandle[KEY_AMOUNT] = _state.value.amountText
        _state.update { it.copy(amountMode = mode) }
        recompute()
    }

    private fun onAmountChange(value: String) {
        savedStateHandle[KEY_AMOUNT] = value
        _state.update { it.copy(amountText = value, amountError = null) }
        recompute()
    }

    private fun onPortionsChange(value: String) {
        savedStateHandle[KEY_PORTIONS] = value
        _state.update { it.copy(portions = value, numberError = null) }
        recompute()
    }

    private fun onFinalWeightChange(value: String) {
        savedStateHandle[KEY_FINAL_WEIGHT] = value
        _state.update { it.copy(finalWeight = value, numberError = null) }
        recompute()
    }

    private fun onSaveClick() {
        val amountValid = _state.value.amountText.parseNonNegativeDouble() != null && _state.value.amountText.toDoubleOrNull() != null && _state.value.amountText.toDouble() > 0.0
        if (!amountValid) {
            _state.update { it.copy(amountError = UiText.StringResource(R.string.diary_recipe_edit_error_amount_invalid)) }
            return
        }
        if (_state.value.ingredients.isEmpty()) {
            _state.update { it.copy(ingredientError = UiText.StringResource(R.string.diary_recipe_edit_error_no_ingredients)) }
            return
        }
        _state.update { it.copy(isSaveScopeDialogOpen = true) }
    }

    private fun onConfirmSave(scope: DiaryRecipeEditScope) {
        _state.update { it.copy(isSaveScopeDialogOpen = false, isSaving = true, saveError = null) }
        val drafts = _state.value.ingredients.markChangedRecipeSnapshots(recipesState)
        val buildResult = buildRecipeFromDraftsUseCase(drafts, foodsState, recipesState)
        when (buildResult) {
            is Result.Error -> {
                val error = buildResult.error
                val uiError = when (error) {
                    RecipeDraftError.MissingAmount -> UiText.StringResource(R.string.diary_recipe_edit_error_ingredient_weight)
                    RecipeDraftError.UnknownFood -> UiText.StringResource(R.string.diary_recipe_edit_error_unknown_food)
                    RecipeDraftError.MissingSnapshot -> UiText.StringResource(R.string.diary_recipe_edit_error_missing_snapshot)
                }
                _state.update { it.copy(isSaving = false, ingredientError = uiError) }
                return
            }
            is Result.Success -> {
                val ingredients = buildResult.data
                val portions = _state.value.portions.parseNonNegativeDouble()?.toInt() ?: 1
                val finalWeight = _state.value.finalWeight.parseNonNegativeDouble()?.let { com.example.ezmakro.core.domain.model.FinalWeight(it) }
                val editedRecipe = createEditedRecipe(ingredients, portions, finalWeight)
                val amountGrams = if (_state.value.amountMode == DiaryAmountMode.GRAMS) _state.value.amountText.toDoubleOrNull() else null
                val portionCount = if (_state.value.amountMode == DiaryAmountMode.PORTIONS) _state.value.amountText.toDoubleOrNull() else null
                viewModelScope.launch {
                    val saveResult = editDiaryRecipeEntryUseCase(DiaryEntryId(entryId), editedRecipe, amountGrams, portionCount, scope)
                    when (saveResult) {
                        is Result.Success -> _events.send(DiaryRecipeEditEvent.Saved)
                        is Result.Error -> _state.update { it.copy(isSaving = false, saveError = saveResult.error.toUiText()) }
                    }
                }
            }
        }
    }

    private fun onDismissSaveScopeDialog() {
        _state.update { it.copy(isSaveScopeDialogOpen = false) }
    }

    private fun onDismissPicker() {
        _state.update { it.copy(isAddPickerOpen = false) }
    }

    private fun recompute() {
        val drafts = _state.value.ingredients.markChangedRecipeSnapshots(recipesState)
        _state.update { it.copy(ingredients = drafts) }
        val buildResult = buildRecipeFromDraftsUseCase(drafts, foodsState, recipesState)
        when (buildResult) {
            is Result.Error -> {
                _state.update {
                    it.copy(
                        ingredientNutrition = List(drafts.size) { NutritionValues.ZERO },
                        totalNutrition = NutritionValues.ZERO,
                        consumedNutrition = computeConsumedForCurrent(drafts, NutritionValues.ZERO, emptyList()),
                    )
                }
                val uiError = when (buildResult.error) {
                    RecipeDraftError.MissingAmount -> UiText.StringResource(R.string.diary_recipe_edit_error_ingredient_weight)
                    RecipeDraftError.UnknownFood -> UiText.StringResource(R.string.diary_recipe_edit_error_unknown_food)
                    RecipeDraftError.MissingSnapshot -> UiText.StringResource(R.string.diary_recipe_edit_error_missing_snapshot)
                }
                _state.update { it.copy(ingredientError = uiError) }
            }
            is Result.Success -> {
                val ingredients = buildResult.data
                val nutritionList = ingredients.map { it.nutrition() }
                val total = nutritionList.fold(NutritionValues.ZERO) { acc, v -> acc + v }
                val consumed = computeConsumedForCurrent(drafts, total, ingredients)
                _state.update {
                    it.copy(
                        ingredientNutrition = nutritionList,
                        totalNutrition = total,
                        consumedNutrition = consumed,
                        ingredientError = null,
                    )
                }
            }
        }
    }

    private fun computeConsumedForCurrent(
        drafts: List<RecipeIngredientDraft>,
        total: NutritionValues,
        ingredients: List<RecipeIngredient>,
    ): NutritionValues {
        val entry = loadedEntry ?: return NutritionValues.ZERO
        val portions = _state.value.portions.parseNonNegativeDouble()?.toInt() ?: 1
        val finalWeight = _state.value.finalWeight.parseNonNegativeDouble()?.let { com.example.ezmakro.core.domain.model.FinalWeight(it) }
        val edited = createEditedRecipe(ingredients, portions, finalWeight)
        val amountGrams = if (_state.value.amountMode == DiaryAmountMode.GRAMS) _state.value.amountText.toDoubleOrNull() else null
        val portionCount = if (_state.value.amountMode == DiaryAmountMode.PORTIONS) _state.value.amountText.toDoubleOrNull() else null
        return try {
            entry.copy(recipe = edited, amountGrams = amountGrams, portionCount = portionCount).consumedNutrition()
        } catch (_: Exception) {
            NutritionValues.ZERO
        }
    }

    private fun createEditedRecipe(
        ingredients: List<RecipeIngredient>,
        portions: Int,
        finalWeight: com.example.ezmakro.core.domain.model.FinalWeight?,
    ): Recipe {
        val base = loadedEntry?.recipe ?: Recipe(
            id = RecipeId(java.util.UUID.randomUUID().toString()),
            name = _state.value.recipeName,
            category = null,
            portions = portions.toInt(),
            finalWeight = finalWeight,
            ingredients = ingredients,
            isFavorite = false,
            lastUsedAt = null,
            lastModifiedAt = System.currentTimeMillis(),
        )
        return base.copy(
            name = _state.value.recipeName,
            portions = portions.toInt(),
            finalWeight = finalWeight,
            ingredients = ingredients,
        )
    }
}

private fun RecipeIngredient.toDraft(): RecipeIngredientDraft =
    RecipeIngredientDraft(
        foodId = food?.id?.value.orEmpty(),
        foodName = food?.name ?: recipeSnapshot?.recipeName.orEmpty(),
        servingLabel = food?.servingUnit?.label ?: if (recipeSnapshot != null) "×1" else null,
        rawWeight = rawWeight?.grams?.toString().orEmpty(),
        servingCount = servingCount?.toString().orEmpty(),
        recipeId = recipeSnapshot?.recipeId?.value,
        originalLastModifiedAt = recipeSnapshot?.originalLastModifiedAt,
        recipeSnapshot = recipeSnapshot,
    )
