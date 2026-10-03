package com.example.ezmakro.core.domain.usecase.diary

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

private typealias ER<E> = com.example.ezmakro.core.domain.result.EmptyResult<E>

class AddRecipeToDiaryUseCaseTest {
    private val date = LocalDate.of(2026, 7, 19)
    private val recipe =
        Recipe(
            id = RecipeId("r1"),
            name = "Pfannkuchen",
            ingredients =
                listOf(
                    RecipeIngredient(
                        food =
                            FoodItem(
                                id = FoodId("f1"),
                                name = "Ei",
                                barcode = null,
                                nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
                                servingUnit = null,
                                isUserCreated = false,
                                cachedStatus = CachedFoodItemStatus.Original,
                                originalSnapshot = null,
                            ),
                        rawWeight = RawWeight(100.0),
                        servingCount = null,
                    ),
                ),
            finalWeight = FinalWeight(200.0),
            portions = 2,
        )

    private class FakeDiaryRepository : DiaryRepository {
        var saved: DiaryEntry? = null

        override fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>> = flow { emit(emptyList()) }

        override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> = Result.Success(null)

        override suspend fun save(entry: DiaryEntry): EmptyResult<DataError.Local> {
            saved = entry
            return Result.Success(Unit)
        }

        override suspend fun delete(id: DiaryEntryId): EmptyResult<DataError.Local> = Result.Success(Unit)

        override fun observeByDateRange(range: LocalDateRange): Flow<List<DiaryEntry>> = flow { emit(emptyList()) }
    }

    @Test
    fun `add recipe logs portion count and keeps recipe snapshot`() =
        runBlocking {
            val repo = FakeDiaryRepository()

            AddRecipeToDiaryUseCase(repo)(recipe, MealSlot.DINNER, date, portionCount = 3.0)

            val saved = repo.saved!!
            assertThat(saved.recipe?.id).isEqualTo(RecipeId("r1"))
            assertThat(saved.food).isNull()
            assertThat(saved.portionCount).isEqualTo(3.0)
            assertThat(saved.amountGrams).isNull()
            assertThat(saved.slot).isEqualTo(MealSlot.DINNER)
            assertThat(saved.date).isEqualTo(date)
        }

    @Test
    fun `add recipe logs amountGrams when provided`() =
        runBlocking {
            val repo = FakeDiaryRepository()

            AddRecipeToDiaryUseCase(repo)(recipe, MealSlot.BREAKFAST, date, amountGrams = 50.0)

            val saved = repo.saved!!
            assertThat(saved.recipe?.id).isEqualTo(RecipeId("r1"))
            assertThat(saved.food).isNull()
            assertThat(saved.portionCount).isNull()
            assertThat(saved.amountGrams).isEqualTo(50.0)
            assertThat(saved.slot).isEqualTo(MealSlot.BREAKFAST)
            assertThat(saved.date).isEqualTo(date)
        }
}
