package com.example.ezmakro.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.ezmakro.core.database.converter.JsonConverters
import com.example.ezmakro.core.database.converter.ScalarConverters
import com.example.ezmakro.core.database.dao.DiaryEntryDao
import com.example.ezmakro.core.database.dao.FoodItemDao
import com.example.ezmakro.core.database.dao.RecipeDao
import com.example.ezmakro.core.database.dao.WeightLogDao
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.database.entity.FoodFtsEntity
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.database.entity.WeightLogEntity

/**
 * The single Room database for EZ Makro (ADR-002: Room = Single Source of Truth).
 *
 * Room 2.8 backs FTS4 for local full-text food-name search.
 *
 * Version history:
 * - 1: initial schema.
 * - 2: Phase 5 — `food_items.is_favorite` and `food_items.last_used_at` columns
 *   plus their indices, for the favorites and recently-used sections.
 * - 3: Phase 6 — `recipes.category` and `recipes.is_meal_prep` columns.
 *   Phase 7 — diary immutable JSON snapshots (`food_snapshot`,
 *   `recipe_snapshot`) added as part of the v1 diary design.
 * - 4: Phase 9 Step 1 — makes the five per-100 g nutrition columns nullable
 *   in `food_items` so user-created foods can omit nutrition data.
 * - 5: Phase 9 Step 6 — Recipe-as-Ingredient snapshot independence:
 *   `recipes.last_modified_at`, `recipe_ingredients` rebuilt with
 *   auto-generated PK, nullable `food_item_id`, and `recipe_snapshot_json`.
 * - 6: Phase 9 Step 6 (follow-up) — Diary entry FK change:
 *   `diary_entries` foreign keys `food_item_id` and `recipe_id` changed from
 *   `ON DELETE CASCADE` to `ON DELETE SET NULL` so logged entries survive
 *   deletion of the source recipe or food (the existing JSON snapshots
 *   already provide all data for rendering and calculation).
 *
 * Migrations live in [com.example.ezmakro.core.database.migration.Migrations];
 * the migration test harness lives in the test source set.
 */
@Database(
    entities = [
        FoodItemEntity::class,
        FoodFtsEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        DiaryEntryEntity::class,
        WeightLogEntity::class,
    ],
    version = 6,
)
@TypeConverters(ScalarConverters::class, JsonConverters::class)
abstract class EZDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao

    abstract fun recipeDao(): RecipeDao

    abstract fun diaryEntryDao(): DiaryEntryDao

    abstract fun weightLogDao(): WeightLogDao

    companion object {
        const val DB_NAME = "ez_makro.db"
    }
}
