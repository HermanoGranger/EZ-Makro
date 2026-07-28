package com.example.ezmakro.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 5: adds the favorites flag and the last-used timestamp to `food_items`,
 * together with their supporting indices. Existing rows default to
 * `is_favorite = 0` and `last_used_at = NULL`.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE food_items ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE food_items ADD COLUMN last_used_at INTEGER",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_food_items_is_favorite " +
                    "ON food_items (is_favorite)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_food_items_last_used_at " +
                    "ON food_items (last_used_at)",
            )
        }
    }

/**
 * Phase 6 + 7: adds the `category` and `is_meal_prep` columns to `recipes`, and
 * the immutable `food_snapshot` / `recipe_snapshot` JSON columns to
 * `diary_entries` (the meal-log v1 design). Existing rows default to NULL.
 *
 * The app is pre-release with `fallbackToDestructiveMigration` enabled and no
 * production users, so this single 2→3 step establishes the complete v1 schema.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE recipes ADD COLUMN category TEXT",
            )
            db.execSQL(
                "ALTER TABLE recipes ADD COLUMN is_meal_prep INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE diary_entries ADD COLUMN food_snapshot TEXT DEFAULT NULL",
            )
            db.execSQL(
                "ALTER TABLE diary_entries ADD COLUMN recipe_snapshot TEXT DEFAULT NULL",
            )
        }
    }

/**
 * Phase 9 Step 1: makes the five per-100 g nutrition columns nullable in
 * `food_items` so user-created foods can be saved without nutrition data.
 * Existing rows keep their values; the columns simply accept NULL now.
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val columns =
                listOf(
                    "nutri_kcal",
                    "nutri_protein_g",
                    "nutri_fat_g",
                    "nutri_carbs_g",
                    "nutri_fiber_g",
                )
            for (col in columns) {
                db.execSQL(
                    "ALTER TABLE food_items RENAME COLUMN $col TO ${col}_old",
                )
                db.execSQL(
                    "ALTER TABLE food_items ADD COLUMN $col REAL",
                )
                db.execSQL(
                    "UPDATE food_items SET $col = ${col}_old",
                )
                db.execSQL(
                    "ALTER TABLE food_items DROP COLUMN ${col}_old",
                )
            }
        }
    }

/** All migrations, in order, for registration with the Room builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
