package com.example.ezmakro.feature.food.presentation.model

import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.ServingUnit

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
    val servingGrams: Double? = null,
    val servingAmount: String? = null,
    val kcalPerServing: String = DASH,
    val proteinPerServing: String = DASH,
    val fatPerServing: String = DASH,
    val carbsPerServing: String = DASH,
    val fiberPerServing: String = DASH,
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

fun FoodItem.toFoodUi(): FoodUi {
    val nutrition = nutritionPer100g
    val serving = servingUnit
    return FoodUi(
        id = id.value,
        name = name,
        barcode = barcode,
        kcalPer100g = nutrition?.kcal.formatOrDash(),
        proteinPer100g = nutrition?.proteinG.formatOrDash(),
        fatPer100g = nutrition?.fatG.formatOrDash(),
        carbsPer100g = nutrition?.carbsG.formatOrDash(),
        fiberPer100g = nutrition?.fiberG.formatOrDash(),
        servingLabel = serving?.label,
        servingGrams = serving?.gramsEquivalent,
        servingAmount = serving?.servingAmountLabel(),
        kcalPerServing = perServing(nutrition?.kcal, serving),
        proteinPerServing = perServing(nutrition?.proteinG, serving),
        fatPerServing = perServing(nutrition?.fatG, serving),
        carbsPerServing = perServing(nutrition?.carbsG, serving),
        fiberPerServing = perServing(nutrition?.fiberG, serving),
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
}

private fun perServing(
    value: Double?,
    serving: ServingUnit?,
): String {
    if (serving == null) return DASH
    return (value?.times(serving.gramsEquivalent / 100.0)).formatOrDash()
}

/**
 * Human-readable size of one serving for "Pro Portion (…)". Prefers an amount
 * already present in the label: OFF provides serving sizes like "100 ml"
 * directly, user-created servings build labels like "1 Portion (150 g)". For a
 * bare generic label such as "1 Portion" the gram equivalent is used instead.
 */
private fun ServingUnit.servingAmountLabel(): String {
    val amount =
        AMOUNT_IN_PARENS
            .find(label)
            ?.groupValues
            ?.get(1)
            ?.trim()
    if (amount != null) return amount
    if (label.equals("1 Portion", ignoreCase = true)) return "${gramsEquivalent.formatMacro()} g"
    return label
}

private val AMOUNT_IN_PARENS = Regex("\\(([^)]+)\\)")
