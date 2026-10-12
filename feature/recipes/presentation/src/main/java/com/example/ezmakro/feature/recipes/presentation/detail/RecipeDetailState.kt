package com.example.ezmakro.feature.recipes.presentation.detail

import com.example.ezmakro.core.presentation.uitext.UiText

data class IngredientDetailUi(
    val name: String,
    val amountDisplay: String,
    val kcal: String,
    val proteinG: String,
    val fatG: String,
    val carbsG: String,
    val fiberG: String,
)

data class RecipeDetailState(
    val recipe: com.example.ezmakro.feature.recipes.presentation.model.RecipeUi? = null,
    val ingredients: List<IngredientDetailUi> = emptyList(),
    val expandedIngredientIndex: Int? = null,
    val isLoading: Boolean = true,
    val deleteError: UiText? = null,
    val duplicateError: UiText? = null,
)

sealed interface RecipeDetailAction {
    data object OnEditClick : RecipeDetailAction

    data object OnDuplicateClick : RecipeDetailAction

    data object OnDeleteClick : RecipeDetailAction

    data object OnToggleFavorite : RecipeDetailAction

    data object OnBackClick : RecipeDetailAction

    data class OnIngredientClick(
        val index: Int,
    ) : RecipeDetailAction
}

sealed interface RecipeDetailEvent {
    data object NavigateBack : RecipeDetailEvent

    data class NavigateToEditor(
        val recipeId: String,
    ) : RecipeDetailEvent
}
