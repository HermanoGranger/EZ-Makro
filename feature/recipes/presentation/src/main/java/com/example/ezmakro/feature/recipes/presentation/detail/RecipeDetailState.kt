package com.example.ezmakro.feature.recipes.presentation.detail

import com.example.ezmakro.core.presentation.uitext.UiText

data class RecipeDetailState(
    val recipe: com.example.ezmakro.feature.recipes.presentation.model.RecipeUi? = null,
    val isLoading: Boolean = true,
    val deleteError: UiText? = null,
)

sealed interface RecipeDetailAction {
    data object OnEditClick : RecipeDetailAction

    data object OnDuplicateClick : RecipeDetailAction

    data object OnDeleteClick : RecipeDetailAction

    data object OnBackClick : RecipeDetailAction
}

sealed interface RecipeDetailEvent {
    data object NavigateBack : RecipeDetailEvent

    data class NavigateToEditor(
        val recipeId: String,
    ) : RecipeDetailEvent
}
