package com.example.ezmakro.feature.foodpicker.presentation.picker

import com.example.ezmakro.core.domain.model.MealSlot

sealed interface FoodPickerAction {
    data class OnTabSelected(
        val tab: FoodPickerTab,
    ) : FoodPickerAction

    data class OnSearchQueryChange(
        val query: String,
    ) : FoodPickerAction

    data object OnSearchOnlineClick : FoodPickerAction

    data class OnFoodClick(
        val foodId: String,
    ) : FoodPickerAction

    data class OnRecipeClick(
        val recipeId: String,
    ) : FoodPickerAction

    data class OnAddClick(
        val foodId: String,
    ) : FoodPickerAction

    data class OnAddRecipeClick(
        val recipeId: String,
    ) : FoodPickerAction

    data object OnDismissSelection : FoodPickerAction

    data object OnDismissDetail : FoodPickerAction

    data object OnDetailAddClick : FoodPickerAction

    data object OnDetailFullscreenClick : FoodPickerAction

    data class OnConfirmAddToDiary(
        val slot: MealSlot,
        val amount: Double,
        val isGrams: Boolean,
    ) : FoodPickerAction

    data object OnMyFoodsToggle : FoodPickerAction

    data class OnBarcodeDetected(
        val barcode: String,
    ) : FoodPickerAction

    data class OnPermissionResult(
        val granted: Boolean,
    ) : FoodPickerAction

    data object OnDismiss : FoodPickerAction

    data object OnScanBarcodeClick : FoodPickerAction

    data object OnCreateOwnClick : FoodPickerAction

    data object OnQuickAddClick : FoodPickerAction

    data class OnSlotSet(
        val slot: MealSlot,
    ) : FoodPickerAction

    data class OnDateSet(
        val date: String,
    ) : FoodPickerAction

    data class OnToggleFavorite(
        val foodId: String,
    ) : FoodPickerAction
}
