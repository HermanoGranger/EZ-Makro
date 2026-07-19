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

    /**
     * Cross-feature "add to diary" contract: the food picker was opened from the
     * diary for [date]; selecting [foodId], [slot] and [amountGrams] logs it to
     * the meal log.
     */
    data class AddToDiary(
        val foodId: String,
        val date: String,
        val slot: com.example.ezmakro.core.domain.model.MealSlot,
        val amountGrams: Double,
    ) : FoodSearchEvent
}
