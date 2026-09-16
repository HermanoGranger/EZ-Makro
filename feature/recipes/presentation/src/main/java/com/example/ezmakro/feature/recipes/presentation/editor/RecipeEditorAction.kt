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

    data class OnAddIngredientClick(
        val foodId: String,
        val foodName: String = "",
        val servingLabel: String? = null,
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

    data class OnIngredientClick(
        val index: Int,
    ) : RecipeEditorAction

    data object OnToggleFavorite : RecipeEditorAction

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
