package com.example.ezmakro.diary

import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.usecase.diary.AddFoodToDiaryUseCase
import com.example.ezmakro.core.domain.usecase.diary.AddRecipeToDiaryUseCase
import java.time.LocalDate

/**
 * App-level orchestration for every "add to diary" entry point. Resolves the
 * referenced [FoodItem]/[Recipe], persists online-only foods before the diary
 * references them and reports the outcome so the caller can navigate or surface
 * a failure instead of pretending the entry was written.
 *
 * Kept out of the Activity so the resolution/error handling is unit-testable
 * without an Android runtime.
 */
class DiaryEntryAdder(
    private val foodRepository: FoodRepository,
    private val recipeRepository: RecipeRepository,
    private val addFoodToDiary: AddFoodToDiaryUseCase,
    private val addRecipeToDiary: AddRecipeToDiaryUseCase,
) {
    /** Entry point for the food picker, which may add either a food or a recipe. */
    suspend fun addFromPicker(
        itemId: String,
        isRecipe: Boolean,
        date: LocalDate,
        slot: MealSlot,
        amount: Double,
        isGrams: Boolean,
    ): AddToDiaryOutcome =
        if (isRecipe) {
            addRecipe(itemId, date, slot, amount, isGrams)
        } else {
            addFood(itemId, date, slot, amount, isGrams)
        }

    suspend fun addFood(
        foodId: String,
        date: LocalDate,
        slot: MealSlot,
        amount: Double,
        isGrams: Boolean,
    ): AddToDiaryOutcome {
        val food =
            when (val result = foodRepository.getById(FoodId(foodId))) {
                is Result.Success -> result.data
                is Result.Error -> null
            } ?: foodRepository.getSessionItem(FoodId(foodId))
                ?: return AddToDiaryOutcome.NotFound
        // Session-only online items are not stored yet; persist them before the
        // diary references them, otherwise the entry is silently dropped.
        foodRepository.cacheRemoteItem(food)
        return toOutcome(writeFood(food, slot, date, amount, isGrams))
    }

    suspend fun addRecipe(
        recipeId: String,
        date: LocalDate,
        slot: MealSlot,
        amount: Double,
        isGrams: Boolean,
    ): AddToDiaryOutcome {
        val recipe =
            when (val result = recipeRepository.getById(RecipeId(recipeId))) {
                is Result.Success -> result.data
                is Result.Error -> null
            } ?: return AddToDiaryOutcome.NotFound
        return toOutcome(writeRecipe(recipe, slot, date, amount, isGrams))
    }

    private suspend fun writeFood(
        food: FoodItem,
        slot: MealSlot,
        date: LocalDate,
        amount: Double,
        isGrams: Boolean,
    ): EmptyResult<DataError.Local> =
        if (isGrams) {
            addFoodToDiary(food, slot, date, amountGrams = amount)
        } else {
            addFoodToDiary(food, slot, date, portionCount = amount)
        }

    private suspend fun writeRecipe(
        recipe: Recipe,
        slot: MealSlot,
        date: LocalDate,
        amount: Double,
        isGrams: Boolean,
    ): EmptyResult<DataError.Local> =
        if (isGrams) {
            addRecipeToDiary(recipe, slot, date, amountGrams = amount)
        } else {
            addRecipeToDiary(recipe, slot, date, portionCount = amount)
        }

    private fun toOutcome(result: EmptyResult<DataError.Local>): AddToDiaryOutcome =
        when (result) {
            is Result.Success -> AddToDiaryOutcome.Added
            is Result.Error -> AddToDiaryOutcome.Failed
        }

    enum class AddToDiaryOutcome {
        /** The diary entry was written. */
        Added,

        /** The referenced food/recipe could not be resolved (deleted or read error). */
        NotFound,

        /** The diary write failed. */
        Failed,
    }
}
