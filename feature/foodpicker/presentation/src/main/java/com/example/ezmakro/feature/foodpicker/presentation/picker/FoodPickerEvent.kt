package com.example.ezmakro.feature.foodpicker.presentation.picker

import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.uitext.UiText

sealed interface FoodPickerEvent {
    data class NavigateToFoodDetail(
        val foodId: String,
    ) : FoodPickerEvent

    data class NavigateToFoodCreate(
        val barcode: String?,
    ) : FoodPickerEvent

    data class NavigateToRecipeDetail(
        val recipeId: String,
    ) : FoodPickerEvent

    data class NavigateToQuickAdd(
        val date: String,
        val slot: MealSlot,
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
        val rawWeight: String,
        val servingCount: String,
    ) : FoodPickerEvent

    /** One-shot success feedback, shown as a snackbar by the hosting screen. */
    data class ShowMessage(
        val message: UiText,
    ) : FoodPickerEvent
}
