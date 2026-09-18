package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.calculation.nutritionPer100g
import com.example.ezmakro.core.domain.calculation.totalNutrition
import com.example.ezmakro.core.domain.calculation.totalRawWeight
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.RecipeSnapshot
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

@Suppress("TooManyFunctions")
class RecipeEditorViewModel(
    private val recipeId: String?,
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            RecipeEditorState(
                isEditingExisting = recipeId != null,
                recipeId = recipeId,
                name = (savedStateHandle[KEY_NAME] as String?).orEmpty(),
                category = (savedStateHandle[KEY_CATEGORY] as String?).orEmpty(),
                portions = (savedStateHandle[KEY_PORTIONS] as String?) ?: "1",
                finalWeight = (savedStateHandle[KEY_FINAL_WEIGHT] as String?).orEmpty(),
            ),
        )
    val state = _state.asStateFlow()

    private val _events = Channel<RecipeEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val foodsState = MutableStateFlow<List<FoodItem>>(emptyList())
    private val recipesState = MutableStateFlow<List<Recipe>>(emptyList())

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

        recipeRepository
            .observeAll()
            .onEach { recipes ->
                recipesState.value = recipes
                _state.update {
                    it.copy(
                        availableRecipes =
                            recipes.map { r ->
                                RecipePickerItem(
                                    id = r.id.value,
                                    name = r.name,
                                )
                            },
                    )
                }
                checkSnapshotChanges()
            }.launchIn(viewModelScope)

        if (recipeId != null) loadExisting(recipeId)
    }

    @Suppress("LongMethod")
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
            is RecipeEditorAction.OnFinalWeightChange ->
                handleField(KEY_FINAL_WEIGHT, action.value) {
                    it.copy(finalWeight = action.value, numberError = null)
                }

            is RecipeEditorAction.OnAddIngredientClick -> addIngredient(action)

            is RecipeEditorAction.OnAddRecipeIngredientClick -> addRecipeIngredient(action.recipeId)

            is RecipeEditorAction.OnIngredientRawWeightChange ->
                _state.update {
                    it.copy(
                        ingredients =
                            it.ingredients.updateAt(action.index) { d ->
                                d.copy(rawWeight = action.value)
                            },
                        ingredientError = null,
                    )
                }

            is RecipeEditorAction.OnIngredientServingCountChange ->
                _state.update {
                    it.copy(
                        ingredients =
                            it.ingredients.updateAt(action.index) { d ->
                                d.copy(servingCount = action.value)
                            },
                        ingredientError = null,
                    )
                }

            is RecipeEditorAction.OnRemoveIngredientClick ->
                _state.update {
                    it.copy(
                        ingredients = it.ingredients.filterIndexed { i, _ -> i != action.index },
                        expandedIngredientIndex = null,
                    )
                }

            is RecipeEditorAction.OnIngredientClick ->
                _state.update {
                    it.copy(
                        expandedIngredientIndex =
                            if (it.expandedIngredientIndex == action.index) null else action.index,
                    )
                }

            RecipeEditorAction.OnToggleFavorite ->
                _state.update { it.copy(isFavorite = !it.isFavorite) }

            RecipeEditorAction.OnSaveClick ->
                viewModelScope.launch { save() }

            RecipeEditorAction.OnBackClick ->
                viewModelScope.launch { _events.send(RecipeEditorEvent.NavigateBack) }
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

    private fun addIngredient(action: RecipeEditorAction.OnAddIngredientClick) {
        val known = _state.value.availableFoods.firstOrNull { it.id == action.foodId }
        val foodName = action.foodName.ifBlank { known?.name ?: return }
        val servingLabel = action.servingLabel ?: known?.servingLabel
        _state.update {
            it.copy(
                ingredients =
                    it.ingredients +
                        IngredientDraft(
                            foodId = action.foodId,
                            foodName = foodName,
                            servingLabel = servingLabel,
                        ),
                ingredientError = null,
            )
        }
        viewModelScope.launch { _events.send(RecipeEditorEvent.ShowIngredientPicker(false)) }
    }

    private fun addRecipeIngredient(recipeId: String) {
        val recipe = recipesState.value.firstOrNull { it.id.value == recipeId } ?: return
        _state.update {
            it.copy(
                ingredients =
                    it.ingredients +
                        IngredientDraft(
                            foodId = recipeId,
                            foodName = recipe.name,
                            servingLabel = null,
                            recipeId = recipeId,
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
                        isFavorite = recipe.isFavorite,
                        ingredients =
                            recipe.ingredients.map { ing ->
                                IngredientDraft(
                                    foodId =
                                        ing.food
                                            ?.id
                                            ?.value
                                            .orEmpty(),
                                    foodName = ing.food?.name ?: ing.recipeSnapshot?.recipeName.orEmpty(),
                                    servingLabel =
                                        ing.food?.servingUnit?.label
                                            ?: if (ing.recipeSnapshot != null) GENERIC_SERVING_LABEL else null,
                                    rawWeight =
                                        ing.rawWeight
                                            ?.grams
                                            ?.toString()
                                            .orEmpty(),
                                    servingCount = ing.servingCount?.toString().orEmpty(),
                                    recipeId = ing.recipeSnapshot?.recipeId?.value,
                                    originalLastModifiedAt = ing.recipeSnapshot?.originalLastModifiedAt,
                                    recipeSnapshot = ing.recipeSnapshot,
                                )
                            },
                    )
                }
                checkSnapshotChanges()
            }.launchIn(viewModelScope)
    }

    private fun checkSnapshotChanges() {
        val recipes = recipesState.value
        _state.update { current ->
            current.copy(
                ingredients =
                    current.ingredients.map { draft ->
                        if (draft.recipeId == null || draft.originalLastModifiedAt == null) return@map draft
                        val original = recipes.firstOrNull { it.id.value == draft.recipeId }
                        val changed = original == null || original.lastModifiedAt != draft.originalLastModifiedAt
                        draft.copy(snapshotChanged = changed)
                    },
            )
        }
    }

    private fun save() {
        val s = _state.value
        if (!validateForSave(s)) return
        _state.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch { executeSave() }
    }

    private suspend fun executeSave() {
        val s = _state.value
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
        val portions: Int,
        val finalWeight: FinalWeight?,
    )

    private fun parseSaveInput(s: RecipeEditorState): ParsedSaveInput? {
        val portions = s.portions.parsePositiveInt()
        if (portions == null) {
            _state.update {
                it.copy(
                    isSaving = false,
                    numberError = UiText.StringResource(R.string.recipe_editor_error_invalid_number),
                )
            }
            return null
        }
        val fwRaw = s.finalWeight.parseNonNegativeDouble()
        return ParsedSaveInput(portions, fwRaw?.let { FinalWeight(it) })
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
            category = s.category.trim().ifBlank { null },
            isFavorite = s.isFavorite,
            lastModifiedAt = System.currentTimeMillis(),
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
            if (s.portions.parsePositiveInt() == null) {
                UiText.StringResource(R.string.recipe_editor_error_invalid_number)
            } else {
                null
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
        val foods = foodsState.value
        val recipes = recipesState.value
        for (draft in drafts) {
            val ingredient =
                if (draft.recipeId != null) {
                    resolveRecipeIngredient(draft, recipes)
                } else {
                    resolveFoodIngredient(draft, foods)
                }
            if (ingredient == null) {
                _state.update {
                    it.copy(ingredientError = UiText.StringResource(R.string.recipe_editor_error_ingredient_weight))
                }
                return null
            }
            result += ingredient
        }
        return result
    }

    private fun resolveRecipeIngredient(
        draft: IngredientDraft,
        recipes: List<Recipe>,
    ): RecipeIngredient? {
        val recipe = recipes.firstOrNull { it.id.value == draft.recipeId }
        val rawWeight = draft.rawWeight.parseNonNegativeDouble()?.let { RawWeight(it) }
        val servingCount = draft.servingCount.parseNonNegativeDouble()
        val hasPositiveAmount =
            (rawWeight != null && rawWeight.grams > 0.0) || (servingCount != null && servingCount > 0.0)
        if (!hasPositiveAmount) return null
        // A deleted sub-recipe still keeps its stored snapshot in the parent
        // recipe; fall back to it so a mere save does not fail.
        val snapshot = recipe?.let { createSnapshot(it) } ?: draft.recipeSnapshot ?: return null
        return RecipeIngredient(
            food = null,
            recipeSnapshot = snapshot,
            rawWeight = rawWeight,
            servingCount = servingCount,
        )
    }

    private fun resolveFoodIngredient(
        draft: IngredientDraft,
        foods: List<FoodItem>,
    ): RecipeIngredient? {
        val food = foods.firstOrNull { it.id.value == draft.foodId }
        val rawWeight = draft.rawWeight.parseNonNegativeDouble()?.let { RawWeight(it) }
        val servingCount = draft.servingCount.parseNonNegativeDouble()
        val hasPositiveWeight =
            (rawWeight != null && rawWeight.grams > 0.0) || (servingCount != null && servingCount > 0.0)
        val needsServingUnit = servingCount != null && rawWeight == null
        val hasServingUnit = !needsServingUnit || food?.servingUnit != null
        if (food == null || !hasPositiveWeight || !hasServingUnit) return null
        return RecipeIngredient(
            food = food,
            recipeSnapshot = null,
            rawWeight = rawWeight,
            servingCount = servingCount,
        )
    }

    private fun createSnapshot(recipe: Recipe): RecipeSnapshot {
        val totalRaw = recipe.totalRawWeight().grams
        val totalWeight = recipe.finalWeight?.grams ?: totalRaw
        val nutritionPer100g =
            recipe.nutritionPer100g()
                ?: if (totalRaw > 0.0) {
                    recipe.totalNutrition() * (100.0 / totalRaw)
                } else {
                    NutritionValues.ZERO
                }
        val gramsPerPortion = totalWeight / recipe.portions
        return RecipeSnapshot(
            recipeId = recipe.id,
            recipeName = recipe.name,
            nutritionPer100g = nutritionPer100g,
            originalLastModifiedAt = recipe.lastModifiedAt,
            portions = recipe.portions,
            gramsPerPortion = gramsPerPortion,
        )
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

        const val GENERIC_SERVING_LABEL = "Portionen"
    }
}
