package com.example.ezmakro.feature.food.presentation.edit

sealed interface FoodEditEvent {
    data object NavigateBack : FoodEditEvent

    data class Saved(
        val foodId: String,
    ) : FoodEditEvent
}
