package com.example.ezmakro.feature.diary.presentation.model

import com.example.ezmakro.core.domain.calculation.consumedNutrition
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues

/**
 * Presentation model for a single diary entry. Nutrition is pre-computed from
 * the entry's materialized snapshot so composables stay free of math and of any
 * dependency on the source food/recipe entity.
 */
data class DiaryEntryUi(
    val id: String,
    val name: String,
    val slot: MealSlot,
    val isRecipe: Boolean,
    val detail: String,
    val kcal: String,
    val protein: String,
    val fat: String,
    val carbs: String,
    val fiber: String,
)

/**
 * Aggregate nutrition per [MealSlot] (and the day total), pre-formatted.
 */
data class SlotSummaryUi(
    val slot: MealSlot?,
    val title: String,
    val kcal: String,
    val protein: String,
    val fat: String,
    val carbs: String,
    val fiber: String,
)

internal const val MACRO_DECIMAL_PLACES = 10.0

internal fun Double.formatMacro(): String {
    val rounded = Math.round(this * MACRO_DECIMAL_PLACES) / MACRO_DECIMAL_PLACES
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

fun DiaryEntry.toDiaryEntryUi(): DiaryEntryUi {
    val nutrition = consumedNutrition()
    val name = food?.name ?: recipe?.name ?: ""
    val detail =
        if (food != null) {
            portionCount?.let { "${it.formatMacro()} Portionen" }
                ?: amountGrams?.let { "${it.formatMacro()} g" } ?: ""
        } else {
            recipe?.let { r ->
                portionCount?.let { "${it.formatMacro()} Portionen" }
                    ?: amountGrams?.let { "${it.formatMacro()} g" } ?: ""
            } ?: ""
        }
    return DiaryEntryUi(
        id = id.value,
        name = name,
        slot = slot,
        isRecipe = recipe != null,
        detail = detail,
        kcal = nutrition.kcal.formatMacro(),
        protein = nutrition.proteinG.formatMacro(),
        fat = nutrition.fatG.formatMacro(),
        carbs = nutrition.carbsG.formatMacro(),
        fiber = nutrition.fiberG.formatMacro(),
    )
}

fun List<DiaryEntryUi>.sumNutrition(): NutritionValues =
    fold(NutritionValues.ZERO) { acc, entry ->
        acc +
            NutritionValues(
                kcal = entry.kcal.toDoubleOrNull() ?: 0.0,
                proteinG = entry.protein.toDoubleOrNull() ?: 0.0,
                fatG = entry.fat.toDoubleOrNull() ?: 0.0,
                carbsG = entry.carbs.toDoubleOrNull() ?: 0.0,
                fiberG = entry.fiber.toDoubleOrNull() ?: 0.0,
            )
    }
