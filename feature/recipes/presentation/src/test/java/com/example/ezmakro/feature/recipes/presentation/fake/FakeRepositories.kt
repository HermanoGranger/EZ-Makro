package com.example.ezmakro.feature.recipes.presentation.fake

import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeFoodRepository : FoodRepository {
    private val items = MutableStateFlow<List<FoodItem>>(emptyList())

    fun emitFoods(foods: List<FoodItem>) {
        items.value = foods
    }

    override fun observeAll(): Flow<List<FoodItem>> = items

    override fun observeFavorites(): Flow<List<FoodItem>> = items

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> = items

    override fun searchLocal(query: String): Flow<List<FoodItem>> = items

    override suspend fun searchRemote(query: String): Result<List<FoodItem>, DataError.Network> =
        Result.Success(emptyList())

    override suspend fun lookupByBarcode(barcode: String): Result<FoodItem?, DataError> = Result.Success(null)

    override suspend fun saveUserEdit(foodItem: FoodItem): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun refetchFromOff(id: FoodId): Result<FoodItem?, DataError> = Result.Success(null)

    override suspend fun toggleFavorite(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun markUsed(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    companion object {
        fun food(
            id: String,
            name: String = "Apple",
            kcal: Double = 52.0,
        ): FoodItem =
            FoodItem(
                id = FoodId(id),
                name = name,
                barcode = null,
                nutritionPer100g = NutritionValues(kcal, 0.0, 0.0, 0.0, 0.0),
                servingUnit = null,
                isUserCreated = true,
                cachedStatus = CachedFoodItemStatus.Original,
                originalSnapshot = null,
            )
    }
}

class FakeRecipeRepository : RecipeRepository {
    private val store = MutableStateFlow<Map<String, Recipe>>(emptyMap())

    val saved = mutableListOf<Recipe>()

    val deleted = mutableListOf<RecipeId>()

    fun emitRecipe(recipe: Recipe) {
        store.value = store.value + (recipe.id.value to recipe)
    }

    override fun observeAll(): Flow<List<Recipe>> = store.map { it.values.toList() }

    override fun observeById(id: RecipeId): Flow<Recipe?> = store.map { it[id.value] }

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

    override suspend fun duplicate(id: RecipeId): Result<RecipeId, DataError.Local> {
        val source = store.value[id.value] ?: return Result.Error(DataError.Local.NOT_FOUND)
        val copyId = RecipeId("copy-${id.value}")
        val copy = source.copy(id = copyId, name = "${source.name} (Kopie)")
        store.value = store.value + (copyId.value to copy)
        return Result.Success(copyId)
    }
}
