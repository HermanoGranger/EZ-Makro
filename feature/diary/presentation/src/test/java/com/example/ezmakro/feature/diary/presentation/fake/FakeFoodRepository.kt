package com.example.ezmakro.feature.diary.presentation.fake

import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeFoodRepository : FoodRepository {
    private val store = MutableStateFlow<Map<String, FoodItem>>(emptyMap())

    fun emitFood(foodItem: FoodItem) {
        store.value = store.value + (foodItem.id.value to foodItem)
    }

    override fun observeAll(): Flow<List<FoodItem>> = store.map { it.values.toList() }

    override suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local> =
        Result.Success(store.value[id.value])

    override fun observeById(id: FoodId): Flow<FoodItem?> = store.map { it[id.value] }

    override suspend fun searchRemote(query: String): Result<List<FoodItem>, DataError.Network> =
        Result.Success(emptyList())

    override suspend fun lookupByBarcode(barcode: String): Result<FoodItem?, DataError> = Result.Success(null)

    override suspend fun saveUserEdit(foodItem: FoodItem): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun refetchFromOff(id: FoodId): Result<FoodItem?, DataError> = Result.Success(null)

    override suspend fun toggleFavorite(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun markUsed(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun cacheRemoteItem(item: FoodItem): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun rememberRemoteItem(item: FoodItem) = Unit

    override suspend fun getSessionItem(id: FoodId): FoodItem? = null

    override fun observeFavorites(): Flow<List<FoodItem>> = store.map { it.values.filter { it.isFavorite } }

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> = store.map { it.values.take(limit) }

    override fun searchLocal(query: String): Flow<List<FoodItem>> = store.map { it.values.filter { it.name.contains(query, true) } }

    companion object {
        fun food(
            id: String,
            name: String = "Food",
            kcal: Double = 100.0,
        ): FoodItem =
            FoodItem(
                id = FoodId(id),
                name = name,
                barcode = null,
                nutritionPer100g = NutritionValues(kcal, 10.0, 5.0, 2.0, 1.0),
                servingUnit = null,
                isUserCreated = true,
                cachedStatus = CachedFoodItemStatus.Original,
                originalSnapshot = null,
            )
    }
}
