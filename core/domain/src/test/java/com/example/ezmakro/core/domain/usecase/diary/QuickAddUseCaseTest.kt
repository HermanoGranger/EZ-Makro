package com.example.ezmakro.core.domain.usecase.diary

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

class QuickAddUseCaseTest {
    private val date = LocalDate.of(2026, 7, 19)
    private val nutrition =
        NutritionValues(
            kcal = 250.0,
            proteinG = 20.0,
            fatG = 10.0,
            carbsG = 30.0,
            fiberG = 5.0,
        )

    private class FakeDiaryRepository(
        var shouldFail: Boolean = false,
    ) : DiaryRepository {
        var saved: DiaryEntry? = null

        override fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>> = flow { emit(emptyList()) }

        override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> = Result.Success(null)

        override suspend fun save(entry: DiaryEntry): EmptyResult<DataError.Local> {
            if (shouldFail) return Result.Error(DataError.Local.UNKNOWN)
            saved = entry
            return Result.Success(Unit)
        }

        override suspend fun delete(id: DiaryEntryId): EmptyResult<DataError.Local> = Result.Success(Unit)

        override fun observeByDateRange(range: LocalDateRange): Flow<List<DiaryEntry>> = flow { emit(emptyList()) }
    }

    private class RecordingFoodRepository : FoodRepository {
        val savedUserEdits = mutableListOf<FoodItem>()

        override fun observeAll(): Flow<List<FoodItem>> = flow { emit(emptyList()) }

        override fun observeFavorites(): Flow<List<FoodItem>> = flow { emit(emptyList()) }

        override fun observeRecent(limit: Int): Flow<List<FoodItem>> = flow { emit(emptyList()) }

        override fun searchLocal(query: String): Flow<List<FoodItem>> = flow { emit(emptyList()) }

        override suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local> = Result.Success(null)

        override fun observeById(id: FoodId): Flow<FoodItem?> = flow { emit(null) }

        override suspend fun searchRemote(query: String): Result<List<FoodItem>, DataError.Network> =
            Result.Success(emptyList())

        override suspend fun lookupByBarcode(barcode: String): Result<FoodItem?, DataError> = Result.Success(null)

        override suspend fun saveUserEdit(foodItem: FoodItem): EmptyResult<DataError.Local> {
            savedUserEdits.add(foodItem)
            return Result.Success(Unit)
        }

        override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun refetchFromOff(id: FoodId): Result<FoodItem?, DataError> = Result.Success(null)

        override suspend fun toggleFavorite(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun markUsed(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun cacheRemoteItem(item: FoodItem): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun rememberRemoteItem(item: FoodItem) = Unit

        override suspend fun getSessionItem(id: FoodId): FoodItem? = null
    }

    @Test
    fun `quick add saves transient food snapshot with slot date amount and nutrition`() =
        runBlocking {
            val repo = FakeDiaryRepository()

            QuickAddUseCase(repo)(
                name = "Eiweißshake",
                nutritionPer100g = nutrition,
                slot = MealSlot.SNACKS,
                date = date,
                amountGrams = 30.0,
            )

            val saved = repo.saved!!
            assertThat(saved.slot).isEqualTo(MealSlot.SNACKS)
            assertThat(saved.date).isEqualTo(date)
            assertThat(saved.amountGrams).isEqualTo(30.0)
            assertThat(saved.portionCount).isNull()
            val food = saved.food!!
            assertThat(food.isTransient).isTrue()
            assertThat(food.isUserCreated).isTrue()
            assertThat(food.id.value).startsWith("quick-")
            assertThat(food.name).isEqualTo("Eiweißshake")
            assertThat(food.nutritionPer100g).isEqualTo(nutrition)
            assertThat(food.cachedStatus).isEqualTo(CachedFoodItemStatus.Original)
        }

    @Test
    fun `quick add does not persist food via foodRepository`() =
        runBlocking {
            val repo = FakeDiaryRepository()
            val foodRepo = RecordingFoodRepository()

            QuickAddUseCase(repo)(
                name = "Eiweißshake",
                nutritionPer100g = nutrition,
                slot = MealSlot.SNACKS,
                date = date,
                amountGrams = 30.0,
            )

            assertThat(foodRepo.savedUserEdits).isEmpty()
        }

    @Test
    fun `quick add propagates repository error`() =
        runBlocking {
            val repo = FakeDiaryRepository(shouldFail = true)

            val result =
                QuickAddUseCase(repo)(
                    name = "Eiweißshake",
                    nutritionPer100g = nutrition,
                    slot = MealSlot.SNACKS,
                    date = date,
                    amountGrams = 30.0,
                )

            assertThat(result).isEqualTo(Result.Error(DataError.Local.UNKNOWN))
        }
}
