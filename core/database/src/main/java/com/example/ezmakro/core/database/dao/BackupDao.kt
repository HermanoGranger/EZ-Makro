package com.example.ezmakro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.database.entity.WeightLogEntity

/**
 * Raw table access for a full-database export and import.
 *
 * The DAO deliberately knows nothing about any backup file format: it only
 * reads, counts, clears and bulk-inserts the five persistent tables. Every
 * insert resolves conflicts with [OnConflictStrategy.IGNORE] on the natural
 * keys, so an import never overwrites a row that already exists locally
 * (MERGE, A7). [RecipeIngredientEntity.rowId] is never written — callers pass
 * entities with `rowId = 0`, Room assigns fresh surrogate keys and the FTS4
 * rowids stay valid (A13).
 *
 * Foreign keys stay enabled. Parent rows are inserted before their children and
 * child tables are cleared before their parents, so no statement here can
 * violate a foreign key.
 */
@Dao
@Suppress("TooManyFunctions")
interface BackupDao {
    // Read — order: food_items, recipes, recipe_ingredients, diary_entries, weight_logs.
    @Query("SELECT * FROM food_items")
    suspend fun readFoodItems(): List<FoodItemEntity>

    @Query("SELECT * FROM recipes")
    suspend fun readRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredients")
    suspend fun readRecipeIngredients(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM diary_entries")
    suspend fun readDiaryEntries(): List<DiaryEntryEntity>

    @Query("SELECT * FROM weight_logs")
    suspend fun readWeightLogs(): List<WeightLogEntity>

    @Query("SELECT id FROM food_items")
    suspend fun readFoodItemIds(): List<String>

    @Query("SELECT id FROM recipes")
    suspend fun readRecipeIds(): List<String>

    // Write — IGNORE on the natural keys (A7); rowIds come back as -1 when skipped.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFoodItems(items: List<FoodItemEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipes(recipes: List<RecipeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeIngredients(ingredients: List<RecipeIngredientEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDiaryEntries(entries: List<DiaryEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWeightLogs(weightLogs: List<WeightLogEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun countFoodItems(): Int

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun countRecipes(): Int

    @Query("SELECT COUNT(*) FROM recipe_ingredients")
    suspend fun countRecipeIngredients(): Int

    @Query("SELECT COUNT(*) FROM diary_entries")
    suspend fun countDiaryEntries(): Int

    @Query("SELECT COUNT(*) FROM weight_logs")
    suspend fun countWeightLogs(): Int

    // Clear — children before parents.
    @Query("DELETE FROM recipe_ingredients")
    suspend fun deleteAllRecipeIngredients()

    @Query("DELETE FROM diary_entries")
    suspend fun deleteAllDiaryEntries()

    @Query("DELETE FROM recipes")
    suspend fun deleteAllRecipes()

    @Query("DELETE FROM food_items")
    suspend fun deleteAllFoodItems()

    @Query("DELETE FROM weight_logs")
    suspend fun deleteAllWeightLogs()

    /**
     * Clears the five tables and inserts [foods]/[recipes]/[ingredients]/[entries]/[weightLogs]
     * in a single transaction (replace mode). Delete order is children before parents, insert
     * order inside [insertAll] parents before children.
     */
    @Transaction
    suspend fun replaceAll(
        foods: List<FoodItemEntity>,
        recipes: List<RecipeEntity>,
        ingredients: List<RecipeIngredientEntity>,
        entries: List<DiaryEntryEntity>,
        weightLogs: List<WeightLogEntity>,
    ): BackupInsertReport {
        deleteAllRecipeIngredients()
        deleteAllDiaryEntries()
        deleteAllRecipes()
        deleteAllFoodItems()
        deleteAllWeightLogs()
        return insertAll(foods, recipes, ingredients, entries, weightLogs)
    }

    /**
     * Inserts everything that is not present yet, in one transaction (merge mode). Rows whose
     * natural key already exists are ignored, so repeating the same payload is idempotent.
     *
     * Ingredient lines are only written for recipes that this call actually inserted:
     * [RecipeIngredientEntity.rowId] is an auto-incrementing surrogate key without a natural
     * counterpart, so writing them unconditionally would duplicate every ingredient on a second
     * import (A7/A19). Filtered-out lines are reported as skipped.
     */
    @Transaction
    suspend fun insertAll(
        foods: List<FoodItemEntity>,
        recipes: List<RecipeEntity>,
        ingredients: List<RecipeIngredientEntity>,
        entries: List<DiaryEntryEntity>,
        weightLogs: List<WeightLogEntity>,
    ): BackupInsertReport {
        val foodIds = insertFoodItems(foods)
        val recipeIds = insertRecipes(recipes)
        val insertedRecipeIds =
            recipes.filterIndexed { index, _ -> recipeIds[index] >= 0L }.map { it.id }.toSet()
        val keptIngredients = ingredients.filter { it.recipeId in insertedRecipeIds }
        val ingredientIds = insertRecipeIngredients(keptIngredients)
        val entryIds = insertDiaryEntries(entries)
        val weightIds = insertWeightLogs(weightLogs)
        return BackupInsertReport(
            foodItems = TableInsertCounts.from(foodIds),
            recipes = TableInsertCounts.from(recipeIds),
            recipeIngredients =
                TableInsertCounts.from(
                    ingredientIds,
                    preSkipped = ingredients.size - keptIngredients.size,
                ),
            diaryEntries = TableInsertCounts.from(entryIds),
            weightLogs = TableInsertCounts.from(weightIds),
        )
    }
}
