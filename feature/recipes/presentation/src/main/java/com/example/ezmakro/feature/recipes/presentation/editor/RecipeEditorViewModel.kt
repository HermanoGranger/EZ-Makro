package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.feature.recipes.presentation.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Creates or edits a [Recipe]. Ingredients reference existing foods (chosen from
 * [FoodRepository]); each line carries either a raw weight or a serving count.
 * ADR-003 invariants (>=1 ingredient, positive portions/scale, non-negative raw
 * weights) are validated here before the domain model is constructed.
 *
 * @param recipeId null when creating a new recipe.
 * @param isQuickMeal when true, renders the reduced "Schnell-Kombo" UI (no
 *   meal-prep / scale fields), still persisted as a full [Recipe].
 */
@Suppress("TooManyFunctions")
class RecipeEditorViewModel(
    private val recipeId: String?,
    private val isQuickMeal: Boolean,
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            RecipeEditorState(
                isEditingExisting = recipeId != null,
                isQuickMeal = isQuickMeal,
                recipeId = recipeId,
                name = (savedStateHandle[KEY_NAME] as String?).orEmpty(),
                category = (savedStateHandle[KEY_CATEGORY] as String?).orEmpty(),
                portions = (savedStateHandle[KEY_PORTIONS] as String?) ?: "1",
                finalWeight = (savedStateHandle[KEY_FINAL_WEIGHT] as String?).orEmpty(),
                scale = (savedStateHandle[KEY_SCALE] as String?) ?: "1",
                isMealPrep = (savedStateHandle[KEY_MEAL_PREP] as Boolean?) ?: false,
                calculationMode =
                    (savedStateHandle[KEY_CALCULATION_MODE] as? String)
                        ?.let { runCatching { RecipeCalculationMode.valueOf(it) }.getOrNull() }
                        ?: if ((savedStateHandle[KEY_FINAL_WEIGHT] as String?).isNullOrBlank()) {
                            RecipeCalculationMode.Portion
                        } else {
                            RecipeCalculationMode.Weight
                        },
            ),
        )
    val state = _state.asStateFlow()

    private val _events = Channel<RecipeEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val foodsState = MutableStateFlow<List<FoodItem>>(emptyList())

    init {
        foodRepository
            .observeAll()
            .onEach { foods ->
                foodsState.value = foods
                _state.update {
                    it.copy(
                        availableFoods =
                            foods.map { f ->
                                FoodPickerItem(
                                    id = f.id.value,
                                    name = f.name,
                                    servingLabel = f.servingUnit?.label,
                                )
                            },
                    )
                }
            }.launchIn(viewModelScope)
        if (recipeId != null) loadExisting(recipeId)
    }

    fun onAction(action: RecipeEditorAction) {
        when (action) {
            is RecipeEditorAction.OnNameChange ->
                handleField(KEY_NAME, action.value) {
                    it.copy(name = action.value, nameError = null)
                }
            is RecipeEditorAction.OnCategoryChange ->
                handleField(KEY_CATEGORY, action.value) { it.copy(category = action.value) }
            is RecipeEditorAction.OnPortionsChange ->
                handleField(KEY_PORTIONS, action.value) {
                    it.copy(portions = action.value, numberError = null)
                }
            is RecipeEditorAction.OnScaleChange ->
                handleField(KEY_SCALE, action.value) {
                    it.copy(scale = action.value, numberError = null)
                }
            is RecipeEditorAction.OnMealPrepToggle ->
                handleField(KEY_MEAL_PREP, action.value.toString()) {
                    it.copy(isMealPrep = action.value)
                }

            is RecipeEditorAction.OnFinalWeightChange -> onFinalWeightChange(action.value)

            is RecipeEditorAction.OnCalculationModeChange -> onCalculationModeChange(action.mode)

            is RecipeEditorAction.OnAddIngredientClick -> addIngredient(action.foodId)

            is RecipeEditorAction.OnIngredientRawWeightChange ->
                _state.update {
                    it.copy(
                        ingredients =
                            it.ingredients.updateAt(action.index) { d ->
                                d.copy(rawWeight = action.value, servingCount = "")
                            },
                        ingredientError = null,
                    )
                }

            is RecipeEditorAction.OnIngredientServingCountChange ->
                _state.update {
                    it.copy(
                        ingredients =
                            it.ingredients.updateAt(action.index) { d ->
                                d.copy(servingCount = action.value, rawWeight = "")
                            },
                        ingredientError = null,
                    )
                }

            is RecipeEditorAction.OnRemoveIngredientClick ->
                _state.update { it.copy(ingredients = it.ingredients.filterIndexed { i, _ -> i != action.index }) }

            RecipeEditorAction.OnSaveClick ->
                viewModelScope.launch { save() }

            RecipeEditorAction.OnBackClick ->
                viewModelScope.launch { _events.send(RecipeEditorEvent.NavigateBack) }
        }
    }

    private fun onFinalWeightChange(value: String) {
        savedStateHandle[KEY_FINAL_WEIGHT] = value
        _state.update {
            it.copy(
                finalWeight = value,
                numberError = null,
                calculationMode =
                    if (value.isBlank()) {
                        RecipeCalculationMode.Portion
                    } else {
                        RecipeCalculationMode.Weight
                    },
            )
        }
    }

    private fun onCalculationModeChange(mode: RecipeCalculationMode) {
        savedStateHandle[KEY_CALCULATION_MODE] = mode.name
        if (mode == RecipeCalculationMode.Portion) {
            savedStateHandle[KEY_FINAL_WEIGHT] = ""
            _state.update {
                it.copy(
                    calculationMode = RecipeCalculationMode.Portion,
                    finalWeight = "",
                    numberError = null,
                )
            }
        } else {
            _state.update { it.copy(calculationMode = RecipeCalculationMode.Weight) }
        }
    }

    private inline fun handleField(
        key: String,
        value: String,
        crossinline update: (RecipeEditorState) -> RecipeEditorState,
    ) {
        savedStateHandle[key] = value
        _state.update { update(it).copy(saveError = null) }
    }

    private fun addIngredient(foodId: String) {
        val food =
            _state.value.availableFoods.firstOrNull { it.id == foodId }
                ?: _state.value.availableFoods.firstOrNull()
                ?: return
        _state.update {
            it.copy(
                ingredients =
                    it.ingredients +
                        IngredientDraft(
                            foodId = food.id,
                            foodName = food.name,
                            servingLabel = food.servingLabel,
                        ),
                ingredientError = null,
            )
        }
        viewModelScope.launch { _events.send(RecipeEditorEvent.ShowIngredientPicker(false)) }
    }

    private fun loadExisting(id: String) {
        recipeRepository
            .observeById(RecipeId(id))
            .onEach { recipe ->
                if (recipe == null) return@onEach
                _state.update {
                    it.copy(
                        name = it.name.ifEmpty { recipe.name },
                        category = recipe.category ?: "",
                        portions = recipe.portions.toString(),
                        finalWeight =
                            recipe.finalWeight
                                ?.grams
                                ?.toString()
                                .orEmpty(),
                        scale = recipe.scale.toString(),
                        isMealPrep = recipe.isMealPrep,
                        calculationMode =
                            if (recipe.finalWeight != null) {
                                RecipeCalculationMode.Weight
                            } else {
                                RecipeCalculationMode.Portion
                            },
                        ingredients =
                            recipe.ingredients.map { ing ->
                                IngredientDraft(
                                    foodId = ing.food.id.value,
                                    foodName = ing.food.name,
                                    servingLabel = ing.food.servingUnit?.label,
                                    rawWeight =
                                        ing.rawWeight
                                            ?.grams
                                            ?.toString()
                                            .orEmpty(),
                                    servingCount = ing.servingCount?.toString().orEmpty(),
                                )
                            },
                    )
                }
            }.launchIn(viewModelScope)
    }

    private fun save() {
        val s = _state.value
        if (!validateForSave(s)) return
        _state.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch { executeSave(s) }
    }

    private suspend fun executeSave(s: RecipeEditorState) {
        try {
            val parsed = parseSaveInput(s) ?: return
            val ingredients = buildIngredients(s.ingredients)
            if (ingredients == null) {
                _state.update { it.copy(isSaving = false) }
                return
            }
            val recipe = buildRecipe(s, parsed, ingredients)
            val result = recipeRepository.save(recipe)
            result.onSuccess {
                _state.update { it.copy(isSaving = false) }
                _events.send(RecipeEditorEvent.Saved)
            }
            result.onFailure {
                _state.update {
                    it.copy(
                        isSaving = false,
                        saveError = UiText.DynamicString("Fehler beim Speichern: Datenbank-Fehler"),
                    )
                }
            }
        } catch (e: IllegalArgumentException) {
            _state.update {
                it.copy(
                    isSaving = false,
                    saveError = UiText.DynamicString("Fehler: ${e.message ?: e.javaClass.simpleName}"),
                )
            }
        }
    }

    private data class ParsedSaveInput(
        val scale: Double,
        val portions: Int,
        val finalWeight: FinalWeight?,
    )

    private fun parseSaveInput(s: RecipeEditorState): ParsedSaveInput? {
        val scale = s.scale.parsePositiveDouble()
        val portions =
            if (s.calculationMode == RecipeCalculationMode.Weight) {
                s.portions.parsePositiveInt() ?: 1
            } else {
                s.portions.parsePositiveInt()
            }
        if (scale == null || portions == null) {
            _state.update {
                it.copy(
                    isSaving = false,
                    numberError = UiText.StringResource(R.string.recipe_editor_error_invalid_number),
                )
            }
            return null
        }
        val fwRaw =
            if (s.calculationMode == RecipeCalculationMode.Weight) {
                s.finalWeight.parseNonNegativeDouble()
            } else {
                null
            }
        return ParsedSaveInput(scale, portions, fwRaw?.let { FinalWeight(it) })
    }

    private fun buildRecipe(
        s: RecipeEditorState,
        parsed: ParsedSaveInput,
        ingredients: List<RecipeIngredient>,
    ): Recipe {
        val id = recipeId ?: UUID.randomUUID().toString()
        return Recipe(
            id = RecipeId(id),
            name = s.name.trim(),
            ingredients = ingredients,
            finalWeight = parsed.finalWeight,
            portions = parsed.portions,
            scale = parsed.scale,
            category = s.category.trim().ifBlank { null },
            isMealPrep = if (s.isQuickMeal) false else s.isMealPrep,
        )
    }

    private fun validateForSave(s: RecipeEditorState): Boolean {
        val nameError =
            if (s.name.isBlank()) {
                UiText.StringResource(R.string.recipe_editor_error_name_blank)
            } else {
                null
            }
        val ingredientError =
            if (s.ingredients.isEmpty()) {
                UiText.StringResource(R.string.recipe_editor_error_no_ingredients)
            } else {
                null
            }
        val numberError =
            when {
                s.calculationMode == RecipeCalculationMode.Weight ->
                    if (s.finalWeight.parseNonNegativeDouble() == null || s.scale.parsePositiveDouble() == null) {
                        UiText.StringResource(R.string.recipe_editor_error_invalid_number)
                    } else {
                        null
                    }
                else ->
                    if (s.portions.parsePositiveInt() == null || s.scale.parsePositiveDouble() == null) {
                        UiText.StringResource(R.string.recipe_editor_error_invalid_number)
                    } else {
                        null
                    }
            }
        val hasError = nameError != null || ingredientError != null || numberError != null
        if (hasError) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    ingredientError = ingredientError,
                    numberError = numberError,
                )
            }
        }
        return !hasError
    }

    private fun buildIngredients(drafts: List<IngredientDraft>): List<RecipeIngredient>? {
        val result = ArrayList<RecipeIngredient>(drafts.size)
        var failed: UiText? = null
        val foods = foodsState.value
        for (draft in drafts) {
            val food = foods.firstOrNull { it.id.value == draft.foodId }
            val rawWeight = draft.rawWeight.parseNonNegativeDouble()?.let { RawWeight(it) }
            val servingCount = draft.servingCount.parseNonNegativeDouble()
            val ingredientOk =
                food != null &&
                    (rawWeight != null || servingCount != null) &&
                    (servingCount == null || food.servingUnit != null)
            if (ingredientOk) {
                result +=
                    RecipeIngredient(
                        food = food,
                        rawWeight = rawWeight,
                        servingCount = servingCount,
                    )
            } else {
                failed = UiText.StringResource(R.string.recipe_editor_error_ingredient_weight)
                break
            }
        }
        return if (failed == null) {
            result
        } else {
            _state.update { it.copy(ingredientError = failed) }
            null
        }
    }

    private fun <T> List<T>.updateAt(
        index: Int,
        block: (T) -> T,
    ): List<T> = mapIndexed { i, item -> if (i == index) block(item) else item }

    private companion object {
        const val KEY_NAME = "recipe_edit_name"
        const val KEY_CATEGORY = "recipe_edit_category"
        const val KEY_PORTIONS = "recipe_edit_portions"
        const val KEY_FINAL_WEIGHT = "recipe_edit_final_weight"
        const val KEY_SCALE = "recipe_edit_scale"
        const val KEY_MEAL_PREP = "recipe_edit_meal_prep"
        const val KEY_CALCULATION_MODE = "recipe_edit_calculation_mode"
    }
}
