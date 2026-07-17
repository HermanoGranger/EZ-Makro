package com.example.ezmakro.core.database.dao

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.example.ezmakro.core.database.DatabaseTest
import com.example.ezmakro.core.database.entity.WeightLogEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeightLogDaoTest : DatabaseTest() {
    private val dao get() = db.weightLogDao()

    private fun log(
        date: String = "2026-07-17",
        kg: Double = 80.0,
    ) = WeightLogEntity(date = date, kilograms = kg)

    @Test
    fun `upsert round-trips`() {
        runBlocking {
            dao.upsert(log())
            assertThat(dao.getById("2026-07-17")?.kilograms).isEqualTo(80.0)
        }
    }

    @Test
    fun `upsert on same date replaces prior value (unique date)`() {
        runBlocking {
            dao.upsert(log(kg = 80.0))
            dao.upsert(log(kg = 79.0))

            val range = dao.getByDateRange("2026-07-17", "2026-07-17")
            assertThat(range).hasSize(1)
            assertThat(range.first().kilograms).isEqualTo(79.0)
        }
    }

    @Test
    fun `observeLatest emits most recent entry`() =
        runTest {
            dao.upsert(log(date = "2026-07-16", kg = 80.0))
            dao.upsert(log(date = "2026-07-17", kg = 79.0))

            dao.observeLatest().test {
                val latest = awaitItem()
                assertThat(latest).isNotNull()
                assertThat(latest?.kilograms).isEqualTo(79.0)
            }
        }
}
