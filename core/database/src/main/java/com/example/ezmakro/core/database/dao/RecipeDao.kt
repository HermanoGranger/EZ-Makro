package com.example.ezmakro.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local data access for [RecipeEntity] and its ingredient lines. Recipe + its
 * ingredients are written atomically (ADR-003: ingredient raw weights define
 * the dish; partial writes would corrupt nutrition math).
 */
@Dao
interface RecipeDao {
    @Upsert
    fun upsertRecipe(recipe: RecipeEntity)

    @Upsert
    fun upsertIngredients(ingredients: List<RecipeIngredientEntity>)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id = :recipeId")
    suspend fun getIngredients(recipeId: String): List<RecipeIngredientEntity>

    /** Atomically replace a recipe and its ingredient lines. */
    @Transaction
    suspend fun upsertRecipeWithIngredients(
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientEntity>,
    ) {
        upsertRecipe(recipe)
        deleteIngredientsFor(recipe.id)
        upsertIngredients(ingredients)
    }

    @Query("DELETE FROM recipe_ingredients WHERE recipe_id = :recipeId")
    fun deleteIngredientsFor(recipeId: String)

    @Query("SELECT * FROM recipes WHERE is_favorite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE last_used_at IS NOT NULL ORDER BY last_used_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecipeEntity>>

    @Query("UPDATE recipes SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(
        id: String,
        favorite: Boolean,
    )

    @Query("UPDATE recipes SET last_used_at = :usedAt WHERE id = :id")
    suspend fun markUsed(
        id: String,
        usedAt: Long,
    )
}
