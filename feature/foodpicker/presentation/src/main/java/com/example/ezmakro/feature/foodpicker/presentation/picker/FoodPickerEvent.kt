package com.example.ezmakro.feature.foodpicker.presentation.picker

import com.example.ezmakro.core.domain.model.MealSlot

sealed interface FoodPickerEvent {
    data class NavigateToFoodDetail(
        val foodId: String,
    ) : FoodPickerEvent

    data class NavigateToFoodCreate(
        val barcode: String?,
    ) : FoodPickerEvent

    data class AddToDiary(
        val foodId: String,
        val isRecipe: Boolean,
        val date: String,
        val slot: MealSlot,
        val amount: Double,
        val isGrams: Boolean,
    ) : FoodPickerEvent

    data class AddAsIngredient(
        val itemId: String,
        val itemName: String,
        val servingLabel: String?,
        val isRecipe: Boolean,
    ) : FoodPickerEvent

    data object Dismissed : FoodPickerEvent
}
