package com.example.ezmakro.feature.diary.presentation.model

import com.example.ezmakro.core.domain.calculation.consumedNutrition
import com.example.ezmakro.core.domain.calculation.gramsPerPortion
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.util.parseDecimalFlexible
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
    /** Weight of one portion in grams, when known; estimated for recipes without a final weight. */
    val gramsPerPortion: Double? = null,
    val gramsPerPortionEstimated: Boolean = false,
    val kcal: String,
    val protein: String,
    val fat: String,
    val carbs: String,
    val fiber: String,
    val isRecipeOverridden: Boolean = false,
    val isSourceRecipeOutdated: Boolean = false,
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
    val gramsPerPortion =
        food?.servingUnit?.gramsEquivalent
            ?: recipe?.gramsPerPortion()?.takeIf { it > 0.0 }
    val gramsPerPortionEstimated = food == null && recipe?.finalWeight == null
    return DiaryEntryUi(
        id = id.value,
        name = name,
        slot = slot,
        isRecipe = recipe != null,
        detail = detail,
        amountGrams = amountGrams,
        portionCount = portionCount,
        hasServingUnit = food?.servingUnit != null,
        gramsPerPortion = gramsPerPortion,
        gramsPerPortionEstimated = gramsPerPortionEstimated,
        kcal = nutrition.kcal.formatValue(),
        protein = nutrition.proteinG.formatValue(),
        fat = nutrition.fatG.formatValue(),
        carbs = nutrition.carbsG.formatValue(),
        fiber = nutrition.fiberG.formatValue(),
        isRecipeOverridden = recipe != null && isRecipeOverridden,
    )
}

fun List<DiaryEntryUi>.sumNutrition(): NutritionValues =
    fold(NutritionValues.ZERO) { acc, entry ->
        acc +
            NutritionValues(
                kcal = entry.kcal.parseDecimalFlexible() ?: 0.0,
                proteinG = entry.protein.parseDecimalFlexible() ?: 0.0,
                fatG = entry.fat.parseDecimalFlexible() ?: 0.0,
                carbsG = entry.carbs.parseDecimalFlexible() ?: 0.0,
                fiberG = entry.fiber.parseDecimalFlexible() ?: 0.0,
            )
    }
