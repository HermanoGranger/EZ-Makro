package com.example.ezmakro.core.domain.datasource

import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Local Single Source of Truth for recipes (Room, ADR-002). Recipe + ingredient
 * lines are written atomically so partial writes can never corrupt the raw-weight
 * math (ADR-003). Ingredient foods are referenced by id and resolved through
 * [foodLocalDataSource].
 */
interface RecipeLocalDataSource {
    fun observeAll(): Flow<List<Recipe>>

    fun observeById(id: RecipeId): Flow<Recipe?>

    suspend fun getById(id: RecipeId): Result<Recipe?, DataError.Local>

    /** Persist a recipe and its ingredient lines as one atomic unit. */
    suspend fun upsertRecipeWithIngredients(recipe: Recipe): EmptyResult<DataError.Local>

    suspend fun deleteById(id: RecipeId): EmptyResult<DataError.Local>
}
