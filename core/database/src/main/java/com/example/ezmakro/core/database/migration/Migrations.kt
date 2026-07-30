package com.example.ezmakro.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

/**
 * Phase 9 Step 6 follow-up: Diary Entry FK change (CASCADE → SET_NULL).
 *
 * The diary_entries table already stores immutable JSON snapshots of FoodItem
 * and Recipe objects at log time. However, the FK constraints on food_item_id
 * and recipe_id used ON DELETE CASCADE, which deleted the diary entry row
 * when the source recipe or food was deleted — defeating the snapshot purpose.
 *
 * This migration recreates the table with ON DELETE SET NULL so the entry and
 * its JSON snapshot survive even when the source is deleted. After migration
 * the FK columns will be set to NULL for any deleted source, but the snapshot
 * columns retain all historical data.
 */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `diary_entries_new` (" +
                    "`id` TEXT NOT NULL, " +
                    "`slot` TEXT NOT NULL, " +
                    "`date` TEXT NOT NULL, " +
                    "`food_item_id` TEXT, " +
                    "`recipe_id` TEXT, " +
                    "`amount_grams` REAL, " +
                    "`portion_count` REAL, " +
                    "`food_snapshot` TEXT DEFAULT NULL, " +
                    "`recipe_snapshot` TEXT DEFAULT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`food_item_id`) REFERENCES `food_items`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL, " +
                    "FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL" +
                    ")",
            )

            db.execSQL(
                "INSERT INTO `diary_entries_new` " +
                    "SELECT * FROM `diary_entries`",
            )

            db.execSQL("DROP TABLE `diary_entries`")

            db.execSQL(
                "ALTER TABLE `diary_entries_new` RENAME TO `diary_entries`",
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_diary_entries_date` " +
                    "ON `diary_entries` (`date`)",
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_diary_entries_food_item_id` " +
                    "ON `diary_entries` (`food_item_id`)",
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_diary_entries_recipe_id` " +
                    "ON `diary_entries` (`recipe_id`)",
            )
        }
    }

/**
 * Phase 9 Step 6: Recipe-as-Ingredient (Snapshot Independence).
 *
 * Schema changes:
 * - `recipes` gains `last_modified_at` (epoch millis, default 0) for change
 *   detection on snapshot-referenced recipes.
 * - `recipe_ingredients` is rebuilt with an auto-generated primary key
 *   (`row_id`) instead of the old composite `(recipe_id, food_item_id)`,
 *   `food_item_id` is made nullable, and `recipe_snapshot_json` is added
 *   for storing frozen recipe snapshots.
 */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE recipes ADD COLUMN last_modified_at INTEGER NOT NULL DEFAULT 0",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS recipe_ingredients_new (" +
                    "row_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "recipe_id TEXT NOT NULL, " +
                    "food_item_id TEXT, " +
                    "raw_weight_grams REAL, " +
                    "serving_count REAL, " +
                    "recipe_snapshot_json TEXT DEFAULT NULL, " +
                    "FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (food_item_id) REFERENCES food_items(id) ON DELETE SET NULL" +
                    ")",
            )

            db.execSQL(
                "INSERT INTO recipe_ingredients_new " +
                    "(recipe_id, food_item_id, raw_weight_grams, serving_count) " +
                    "SELECT recipe_id, food_item_id, raw_weight_grams, serving_count " +
                    "FROM recipe_ingredients",
            )

            db.execSQL("DROP TABLE recipe_ingredients")

            db.execSQL(
                "ALTER TABLE recipe_ingredients_new RENAME TO recipe_ingredients",
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_recipe_ingredients_food_item_id " +
                    "ON recipe_ingredients (food_item_id)",
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_recipe_ingredients_recipe_id " +
                    "ON recipe_ingredients (recipe_id)",
            )
        }
    }

/**
 * Phase 9 Refinement — Removes `scale` and `is_meal_prep` columns from `recipes`.
 *
 * These features (Scaling, Meal Prep, Quick Meal) were removed because they
 * added no user-facing value and only caused confusion. Existing recipes with
 * scale != 1.0 will have their total nutrition change (the multiplier is
 * removed). This is acceptable because scale was never exposed through the UI
 * in an effective way and is practically always 1.0.
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recipes_new` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`final_weight_grams` REAL, " +
                    "`portions` INTEGER NOT NULL, " +
                    "`category` TEXT, " +
                    "`last_modified_at` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`)" +
                    ")",
            )
            db.execSQL(
                "INSERT INTO `recipes_new` " +
                    "(`id`, `name`, `final_weight_grams`, `portions`, `category`, `last_modified_at`) " +
                    "SELECT `id`, `name`, `final_weight_grams`, `portions`, `category`, `last_modified_at` " +
                    "FROM `recipes`",
            )
            db.execSQL("DROP TABLE `recipes`")
            db.execSQL("ALTER TABLE `recipes_new` RENAME TO `recipes`")
        }
    }

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
