package com.example.ezmakro.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.example.ezmakro.core.database.migration.MIGRATION_1_2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the Phase 5 migration 1 → 2 (favorites + last-used columns).
 * Uses the exported schema JSONs under `schemas/`.
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
}
