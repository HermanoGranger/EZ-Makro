package com.example.ezmakro.feature.food.presentation.search

sealed interface FoodSearchAction {
    data class OnQueryChange(
        val query: String,
    ) : FoodSearchAction

    data object OnSearchOnlineClick : FoodSearchAction

    data class OnFoodClick(
        val foodId: String,
    ) : FoodSearchAction

    data object OnDismissAddToDiary : FoodSearchAction

    data class OnConfirmAddToDiary(
        val slot: com.example.ezmakro.core.domain.model.MealSlot,
        val amountGrams: Double,
    ) : FoodSearchAction

    data class OnToggleFavorite(
        val foodId: String,
    ) : FoodSearchAction

    data object OnScanBarcodeClick : FoodSearchAction

    data object OnCreateOwnClick : FoodSearchAction
}
