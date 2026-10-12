package com.example.ezmakro.core.domain.usecase.diary

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

private typealias EmptyResult<E> = com.example.ezmakro.core.domain.result.EmptyResult<E>

class CopyYesterdayUseCaseTest {
    private val today = LocalDate.of(2026, 7, 19)
    private val yesterday = LocalDate.of(2026, 7, 18)

    private fun food(id: String): FoodItem =
        FoodItem(
            id = FoodId(id),
            name = "Food $id",
            barcode = null,
            nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
            servingUnit = null,
            isUserCreated = true,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )

    private fun entry(
        id: String,
        slot: MealSlot,
        date: LocalDate,
        foodId: String,
        amountGrams: Double,
    ): DiaryEntry =
        DiaryEntry(
            id = DiaryEntryId(id),
            slot = slot,
            date = date,
            food = food(foodId),
            recipe = null,
            amountGrams = amountGrams,
            portionCount = null,
        )

    private fun recipe(id: String): Recipe =
        Recipe(
            id = RecipeId(id),
            name = "Recipe $id",
            ingredients =
                listOf(
                    RecipeIngredient(
                        food = food("f1"),
                        recipeSnapshot = null,
                        rawWeight = RawWeight(100.0),
                        servingCount = null,
                    ),
                ),
            finalWeight = FinalWeight(250.0),
            portions = 2,
        )

    private fun entry(
        id: String,
        slot: MealSlot,
        date: LocalDate,
        recipe: Recipe,
        portionCount: Double,
        isRecipeOverridden: Boolean,
    ): DiaryEntry =
        DiaryEntry(
            id = DiaryEntryId(id),
            slot = slot,
            date = date,
            food = null,
            recipe = recipe,
            amountGrams = null,
            portionCount = portionCount,
            isRecipeOverridden = isRecipeOverridden,
        )

    private class FakeDiaryRepository : DiaryRepository {
        val entries = mutableListOf<DiaryEntry>()

        override fun observeByDate(date: LocalDate): kotlinx.coroutines.flow.Flow<List<DiaryEntry>> =
            kotlinx.coroutines.flow.flow { emit(entries.filter { it.date == date }) }

        override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> =
            Result.Success(entries.firstOrNull { it.id == id })

        override suspend fun save(entry: DiaryEntry): EmptyResult<DataError.Local> {
            entries.removeIf { it.id == entry.id }
            entries.add(entry)
            return Result.Success(Unit)
        }

        override suspend fun delete(id: DiaryEntryId): EmptyResult<DataError.Local> {
            entries.removeIf { it.id == id }
            return Result.Success(Unit)
        }

        override fun observeByDateRange(range: LocalDateRange): kotlinx.coroutines.flow.Flow<List<DiaryEntry>> =
            kotlinx.coroutines.flow.flow {
                emit(
                    entries
                        .filter { !it.date.isBefore(range.from) && !it.date.isAfter(range.to) }
                        .sortedWith(compareBy({ it.date }, { it.slot })),
                )
            }
    }

    @Test
    fun `copy yesterday copies all source entries into target with fresh ids`() =
        runBlocking {
            val repo = FakeDiaryRepository()
            repo.entries +=
                listOf(
                    entry("a", MealSlot.BREAKFAST, yesterday, "f1", 100.0),
                    entry("b", MealSlot.LUNCH, yesterday, "f2", 150.0),
                    entry("c", MealSlot.DINNER, yesterday, "f3", 200.0),
                    entry("d", MealSlot.SNACKS, yesterday, "f4", 50.0),
                    entry("e", MealSlot.BREAKFAST, today, "f9", 10.0),
                )

            val result = CopyYesterdayUseCase(repo)(today)

            assertThat(result).isInstanceOf(Result.Success::class)
            assertThat(result).isEqualTo(Result.Success(4))
            val target = repo.entries.filter { it.date == today }
            assertThat(target).hasSize(1 + 4)
            val copied = target.filter { it.id.value != "e" }
            assertThat(copied).hasSize(4)
            assertThat(copied.map { it.slot })
                .containsExactly(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.SNACKS)
            assertThat(copied.map { it.date }).containsExactly(today, today, today, today)
            assertThat(copied.none { it.id.value in setOf("a", "b", "c", "d") }).isEqualTo(true)
        }

    @Test
    fun `copy yesterday with no source entries copies nothing`() =
        runBlocking {
            val repo = FakeDiaryRepository()
            val result = CopyYesterdayUseCase(repo)(today)
            assertThat(result).isEqualTo(Result.Success(0))
        }

    @Test
    fun `copy yesterday preserves the recipe override flag`() =
        runBlocking {
            val repo = FakeDiaryRepository()
            repo.entries +=
                listOf(
                    entry("a", MealSlot.LUNCH, yesterday, recipe("r1"), 1.0, isRecipeOverridden = true),
                    entry("b", MealSlot.DINNER, yesterday, recipe("r2"), 2.0, isRecipeOverridden = false),
                )

            val result = CopyYesterdayUseCase(repo)(today)

            assertThat(result).isEqualTo(Result.Success(2))
            val copied = repo.entries.filter { it.date == today && it.id.value !in setOf("a", "b") }
            assertThat(copied).hasSize(2)
            val overriddenEntry = copied.first { it.recipe?.id?.value == "r1" }
            val untouchedEntry = copied.first { it.recipe?.id?.value == "r2" }
            assertThat(overriddenEntry.isRecipeOverridden).isTrue()
            assertThat(untouchedEntry.isRecipeOverridden).isEqualTo(false)
        }
}
