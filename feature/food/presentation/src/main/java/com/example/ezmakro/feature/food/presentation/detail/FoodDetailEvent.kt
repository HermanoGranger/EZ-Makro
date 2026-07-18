package com.example.ezmakro.feature.food.presentation.detail

import com.example.ezmakro.core.presentation.uitext.UiText

sealed interface FoodDetailEvent {
    data object NavigateBack : FoodDetailEvent

    data class NavigateToEdit(
        val foodId: String,
    ) : FoodDetailEvent

    data class ShowMessage(
        val message: UiText,
    ) : FoodDetailEvent
}
