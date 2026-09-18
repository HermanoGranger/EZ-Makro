package com.example.ezmakro.feature.diary.presentation.model

import com.example.ezmakro.core.domain.calculation.consumedNutrition
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.presentation.util.formatValue

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
    val amountGrams: Double?,
    val portionCount: Double?,
    val hasServingUnit: Boolean,
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

internal fun DiaryEntry.toDiaryEntryUi(): DiaryEntryUi {
    val nutrition = consumedNutrition()
    val name = food?.name ?: recipe?.name ?: ""
    val detail =
        if (food != null) {
            portionCount?.let { "${it.formatValue()} Portionen" }
                ?: amountGrams?.let { "${it.formatValue()} g" } ?: ""
        } else {
            recipe?.let { r ->
                portionCount?.let { "${it.formatValue()} Portionen" }
                    ?: amountGrams?.let { "${it.formatValue()} g" } ?: ""
            } ?: ""
        }
    return DiaryEntryUi(
        id = id.value,
        name = name,
        slot = slot,
        isRecipe = recipe != null,
        detail = detail,
        amountGrams = amountGrams,
        portionCount = portionCount,
        hasServingUnit = food?.servingUnit != null,
        kcal = nutrition.kcal.formatValue(),
        protein = nutrition.proteinG.formatValue(),
        fat = nutrition.fatG.formatValue(),
        carbs = nutrition.carbsG.formatValue(),
        fiber = nutrition.fiberG.formatValue(),
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
