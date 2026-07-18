package com.example.ezmakro.feature.recipes.presentation.list

data class RecipeListState(
    val recipes: List<com.example.ezmakro.feature.recipes.presentation.model.RecipeUi> = emptyList(),
    val isLoading: Boolean = false,
)

sealed interface RecipeListAction {
    data class OnRecipeClick(
        val recipeId: String,
    ) : RecipeListAction

    data object OnCreateClick : RecipeListAction

    data object OnCreateQuickMealClick : RecipeListAction
}

sealed interface RecipeListEvent {
    data class NavigateToDetail(
        val recipeId: String,
    ) : RecipeListEvent

    data class NavigateToEditor(
        val recipeId: String?,
    ) : RecipeListEvent
}
