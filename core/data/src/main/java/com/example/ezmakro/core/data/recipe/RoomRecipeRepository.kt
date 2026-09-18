package com.example.ezmakro.core.data.recipe

import com.example.ezmakro.core.domain.datasource.RecipeLocalDataSource
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Offline-first recipe repository (ADR-002): recipes have no remote source, so
 * [local] is the sole Single Source of Truth. The UI observes only the local
 * flows.
 */
class RoomRecipeRepository(
    private val local: RecipeLocalDataSource,
) : RecipeRepository {
    override fun observeAll(): Flow<List<Recipe>> = local.observeAll()

    override fun observeById(id: RecipeId): Flow<Recipe?> = local.observeById(id)

    override suspend fun getById(id: RecipeId): Result<Recipe?, DataError.Local> = local.getById(id)

    override suspend fun save(recipe: Recipe): EmptyResult<DataError.Local> = local.upsertRecipeWithIngredients(recipe)

    override fun observeFavorites(): Flow<List<Recipe>> = local.observeFavorites()

    override fun observeRecent(limit: Int): Flow<List<Recipe>> = local.observeRecent(limit)

    override suspend fun delete(id: RecipeId): EmptyResult<DataError.Local> = local.deleteById(id)

    override suspend fun toggleFavorite(id: RecipeId): EmptyResult<DataError.Local> {
        val current =
            when (val result = local.getById(id)) {
                is Result.Success -> result.data ?: return Result.Error(DataError.Local.NOT_FOUND)
                is Result.Error -> return Result.Error(result.error)
            }
        return local.setFavorite(id, current.isFavorite.not())
    }

    override suspend fun markUsed(id: RecipeId): EmptyResult<DataError.Local> =
        local.markUsed(id, System.currentTimeMillis())

    override suspend fun duplicate(id: RecipeId): Result<RecipeId, DataError.Local> {
        val source =
            when (val result = local.getById(id)) {
                is Result.Success -> result.data ?: return Result.Error(DataError.Local.NOT_FOUND)
                is Result.Error -> return Result.Error(result.error)
            }
        val copyId = RecipeId(UUID.randomUUID().toString())
        val copy =
            Recipe(
                id = copyId,
                name = "${source.name} (Kopie)",
                ingredients = source.ingredients,
                finalWeight = source.finalWeight,
                portions = source.portions,
                category = source.category,
                isFavorite = source.isFavorite,
                lastModifiedAt = System.currentTimeMillis(),
            )
        return when (val saved = local.upsertRecipeWithIngredients(copy)) {
            is Result.Error -> saved
            is Result.Success -> Result.Success(copyId)
        }
    }
}
