package com.example.ezmakro.core.database.dao

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.example.ezmakro.core.database.DatabaseTest
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.database.testutil.FoodItemFixtures
import com.example.ezmakro.core.database.testutil.upsertFood
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiaryEntryDaoTest : DatabaseTest() {
    private val dao get() = db.diaryEntryDao()

    private fun entry(
        id: String = "d1",
        date: String = "2026-07-17",
        slot: String = "BREAKFAST",
    ) = DiaryEntryEntity(
        id = id,
        slot = slot,
        date = date,
        foodItemId = "f1",
        recipeId = null,
        amountGrams = 150.0,
        portionCount = null,
    )

    private fun seedFoods() {
        db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
    }

    @Test
    fun `upsert and getById round-trips`() {
        seedFoods()
        runBlocking {
            dao.upsert(entry())
            assertThat(dao.getById("d1")?.amountGrams).isEqualTo(150.0)
        }
    }

    @Test
    fun `observeByDate emits entries for the day`() =
        runTest {
            seedFoods()
            dao.upsert(entry(id = "d1", slot = "BREAKFAST"))
            dao.upsert(entry(id = "d2", slot = "LUNCH"))
            dao.upsert(entry(id = "d3", date = "2026-07-18"))

            dao.observeByDate("2026-07-17").test {
                assertThat(awaitItem()).hasSize(2)
            }
        }

    @Test
    fun `observeByDateRange emits only the inclusive range ordered by date and slot`() =
        runTest {
            seedFoods()
            dao.upsert(entry(id = "d1", date = "2026-07-16"))
            dao.upsert(entry(id = "d2", date = "2026-07-17", slot = "LUNCH"))
            dao.upsert(entry(id = "d3", date = "2026-07-17", slot = "BREAKFAST"))
            dao.upsert(entry(id = "d4", date = "2026-07-18"))
            dao.upsert(entry(id = "d5", date = "2026-07-19"))

            dao.observeByDateRange("2026-07-17", "2026-07-18").test {
                assertThat(awaitItem().map { it.id }).containsExactly("d3", "d2", "d4")
            }
        }

    @Test
    fun `observeByDateRange re-emits after upsert and delete`() =
        runTest {
            seedFoods()
            dao.observeByDateRange("2026-07-17", "2026-07-18").test {
                assertThat(awaitItem()).hasSize(0)

                dao.upsert(entry(id = "d1", date = "2026-07-17"))
                assertThat(awaitItem().map { it.id }).containsExactly("d1")

                dao.deleteById("d1")
                assertThat(awaitItem()).hasSize(0)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `diary entry survives food deletion with SET NULL foreign key`() {
        seedFoods()
        runBlocking {
            dao.upsert(entry())
            // Delete the referenced food
            db.foodItemDao().deleteById("f1")
            // The diary entry must survive with a null food_item_id
            val survived = dao.getById("d1")
            assertThat(survived).isNotNull()
            assertThat(survived?.foodItemId).isNull()
            // amount_grams etc. (the actual logged data) must be preserved
            assertThat(survived?.amountGrams).isEqualTo(150.0)
        }
    }
}
