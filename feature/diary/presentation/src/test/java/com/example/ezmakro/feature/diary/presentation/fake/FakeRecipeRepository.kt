package com.example.ezmakro.feature.diary.presentation.fake

import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRecipeRepository : RecipeRepository {
    private val store = MutableStateFlow<Map<String, Recipe>>(emptyMap())

    val saved = mutableListOf<Recipe>()
    val deleted = mutableListOf<RecipeId>()

    fun emitRecipe(recipe: Recipe) {
        store.value = store.value + (recipe.id.value to recipe)
    }

    /** Replaces the whole set at once, e.g. to simulate a deleted source recipe. */
    fun emitRecipes(recipes: List<Recipe>) {
        store.value = recipes.associateBy { it.id.value }
    }

    fun clearRecipes() {
        store.value = emptyMap()
    }

    override fun observeAll(): Flow<List<Recipe>> = store.map { it.values.toList() }

    override fun observeById(id: RecipeId): Flow<Recipe?> = store.map { it[id.value] }

    override suspend fun getById(id: RecipeId): Result<Recipe?, DataError.Local> = Result.Success(store.value[id.value])

    override suspend fun save(recipe: Recipe): EmptyResult<DataError.Local> {
        saved += recipe
        store.value = store.value + (recipe.id.value to recipe)
        return Result.Success(Unit)
    }

    override suspend fun delete(id: RecipeId): EmptyResult<DataError.Local> {
        deleted += id
        store.value = store.value - id.value
        return Result.Success(Unit)
    }

    override suspend fun findRecipeNamesUsingFood(foodId: FoodId): Result<List<String>, DataError.Local> =
        Result.Success(
            store.value.values
                .filter { recipe -> recipe.ingredients.any { it.food?.id == foodId } }
                .map { it.name }
                .sorted(),
        )

    override fun observeFavorites(): Flow<List<Recipe>> = store.map { it.values.filter(Recipe::isFavorite) }

    override fun observeRecent(limit: Int): Flow<List<Recipe>> =
        store.map { recipes ->
            recipes.values
                .filter { it.lastUsedAt != null }
                .sortedByDescending { it.lastUsedAt }
                .take(limit)
        }

    override suspend fun toggleFavorite(id: RecipeId): EmptyResult<DataError.Local> {
        val map = store.value.toMutableMap()
        val recipe = map[id.value] ?: return Result.Error(DataError.Local.NOT_FOUND)
        map[id.value] = recipe.copy(isFavorite = !recipe.isFavorite)
        store.value = map
        return Result.Success(Unit)
    }

    override suspend fun markUsed(id: RecipeId): EmptyResult<DataError.Local> {
        val map = store.value.toMutableMap()
        val recipe = map[id.value] ?: return Result.Error(DataError.Local.NOT_FOUND)
        map[id.value] = recipe.copy(lastUsedAt = System.currentTimeMillis())
        store.value = map
        return Result.Success(Unit)
    }

    override suspend fun duplicate(id: RecipeId): Result<RecipeId, DataError.Local> {
        val source = store.value[id.value] ?: return Result.Error(DataError.Local.NOT_FOUND)
        val copyId = RecipeId("copy-${id.value}")
        val copy = source.copy(id = copyId, name = "${source.name} (Kopie)")
        store.value = store.value + (copyId.value to copy)
        return Result.Success(copyId)
    }
}
