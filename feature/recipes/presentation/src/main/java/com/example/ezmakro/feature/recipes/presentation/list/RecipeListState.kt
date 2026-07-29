package com.example.ezmakro.feature.recipes.presentation.list

import com.example.ezmakro.core.domain.model.MealSlot

data class RecipeListState(
    val recipes: List<com.example.ezmakro.feature.recipes.presentation.model.RecipeUi> = emptyList(),
    val isLoading: Boolean = false,
    val pendingAddRecipeId: String? = null,
)

sealed interface RecipeListAction {
    data class OnRecipeClick(
        val recipeId: String,
    ) : RecipeListAction

    data object OnCreateClick : RecipeListAction

    data object OnCreateQuickMealClick : RecipeListAction

    data object OnDismissAddToDiary : RecipeListAction

    data class OnConfirmAddToDiary(
        val slot: MealSlot,
        val amount: Double,
        val isGrams: Boolean = false,
    ) : RecipeListAction
}

sealed interface RecipeListEvent {
    data class NavigateToDetail(
        val recipeId: String,
    ) : RecipeListEvent

    data class NavigateToEditor(
        val recipeId: String?,
    ) : RecipeListEvent

    data class AddToDiary(
        val recipeId: String,
        val date: String,
        val slot: MealSlot,
        val amount: Double,
        val isGrams: Boolean = false,
    ) : RecipeListEvent
}
