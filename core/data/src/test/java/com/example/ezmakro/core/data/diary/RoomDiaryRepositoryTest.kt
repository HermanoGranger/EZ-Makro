package com.example.ezmakro.core.data.diary

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.example.ezmakro.core.database.dao.DiaryEntryDao
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.domain.calculation.consumedNutrition
import com.example.ezmakro.core.domain.calculation.nutritionForGrams
import com.example.ezmakro.core.domain.calculation.nutritionPerPortion
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
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RoomDiaryRepositoryTest {
    private fun domainFood(id: String): FoodItem =
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

    private fun recipe(): Recipe =
        Recipe(
            id = RecipeId("r1"),
            name = "Recipe r1",
            ingredients =
                listOf(
                    RecipeIngredient(food = domainFood("f1"), rawWeight = RawWeight(100.0), servingCount = null),
                    RecipeIngredient(food = domainFood("f2"), rawWeight = RawWeight(200.0), servingCount = null),
                ),
            finalWeight = FinalWeight(500.0),
            portions = 2,
            scale = 1.0,
            category = null,
            isMealPrep = false,
        )

    private fun foodEntry(
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
            food = domainFood(foodId),
            recipe = null,
            amountGrams = amountGrams,
            portionCount = null,
        )

    private class FakeDiaryDao : DiaryEntryDao {
        val store = MutableStateFlow<Map<String, DiaryEntryEntity>>(emptyMap())

        override fun upsert(entity: DiaryEntryEntity) {
            store.value = store.value + (entity.id to entity)
        }

        override fun delete(entity: DiaryEntryEntity) {
            store.value = store.value - entity.id
        }

        override fun deleteById(id: String) {
            store.value = store.value - id
        }

        override fun getById(id: String): DiaryEntryEntity? = store.value[id]

        override fun observeByDate(date: String): Flow<List<DiaryEntryEntity>> =
            store.map { m -> m.values.filter { it.date == date }.sortedBy { it.slot } }

        override fun getByDateRange(
            from: String,
            to: String,
        ): List<DiaryEntryEntity> =
            store.value.values
                .filter { it.date >= from && it.date <= to }
                .sortedWith(compareBy({ it.date }, { it.slot }))
    }

    private fun repoWith(dao: FakeDiaryDao) = RoomDiaryRepository(RoomDiaryLocalDataSource(dao))

    @Test
    fun `save food entry round-trips snapshot and reads back`() =
        runTest {
            val dao = FakeDiaryDao()
            val entry = foodEntry("d1", MealSlot.BREAKFAST, LocalDate.of(2026, 7, 19), "f1", 150.0)
            repoWith(dao).save(entry)

            val stored = dao.getById("d1")
            assertThat(stored).isNotNull()
            assertThat(stored!!.foodItemId).isEqualTo("f1")
            assertThat(stored.foodSnapshot).isNotNull()
            assertThat(stored.recipeSnapshot).isNull()

            val read = repoWith(dao).getById(DiaryEntryId("d1"))
            assertThat(read).isInstanceOf(Result.Success::class)
            val entryRead = (read as Result.Success).data!!
            assertThat(entryRead.food).isNotNull()
            assertThat(entryRead.food!!.id).isEqualTo(FoodId("f1"))
            assertThat(entryRead.consumedNutrition()).isEqualTo(domainFood("f1").nutritionForGrams(150.0))
        }

    @Test
    fun `save recipe entry round-trips snapshot with ingredients`() =
        runTest {
            val dao = FakeDiaryDao()
            val r = recipe()
            val entry =
                DiaryEntry(
                    id = DiaryEntryId("d1"),
                    slot = MealSlot.DINNER,
                    date = LocalDate.of(2026, 7, 19),
                    food = null,
                    recipe = r,
                    amountGrams = null,
                    portionCount = 1.0,
                )
            repoWith(dao).save(entry)

            val read = repoWith(dao).getById(DiaryEntryId("d1"))
            assertThat(read).isInstanceOf(Result.Success::class)
            val entryRead = (read as Result.Success).data!!
            assertThat(entryRead.recipe).isNotNull()
            assertThat(entryRead.recipe!!.ingredients).hasSize(2)
            assertThat(entryRead.consumedNutrition()).isEqualTo(r.nutritionPerPortion() * 1.0)
        }

    @Test
    fun `observeByDate returns only entries of that date ordered by slot`() =
        runTest {
            val dao = FakeDiaryDao()
            repoWith(dao).save(foodEntry("d1", MealSlot.BREAKFAST, LocalDate.of(2026, 7, 19), "f1", 100.0))
            repoWith(dao).save(foodEntry("d2", MealSlot.LUNCH, LocalDate.of(2026, 7, 19), "f1", 100.0))
            repoWith(dao).save(foodEntry("d3", MealSlot.BREAKFAST, LocalDate.of(2026, 7, 18), "f1", 100.0))

            val day = repoWith(dao).observeByDate(LocalDate.of(2026, 7, 19)).first()
            assertThat(day).hasSize(2)
            assertThat(day.map { it.id.value }).containsExactly("d1", "d2")
            assertThat(day.map { it.slot }).containsExactly(MealSlot.BREAKFAST, MealSlot.LUNCH)
        }

    @Test
    fun `delete removes the entry`() =
        runTest {
            val dao = FakeDiaryDao()
            repoWith(dao).save(foodEntry("d1", MealSlot.BREAKFAST, LocalDate.of(2026, 7, 19), "f1", 100.0))
            repoWith(dao).delete(DiaryEntryId("d1"))

            val read = repoWith(dao).getById(DiaryEntryId("d1"))
            assertThat(read).isInstanceOf(Result.Success::class)
            assertThat((read as Result.Success).data).isNull()
        }

    @Test
    fun `getHistory returns entries within range newest first`() =
        runTest {
            val dao = FakeDiaryDao()
            repoWith(dao).save(foodEntry("d1", MealSlot.BREAKFAST, LocalDate.of(2026, 7, 19), "f1", 100.0))
            repoWith(dao).save(foodEntry("d2", MealSlot.BREAKFAST, LocalDate.of(2026, 7, 18), "f1", 100.0))

            val history = repoWith(dao).getHistory(LocalDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 19)))
            assertThat(history).isInstanceOf(Result.Success::class)
            val ids =
                when (history) {
                    is Result.Success -> history.data.map { it.id.value }
                    is Result.Error -> emptyList()
                }
            assertThat(ids).containsExactly("d2", "d1")
        }
}
