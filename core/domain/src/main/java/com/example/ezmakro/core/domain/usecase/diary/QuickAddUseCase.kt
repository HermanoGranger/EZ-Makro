package com.example.ezmakro.core.domain.usecase.diary

import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import java.time.LocalDate
import java.util.UUID

/**
 * Quick Add: log a one-off food the user describes ad-hoc (name + nutrition per
 * 100 g + consumed grams) without creating a reusable [FoodItem]. The described
 * values are materialized into a synthetic, user-created, transient [FoodItem]
 * snapshot so the resulting diary entry is a self-contained historical record
 * that is never persisted as a reusable library food.
 *
 * [servingLabel] / [servingGramsEquivalent] are optional and only used when the
 * user prefers to log by serving count instead of grams.
 */
class QuickAddUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(
        name: String,
        nutritionPer100g: NutritionValues,
        slot: MealSlot,
        date: LocalDate,
        amountGrams: Double? = null,
        portionCount: Double? = null,
        servingLabel: String? = null,
        servingGramsEquivalent: Double? = null,
    ): EmptyResult<DataError.Local> {
        val servingUnit =
            if (servingLabel != null && servingGramsEquivalent != null) {
                ServingUnit(label = servingLabel, gramsEquivalent = servingGramsEquivalent)
            } else {
                null
            }
        val food =
            FoodItem(
                id = FoodId("quick-${UUID.randomUUID()}"),
                name = name,
                barcode = null,
                nutritionPer100g = nutritionPer100g,
                servingUnit = servingUnit,
                isUserCreated = true,
                cachedStatus = CachedFoodItemStatus.Original,
                originalSnapshot = null,
                isTransient = true,
            )
        val entry =
            DiaryEntry(
                id = DiaryEntryId(UUID.randomUUID().toString()),
                slot = slot,
                date = date,
                food = food,
                recipe = null,
                amountGrams = amountGrams,
                portionCount = portionCount,
            )
        return repository.save(entry)
    }
}
