package com.example.ezmakro.feature.food.presentation.edit

import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.util.parseDecimalFlexible
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.food.presentation.R

internal object NutritionParser {
    fun parseNutrition(
        state: FoodEditState,
        onError: (UiText) -> Unit,
    ): NutritionValues? {
        val allBlank =
            state.kcal.isBlank() &&
                state.protein.isBlank() &&
                state.fat.isBlank() &&
                state.carbs.isBlank() &&
                state.fiber.isBlank()
        if (allBlank) return null

        val anyInvalid =
            listOf(state.kcal, state.protein, state.fat, state.carbs, state.fiber)
                .any { it.isNotBlank() && !it.isValidNonNegativeNumber() }
        if (anyInvalid) {
            onError(UiText.StringResource(R.string.food_edit_error_invalid_number))
            return null
        }

        return NutritionValues(
            kcal = state.kcal.parseOrZero(),
            proteinG = state.protein.parseOrZero(),
            fatG = state.fat.parseOrZero(),
            carbsG = state.carbs.parseOrZero(),
            fiberG = state.fiber.parseOrZero(),
        )
    }

    fun parseServingUnit(portionGrams: String): ServingUnit? {
        val gramsText = portionGrams.trim()
        if (gramsText.isBlank()) return null
        val grams = gramsText.parseDecimalFlexible() ?: return null
        return if (grams > 0.0) {
            val label = "1 Portion (${grams.formatValue()} g)"
            ServingUnit(label = label, gramsEquivalent = grams)
        } else {
            null
        }
    }

    private fun String.isValidNonNegativeNumber(): Boolean =
        trim().ifBlank { "0" }.parseDecimalFlexible()?.let { it >= 0.0 } == true

    private fun String.parseOrZero(): Double = trim().ifBlank { "0" }.parseDecimalFlexible() ?: 0.0
}
