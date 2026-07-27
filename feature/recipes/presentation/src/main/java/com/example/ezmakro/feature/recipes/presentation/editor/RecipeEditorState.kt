package com.example.ezmakro.feature.recipes.presentation.editor

import com.example.ezmakro.core.presentation.uitext.UiText

/**
 * How the recipe's nutrition is divided when logging a portion of it.
 *
 * - [Portion]: the dish is split into equal servings ([RecipeEditorState.portions]);
 *   no cooked weight is required, "per portion" is derived from total / portions.
 * - [Weight]: the cooked dish has a measured [RecipeEditorState.finalWeight];
 *   "per 100 g" is derived from total / finalWeight (ADR-003). Portions become
 *   optional and are only a secondary convenience.
 *
 * The mode is driven by whether [RecipeEditorState.finalWeight] is set: choosing
 * [Weight] reveals and requires the final-weight field, choosing [Portion] clears it.
 */
enum class RecipeCalculationMode {
    Portion,
    Weight,
}

/**
 * One editable ingredient line in the recipe editor. Holds the picked [foodId]
 * and the amount expressed either as a raw weight in grams ([rawWeight]) or a
 * serving count ([servingCount]); exactly one of the two is set. [foodName] and
 * [servingLabel] are cached from the picked food so the row can render without
 * re-reading the repository.
 */
data class IngredientDraft(
    val foodId: String,
    val foodName: String,
    val servingLabel: String?,
    val rawWeight: String = "",
    val servingCount: String = "",
)

data class RecipeEditorState(
    val isEditingExisting: Boolean,
    val isQuickMeal: Boolean,
    val recipeId: String? = null,
    val name: String = "",
    val category: String = "",
    val portions: String = "1",
    val finalWeight: String = "",
    val scale: String = "1",
    val isMealPrep: Boolean = false,
    val ingredients: List<IngredientDraft> = emptyList(),
    val availableFoods: List<FoodPickerItem> = emptyList(),
    val calculationMode: RecipeCalculationMode = RecipeCalculationMode.Portion,
    val nameError: UiText? = null,
    val numberError: UiText? = null,
    val ingredientError: UiText? = null,
    val saveError: UiText? = null,
    val isSaving: Boolean = false,
)

/** A selectable food in the ingredient picker. */
data class FoodPickerItem(
    val id: String,
    val name: String,
    val servingLabel: String?,
)
