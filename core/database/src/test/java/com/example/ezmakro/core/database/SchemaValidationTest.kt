package com.example.ezmakro.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import com.example.ezmakro.core.database.testutil.FoodItemFixtures
import com.example.ezmakro.core.database.testutil.upsertFood
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Schema / migration harness (Phase 3).
 *
 * At version 1 there are no migrations yet; this validates that the current
 * schema creates cleanly and that the FTS4 backing table is wired to
 * [EZDatabase]. When later versions add [androidx.room.migration.Migration]s,
 * extend this class with `MigrationTestHelper`-based cases (see
 * `androidx.room.testing`).
 */
@RunWith(RobolectricTestRunner::class)
class SchemaValidationTest {
    private fun buildDb() =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                EZDatabase::class.java,
            ).allowMainThreadQueries()
            .build()

    @Test
    fun `version 1 schema creates all expected tables`() {
        val db = buildDb()
        val tables =
            db.openHelper.writableDatabase
                .query("SELECT name FROM sqlite_master WHERE type='table'")
                .use { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.moveToNext()) names += cursor.getString(0)
                    names
                }
        db.close()

        assertThat(tables).contains("food_items")
        assertThat(tables).contains("food_items_fts")
        assertThat(tables).contains("recipes")
        assertThat(tables).contains("recipe_ingredients")
        assertThat(tables).contains("diary_entries")
        assertThat(tables).contains("weight_logs")
    }

    @Test
    fun `bulk insert of 1000 foods stays under performance budget`() {
        val db = buildDb()
        val dao = db.foodItemDao()

        val start = System.nanoTime()
        repeat(1000) { i -> dao.upsertFood(FoodItemFixtures.food(id = "bulk-$i", name = "Food $i")) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        db.close()

        // Local-only bulk insert should comfortably stay under 2s on CI hardware.
        check(elapsedMs < 2000) { "Bulk insert of 1000 foods took ${elapsedMs}ms (budget 2000ms)" }
    }
}
