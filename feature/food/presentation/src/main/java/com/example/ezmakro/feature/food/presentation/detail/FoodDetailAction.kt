package com.example.ezmakro.feature.food.presentation.detail

sealed interface FoodDetailAction {
    data object OnBackClick : FoodDetailAction

    data object OnEditClick : FoodDetailAction

    data object OnToggleFavorite : FoodDetailAction

    data object OnResetToOriginalClick : FoodDetailAction

    data object OnRefetchClick : FoodDetailAction

    data object OnRefetchConfirm : FoodDetailAction

    data object OnRefetchDismiss : FoodDetailAction
}
