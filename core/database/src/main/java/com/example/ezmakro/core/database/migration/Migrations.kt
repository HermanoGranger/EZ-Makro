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

/** All migrations, in order, for registration with the Room builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
