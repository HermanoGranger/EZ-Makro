package com.example.ezmakro.feature.food.presentation.fake

import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Minimal [RecipeRepository] fake for the food detail ViewModel test. Only
 * [findRecipeNamesUsingFood] carries meaningful behavior; the rest is inert.
 */
class FakeRecipeRepository : RecipeRepository {
    /** Recipe names returned for any food lookup. */
    var namesUsingFood: List<String> = emptyList()

    var lastQueriedFood: FoodId? = null
        private set

    override fun observeAll(): Flow<List<Recipe>> = flow { emit(emptyList()) }

    override fun observeById(id: RecipeId): Flow<Recipe?> = flow { emit(null) }

    override fun observeFavorites(): Flow<List<Recipe>> = flow { emit(emptyList()) }

    override fun observeRecent(limit: Int): Flow<List<Recipe>> = flow { emit(emptyList()) }

    override suspend fun getById(id: RecipeId): Result<Recipe?, DataError.Local> = Result.Success(null)

    override suspend fun save(recipe: Recipe): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun delete(id: RecipeId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun findRecipeNamesUsingFood(foodId: FoodId): Result<List<String>, DataError.Local> {
        lastQueriedFood = foodId
        return Result.Success(namesUsingFood)
    }

    override suspend fun duplicate(id: RecipeId): Result<RecipeId, DataError.Local> =
        Result.Error(DataError.Local.NOT_FOUND)

    override suspend fun toggleFavorite(id: RecipeId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun markUsed(id: RecipeId): EmptyResult<DataError.Local> = Result.Success(Unit)
}
