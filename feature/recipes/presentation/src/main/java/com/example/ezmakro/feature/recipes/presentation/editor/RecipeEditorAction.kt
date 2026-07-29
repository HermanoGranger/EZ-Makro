package com.example.ezmakro.feature.recipes.presentation.editor

sealed interface RecipeEditorAction {
    data class OnNameChange(
        val value: String,
    ) : RecipeEditorAction

    data class OnCategoryChange(
        val value: String,
    ) : RecipeEditorAction

    data class OnPortionsChange(
        val value: String,
    ) : RecipeEditorAction

    data class OnFinalWeightChange(
        val value: String,
    ) : RecipeEditorAction

    data class OnCalculationModeChange(
        val mode: RecipeCalculationMode,
    ) : RecipeEditorAction

    data class OnScaleChange(
        val value: String,
    ) : RecipeEditorAction

    data class OnMealPrepToggle(
        val value: Boolean,
    ) : RecipeEditorAction

    data class OnAddIngredientClick(
        val foodId: String,
    ) : RecipeEditorAction

    data class OnAddRecipeIngredientClick(
        val recipeId: String,
    ) : RecipeEditorAction

    data class OnIngredientRawWeightChange(
        val index: Int,
        val value: String,
    ) : RecipeEditorAction

    data class OnIngredientServingCountChange(
        val index: Int,
        val value: String,
    ) : RecipeEditorAction

    data class OnRemoveIngredientClick(
        val index: Int,
    ) : RecipeEditorAction

    data object OnSaveClick : RecipeEditorAction

    data object OnBackClick : RecipeEditorAction
}

sealed interface RecipeEditorEvent {
    data object Saved : RecipeEditorEvent

    data object NavigateBack : RecipeEditorEvent

    data class ShowIngredientPicker(
        val show: Boolean,
    ) : RecipeEditorEvent
}
