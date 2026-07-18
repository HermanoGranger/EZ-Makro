package com.example.ezmakro.feature.food.presentation.search

sealed interface FoodSearchEvent {
    data class NavigateToDetail(
        val foodId: String,
    ) : FoodSearchEvent

    data object NavigateToScanner : FoodSearchEvent

    /** Open the create flow, optionally prefilled with a scanned [barcode]. */
    data class NavigateToCreate(
        val barcode: String?,
    ) : FoodSearchEvent
}
