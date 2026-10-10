package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.calculation.nutrition
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.domain.model.markChangedRecipeSnapshots
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.domain.usecase.recipe.BuildRecipeFromDraftsUseCase
import com.example.ezmakro.core.domain.util.NumberInput
import com.example.ezmakro.core.domain.util.parsePositiveDecimalInput
import com.example.ezmakro.core.domain.util.parsePositiveInt
import com.example.ezmakro.core.domain.util.valueOrNull
import com.example.ezmakro.core.presentation.recipe.RECIPE_SERVING_LABEL
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.recipes.presentation.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Suppress("TooManyFunctions")
class RecipeEditorViewModel(
    private val recipeId: String?,
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository,
    private val buildRecipeFromDraftsUseCase: BuildRecipeFromDraftsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredIngredients: List<RecipeIngredientDraft>? = restoreIngredients()

    private val _state =
        MutableStateFlow(
            RecipeEditorState(
                isEditingExisting = recipeId != null,
                recipeId = recipeId,
                name = (savedStateHandle[KEY_NAME] as String?).orEmpty(),
                category = (savedStateHandle[KEY_CATEGORY] as String?).orEmpty(),
                portions = (savedStateHandle[KEY_PORTIONS] as String?) ?: "1",
                finalWeight = (savedStateHandle[KEY_FINAL_WEIGHT] as String?).orEmpty(),
                ingredients = restoredIngredients.orEmpty(),
                isFavorite = savedStateHandle[KEY_FAVORITE] ?: false,
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
                recomputeNutrition()
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
                recomputeNutrition()
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

            is RecipeEditorAction.OnAddRecipeIngredientClick ->
                addRecipeIngredient(action.recipeId, action.rawWeight, action.servingCount)

            is RecipeEditorAction.OnIngredientRawWeightChange -> {
                _state.update {
                    it.copy(
                        ingredients =
                            it.ingredients.updateAt(action.index) { d ->
                                d.copy(rawWeight = action.value)
                            },
                        ingredientError = null,
                    )
                }
                persistIngredients()
                recomputeNutrition()
            }

            is RecipeEditorAction.OnIngredientServingCountChange -> {
                _state.update {
                    it.copy(
                        ingredients =
                            it.ingredients.updateAt(action.index) { d ->
                                d.copy(servingCount = action.value)
                            },
                        ingredientError = null,
                    )
                }
                persistIngredients()
                recomputeNutrition()
            }

            is RecipeEditorAction.OnRemoveIngredientClick -> {
                _state.update {
                    it.copy(
                        ingredients = it.ingredients.filterIndexed { i, _ -> i != action.index },
                        expandedIngredientIndex = null,
                    )
                }
                persistIngredients()
                recomputeNutrition()
            }

            is RecipeEditorAction.OnIngredientClick ->
                _state.update {
                    it.copy(
                        expandedIngredientIndex =
                            if (it.expandedIngredientIndex == action.index) null else action.index,
                    )
                }

            RecipeEditorAction.OnToggleFavorite -> {
                _state.update { it.copy(isFavorite = !it.isFavorite) }
                savedStateHandle[KEY_FAVORITE] = _state.value.isFavorite
            }

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
                        RecipeIngredientDraft(
                            foodId = action.foodId,
                            foodName = foodName,
                            servingLabel = servingLabel,
                            rawWeight = action.rawWeight,
                            servingCount = action.servingCount,
                        ),
                ingredientError = null,
            )
        }
        persistIngredients()
        recomputeNutrition()
    }

    private fun addRecipeIngredient(
        recipeId: String,
        rawWeight: String,
        servingCount: String,
    ) {
        val recipe = recipesState.value.firstOrNull { it.id.value == recipeId } ?: return
        _state.update {
            it.copy(
                ingredients =
                    it.ingredients +
                        RecipeIngredientDraft(
                            foodId = recipeId,
                            foodName = recipe.name,
                            servingLabel = RECIPE_SERVING_LABEL,
                            rawWeight = rawWeight,
                            servingCount = servingCount,
                            recipeId = recipeId,
                        ),
                ingredientError = null,
            )
        }
        persistIngredients()
        recomputeNutrition()
    }

    /**
     * Seed the form from the stored recipe **once**. `observeById` is backed by
     * the whole `recipes` table, so a long-lived collector would overwrite the
     * draft on every unrelated write — `markUsed` from the ingredient picker,
     * `toggleFavorite`, saving this very recipe — and added ingredients or typed
     * values would vanish. Later table emissions stay with the
     * `recipeRepository.observeAll()` collector from `init`, which keeps
     * `availableRecipes`, [checkSnapshotChanges] and [recomputeNutrition] live
     * without touching the form.
     */
    private fun loadExisting(id: String) {
        recipeRepository
            .observeById(RecipeId(id))
            .take(1)
            .onEach { recipe ->
                if (recipe == null) return@onEach
                _state.update {
                    it.copy(
                        name = it.name.ifEmpty { recipe.name },
                        category =
                            if (restoredField(KEY_CATEGORY)) it.category else recipe.category ?: "",
                        portions = if (restoredField(KEY_PORTIONS)) it.portions else recipe.portions.toString(),
                        finalWeight =
                            if (restoredField(KEY_FINAL_WEIGHT)) {
                                it.finalWeight
                            } else {
                                recipe.finalWeight
                                    ?.grams
                                    ?.formatValue()
                                    .orEmpty()
                            },
                        isFavorite = if (restoredField(KEY_FAVORITE)) it.isFavorite else recipe.isFavorite,
                        ingredients =
                            restoredIngredients ?: recipe.ingredients.map { ing ->
                                RecipeIngredientDraft(
                                    foodId =
                                        ing.food
                                            ?.id
                                            ?.value
                                            .orEmpty(),
                                    foodName = ing.food?.name ?: ing.recipeSnapshot?.recipeName.orEmpty(),
                                    servingLabel =
                                        ing.food?.servingUnit?.label
                                            ?: if (ing.recipeSnapshot != null) RECIPE_SERVING_LABEL else null,
                                    rawWeight =
                                        ing.rawWeight
                                            ?.grams
                                            ?.formatValue()
                                            .orEmpty(),
                                    servingCount = ing.servingCount?.formatValue().orEmpty(),
                                    recipeId = ing.recipeSnapshot?.recipeId?.value,
                                    originalLastModifiedAt = ing.recipeSnapshot?.originalLastModifiedAt,
                                    recipeSnapshot = ing.recipeSnapshot,
                                )
                            },
                    )
                }
                checkSnapshotChanges()
                recomputeNutrition()
            }.launchIn(viewModelScope)
    }

    private fun recomputeNutrition() {
        val foods = foodsState.value
        val recipes = recipesState.value
        // Resolved draft by draft: an unresolvable draft contributes ZERO instead
        // of dropping the nutrition of all drafts around it.
        val ingredientNutrition =
            _state.value.ingredients.map { draft ->
                val resolved = buildRecipeFromDraftsUseCase(listOf(draft), foods, recipes)
                when (resolved) {
                    is Result.Success -> resolved.data.firstOrNull()?.nutrition() ?: NutritionValues.ZERO
                    is Result.Error -> NutritionValues.ZERO
                }
            }
        val total =
            ingredientNutrition.fold(NutritionValues.ZERO) { acc, value -> acc + value }
        _state.update {
            it.copy(
                ingredientNutrition = ingredientNutrition,
                totalNutrition = total,
            )
        }
    }

    private fun checkSnapshotChanges() {
        val recipes = recipesState.value
        _state.update { current ->
            current.copy(
                ingredients = current.ingredients.markChangedRecipeSnapshots(recipes),
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
            val resolved =
                buildRecipeFromDraftsUseCase(
                    drafts = s.ingredients,
                    foods = foodsState.value,
                    recipes = recipesState.value,
                )
            val ingredients =
                when (resolved) {
                    is Result.Success -> resolved.data
                    is Result.Error -> {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                ingredientError =
                                    UiText.StringResource(R.string.recipe_editor_error_ingredient_weight),
                            )
                        }
                        return
                    }
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
        // `FinalWeight` fordert `grams > 0` — `0` muss als Fehler enden, nicht als
        // `require`-Verletzung. Ein leeres Feld bleibt ein optionaler Wert.
        val finalWeight = s.finalWeight.parsePositiveDecimalInput()
        if (portions == null || finalWeight is NumberInput.Invalid) {
            _state.update {
                it.copy(
                    isSaving = false,
                    numberError = UiText.StringResource(R.string.recipe_editor_error_invalid_number),
                )
            }
            return null
        }
        return ParsedSaveInput(
            portions = portions,
            finalWeight = finalWeight.valueOrNull?.let { FinalWeight(it) },
        )
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

    private fun <T> List<T>.updateAt(
        index: Int,
        block: (T) -> T,
    ): List<T> = mapIndexed { i, item -> if (i == index) block(item) else item }

    private fun restoredField(key: String): Boolean = savedStateHandle.contains(key)

    private fun restoreIngredients(): List<RecipeIngredientDraft>? =
        savedStateHandle.get<String>(KEY_INGREDIENTS)?.let { raw ->
            runCatching {
                INGREDIENTS_JSON.decodeFromString<List<RecipeIngredientDraft>>(raw)
            }.getOrNull()
        }

    private fun persistIngredients() {
        savedStateHandle[KEY_INGREDIENTS] = INGREDIENTS_JSON.encodeToString(_state.value.ingredients)
    }

    private companion object {
        const val KEY_NAME = "recipe_edit_name"
        const val KEY_CATEGORY = "recipe_edit_category"
        const val KEY_PORTIONS = "recipe_edit_portions"
        const val KEY_FINAL_WEIGHT = "recipe_edit_final_weight"
        const val KEY_INGREDIENTS = "recipe_edit_ingredients"
        const val KEY_FAVORITE = "recipe_edit_favorite"
        val INGREDIENTS_JSON: Json = Json { ignoreUnknownKeys = true }
    }
}
