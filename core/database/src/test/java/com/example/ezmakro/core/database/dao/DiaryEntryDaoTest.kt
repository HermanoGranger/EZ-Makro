package com.example.ezmakro.core.database.dao

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
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
    fun `getByDateRange returns inclusive range`() {
        seedFoods()
        runBlocking {
            dao.upsert(entry(id = "d1", date = "2026-07-16"))
            dao.upsert(entry(id = "d2", date = "2026-07-17"))
            dao.upsert(entry(id = "d3", date = "2026-07-18"))

            val range = dao.getByDateRange("2026-07-17", "2026-07-18")
            assertThat(range.map { it.id }).containsExactlyInAnyOrder("d2", "d3")
        }
    }
}
