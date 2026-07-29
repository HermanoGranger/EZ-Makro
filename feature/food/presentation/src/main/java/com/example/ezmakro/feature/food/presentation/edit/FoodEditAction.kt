package com.example.ezmakro.feature.food.presentation.edit

sealed interface FoodEditAction {
    data class OnNameChange(
        val value: String,
    ) : FoodEditAction

    data class OnBarcodeChange(
        val value: String,
    ) : FoodEditAction

    data class OnKcalChange(
        val value: String,
    ) : FoodEditAction

    data class OnProteinChange(
        val value: String,
    ) : FoodEditAction

    data class OnFatChange(
        val value: String,
    ) : FoodEditAction

    data class OnCarbsChange(
        val value: String,
    ) : FoodEditAction

    data class OnFiberChange(
        val value: String,
    ) : FoodEditAction

    data class OnPortionGramsChange(
        val value: String,
    ) : FoodEditAction

    data object OnSaveClick : FoodEditAction

    data object OnBackClick : FoodEditAction
}
