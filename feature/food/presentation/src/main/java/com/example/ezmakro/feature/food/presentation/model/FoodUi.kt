package com.example.ezmakro.feature.food.presentation.model

import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodItem

/**
 * Presentation model for a [FoodItem] in lists and detail. Nutrition values are
 * pre-formatted strings so composables stay free of formatting logic.
 */
data class FoodUi(
    val id: String,
    val name: String,
    val barcode: String?,
    val kcalPer100g: String,
    val proteinPer100g: String,
    val fatPer100g: String,
    val carbsPer100g: String,
    val fiberPer100g: String,
    val servingLabel: String?,
    val isUserCreated: Boolean,
    val isFavorite: Boolean,
    val editStatus: FoodEditStatusUi,
)

/**
 * UI-facing projection of [CachedFoodItemStatus] (ADR-004). [None] is used for
 * user-created foods that have no OFF edit lifecycle.
 */
enum class FoodEditStatusUi {
    None,
    Original,
    UserModified,
    UpdateAvailable,
}

private const val MACRO_DECIMAL_PLACES = 10.0
private const val DASH = "\u2013"

private fun Double.formatMacro(): String {
    val rounded = Math.round(this * MACRO_DECIMAL_PLACES) / MACRO_DECIMAL_PLACES
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun Double?.formatOrDash(): String = this?.formatMacro() ?: DASH

fun FoodItem.toFoodUi(): FoodUi =
    FoodUi(
        id = id.value,
        name = name,
        barcode = barcode,
        kcalPer100g = nutritionPer100g?.kcal.formatOrDash(),
        proteinPer100g = nutritionPer100g?.proteinG.formatOrDash(),
        fatPer100g = nutritionPer100g?.fatG.formatOrDash(),
        carbsPer100g = nutritionPer100g?.carbsG.formatOrDash(),
        fiberPer100g = nutritionPer100g?.fiberG.formatOrDash(),
        servingLabel = servingUnit?.label,
        isUserCreated = isUserCreated,
        isFavorite = isFavorite,
        editStatus =
            when {
                isUserCreated -> FoodEditStatusUi.None
                else ->
                    when (cachedStatus) {
                        CachedFoodItemStatus.Original -> FoodEditStatusUi.Original
                        CachedFoodItemStatus.UserModified -> FoodEditStatusUi.UserModified
                        CachedFoodItemStatus.UpdateAvailable -> FoodEditStatusUi.UpdateAvailable
                    }
            },
    )
