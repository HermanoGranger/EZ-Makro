package com.example.ezmakro.core.domain.usecase.diary

import com.example.ezmakro.core.domain.calculation.consumedNutrition
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import java.time.LocalDate
import java.util.UUID

/**
 * Add a [FoodItem] to the meal log. The consumed [amountGrams] is the raw amount
 * of the food; the [FoodItem] snapshot is materialized by the repository so the
 * entry is an immutable historical record.
 */
class AddFoodToDiaryUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(
        food: FoodItem,
        slot: MealSlot,
        date: LocalDate,
        amountGrams: Double,
    ): EmptyResult<DataError.Local> {
        val entry =
            DiaryEntry(
                id = DiaryEntryId(UUID.randomUUID().toString()),
                slot = slot,
                date = date,
                food = food,
                recipe = null,
                amountGrams = amountGrams,
                portionCount = null,
            )
        return repository.save(entry)
    }
}

/**
 * Add a [Recipe] to the meal log. A recipe entry logs EITHER [portionCount]
 * portions OR [amountGrams] of the prepared dish — exactly one must be set,
 * matching the [DiaryEntry] invariants. The [Recipe] snapshot is materialized.
 */
class AddRecipeToDiaryUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(
        recipe: Recipe,
        slot: MealSlot,
        date: LocalDate,
        amountGrams: Double? = null,
        portionCount: Double? = null,
    ): EmptyResult<DataError.Local> {
        val entry =
            DiaryEntry(
                id = DiaryEntryId(UUID.randomUUID().toString()),
                slot = slot,
                date = date,
                food = null,
                recipe = recipe,
                amountGrams = amountGrams,
                portionCount = portionCount,
            )
        return repository.save(entry)
    }
}

/**
 * Delete a single diary entry by id.
 */
class DeleteDiaryEntryUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(id: DiaryEntryId): EmptyResult<DataError.Local> = repository.delete(id)
}

/**
 * Compute the consumed nutrition of a [DiaryEntry] from its materialized
 * snapshot, without touching the source entity. Delegates to the domain
 * [DiaryEntry.consumedNutrition] extension.
 */
class DiaryEntryNutritionUseCase {
    operator fun invoke(entry: DiaryEntry): NutritionValues = entry.consumedNutrition()
}
