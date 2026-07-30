package com.example.ezmakro.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.database.migration.MIGRATION_1_2
import com.example.ezmakro.core.database.migration.MIGRATION_2_3
import com.example.ezmakro.core.database.migration.MIGRATION_5_6
import com.example.ezmakro.core.database.migration.MIGRATION_6_7
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the schema migrations using the exported schema JSONs under `schemas/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            EZDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    private val dbName = "migration-test.db"

    @Test
    fun `migrate 1 to 2 adds favorites and last-used columns preserving rows`() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO food_items (
                    id, name, barcode,
                    nutri_kcal, nutri_protein_g, nutri_fat_g, nutri_carbs_g, nutri_fiber_g,
                    serving_label, serving_grams_equivalent, is_user_created, cached_status,
                    orig_name, orig_barcode, orig_nutri_kcal, orig_nutri_protein_g,
                    orig_nutri_fat_g, orig_nutri_carbs_g, orig_nutri_fiber_g,
                    orig_serving_label, orig_serving_grams_equivalent, orig_is_user_created
                ) VALUES (
                    'a', 'Apfel', NULL,
                    52.0, 0.3, 0.2, 14.0, 2.4,
                    NULL, NULL, 0, 'ORIGINAL',
                    'Apfel', NULL, 52.0, 0.3,
                    0.2, 14.0, 2.4,
                    NULL, NULL, 0
                )
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        migrated.query("SELECT is_favorite, last_used_at FROM food_items WHERE id = 'a'").use { c ->
            check(c.moveToFirst()) { "expected the pre-migration row to survive" }
            assertThat(c.getInt(0)).isEqualTo(0)
            assertThat(c.isNull(1)).isEqualTo(true)
        }
        migrated.close()
    }

    @Test
    fun `migrate 5 to 6 recreates diary_entries with SET NULL FK and preserves data`() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO food_items (
                    id, name, barcode,
                    nutri_kcal, nutri_protein_g, nutri_fat_g, nutri_carbs_g, nutri_fiber_g,
                    serving_label, serving_grams_equivalent, is_user_created, cached_status,
                    orig_name, orig_barcode, orig_nutri_kcal, orig_nutri_protein_g,
                    orig_nutri_fat_g, orig_nutri_carbs_g, orig_nutri_fiber_g,
                    orig_serving_label, orig_serving_grams_equivalent, orig_is_user_created,
                    is_favorite, last_used_at
                ) VALUES (
                    'f1', 'Haferflocken', NULL,
                    350.0, 12.0, 6.0, 60.0, 8.0,
                    NULL, NULL, 0, 'ORIGINAL',
                    'Haferflocken', NULL, 350.0, 12.0,
                    6.0, 60.0, 8.0,
                    NULL, NULL, 0,
                    0, NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO diary_entries (id, slot, date, food_item_id, recipe_id,
                    amount_grams, portion_count, food_snapshot, recipe_snapshot)
                VALUES ('d1', 'BREAKFAST', '2026-07-17', 'f1', NULL,
                    150.0, NULL, '{"name":"Haferflocken"}', NULL)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        val query =
            "SELECT id, slot, date, food_item_id, recipe_id, " +
                "amount_grams, portion_count, food_snapshot FROM diary_entries"
        migrated.query(query).use { c ->
            check(c.moveToFirst()) { "expected the pre-migration diary entry to survive" }
            assertThat(c.getString(0)).isEqualTo("d1")
            assertThat(c.getString(1)).isEqualTo("BREAKFAST")
            assertThat(c.getString(2)).isEqualTo("2026-07-17")
            assertThat(c.getString(3)).isEqualTo("f1")
            assertThat(c.isNull(4)).isTrue()
            assertThat(c.getDouble(5)).isEqualTo(150.0)
            assertThat(c.isNull(6)).isTrue()
            assertThat(c.getString(7)).isEqualTo("""{"name":"Haferflocken"}""")
        }

        // Verify SET_NULL: deleting the referenced food nulls the FK
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL("DELETE FROM food_items WHERE id = 'f1'")
        migrated.query("SELECT food_item_id FROM diary_entries WHERE id = 'd1'").use { c ->
            check(c.moveToFirst()) { "entry should survive food deletion" }
            assertThat(c.isNull(0)).isTrue()
        }

        migrated.close()
    }

    @Test
    fun `migrate 2 to 3 adds recipe category and meal prep columns`() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO recipes (id, name, final_weight_grams, portions, scale)
                VALUES ('r1', 'Pfannkuchen', 500.0, 2, 1.0)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        migrated.query("SELECT category, is_meal_prep FROM recipes WHERE id = 'r1'").use { c ->
            check(c.moveToFirst()) { "expected the pre-migration recipe to survive" }
            assertThat(c.isNull(0)).isTrue()
            assertThat(c.getInt(1)).isEqualTo(0)
        }
        migrated.close()
    }

    @Test
    fun `migrate 6 to 7 drops scale and is_meal_prep from recipes`() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL(
                """
                INSERT INTO recipes (id, name, final_weight_grams, portions, scale, category, is_meal_prep, last_modified_at)
                VALUES ('r1', 'Pfannkuchen', 500.0, 2, 1.0, 'Backen', 0, 0)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        migrated.query("PRAGMA table_info(recipes)").use { c ->
            val columns = mutableListOf<String>()
            while (c.moveToNext()) {
                columns.add(c.getString(1))
            }
            assertThat("scale" in columns).isEqualTo(false)
            assertThat("is_meal_prep" in columns).isEqualTo(false)
        }

        val query =
            "SELECT id, name, final_weight_grams, portions, category, last_modified_at FROM recipes WHERE id = 'r1'"
        migrated.query(query).use { c ->
            check(c.moveToFirst()) { "expected recipe to survive" }
            assertThat(c.getString(0)).isEqualTo("r1")
            assertThat(c.getString(1)).isEqualTo("Pfannkuchen")
            assertThat(c.getDouble(2)).isEqualTo(500.0)
            assertThat(c.getInt(3)).isEqualTo(2)
            assertThat(c.getString(4)).isEqualTo("Backen")
            assertThat(c.getLong(5)).isEqualTo(0L)
        }

        migrated.close()
    }
}
