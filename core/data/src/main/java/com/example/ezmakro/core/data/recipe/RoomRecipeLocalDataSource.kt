package com.example.ezmakro.core.data.recipe

import com.example.ezmakro.core.data.logging.AppLogger
import com.example.ezmakro.core.data.recipe.mapper.toEntities
import com.example.ezmakro.core.data.recipe.mapper.toRecipe
import com.example.ezmakro.core.database.dao.RecipeDao
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.domain.datasource.FoodLocalDataSource
import com.example.ezmakro.core.domain.datasource.RecipeLocalDataSource
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed Single Source of Truth for recipes (ADR-002). Wraps [RecipeDao],
 * mapping entities to domain [Recipe]s (resolving ingredient foods via
 * [FoodLocalDataSource]) and DAO exceptions to [DataError.Local]. Recipe +
 * ingredients are written atomically through the DAO transaction.
 */
@Suppress("TooManyFunctions")
class RoomRecipeLocalDataSource(
    private val dao: RecipeDao,
    private val foodLocalDataSource: FoodLocalDataSource,
) : RecipeLocalDataSource {
    override fun observeAll(): Flow<List<Recipe>> =
        dao.observeAll().map { entities -> entities.mapNotNull { it.toRecipeSuspending() } }

    override fun observeById(id: RecipeId): Flow<Recipe?> =
        dao.observeAll().map { entities ->
            entities.firstOrNull { it.id == id.value }?.toRecipeSuspending()
        }

    override fun observeFavorites(): Flow<List<Recipe>> =
        dao.observeFavorites().map { entities ->
            entities.mapNotNull { it.toRecipeSuspending() }
        }

    override fun observeRecent(limit: Int): Flow<List<Recipe>> =
        dao.observeRecent(limit).map { entities ->
            entities.mapNotNull { it.toRecipeSuspending() }
        }

    override suspend fun getById(id: RecipeId): Result<Recipe?, DataError.Local> =
        runCatchingLocal {
            val entity = dao.getById(id.value) ?: return@runCatchingLocal null
            val ingredients = dao.getIngredients(id.value)
            entity.toRecipe(ingredients, foodLocalDataSource)
        }

    override suspend fun upsertRecipeWithIngredients(recipe: Recipe): EmptyResult<DataError.Local> =
        runCatchingLocal {
            val (recipeEntity, ingredientEntities) = recipe.toEntities()
            dao.upsertRecipeWithIngredients(recipeEntity, ingredientEntities)
        }

    override suspend fun deleteById(id: RecipeId): EmptyResult<DataError.Local> =
        runCatchingLocal {
            val entity =
                dao.getById(id.value)
                    ?: RecipeEntity(
                        id = id.value,
                        name = "",
                        finalWeightGrams = null,
                        portions = 1,
                        category = null,
                    )
            dao.deleteRecipe(entity)
        }

    override suspend fun getRecipeNamesUsingFood(foodId: FoodId): Result<List<String>, DataError.Local> =
        runCatchingLocal {
            dao.getRecipeNamesUsingFood(foodId.value)
        }

    override suspend fun setFavorite(
        id: RecipeId,
        favorite: Boolean,
    ): EmptyResult<DataError.Local> =
        runCatchingLocal {
            dao.setFavorite(id.value, favorite)
        }

    override suspend fun markUsed(
        id: RecipeId,
        usedAt: Long,
    ): EmptyResult<DataError.Local> =
        runCatchingLocal {
            dao.markUsed(id.value, usedAt)
        }

    /**
     * Resolve a single [RecipeEntity] to its domain [Recipe], reading its
     * ingredient lines from the DAO. Returns null when resolution fails.
     */
    private suspend fun RecipeEntity.toRecipeSuspending(): Recipe? {
        val ingredients = dao.getIngredients(id)
        return toRecipe(ingredients, foodLocalDataSource)
    }
}

/** Map DAO exceptions to a typed [DataError.Local], preserving cancellation. */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> runCatchingLocal(block: suspend () -> T): Result<T, DataError.Local> =
    withContext(Dispatchers.IO) {
        try {
            Result.Success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("RoomRecipeLocalDataSource", "Local recipe data error", e)
            AppLogger.e("Local recipe data error", e, tag = "RoomRecipeLocalDataSource")
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
