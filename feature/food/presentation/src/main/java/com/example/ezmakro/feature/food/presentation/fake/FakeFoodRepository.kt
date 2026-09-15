package com.example.ezmakro.feature.food.presentation.fake

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

@Suppress("TooManyFunctions")
class FakeFoodRepository : FoodRepository {
    private val items = MutableStateFlow<List<FoodItem>>(emptyList())

    var savedEdits = mutableListOf<FoodItem>()
        private set

    var lastToggledFavorite: FoodId? = null
        private set

    var lookupResult: Result<FoodItem?, DataError> = Result.Success(null)
        set(value) {
            field = value
            lookupCallsCount++
        }

    var lookupCallsCount = 0
        private set

    fun emitFoods(foods: List<FoodItem>) {
        items.value = foods
    }

    override fun observeAll(): Flow<List<FoodItem>> = items

    override fun observeFavorites(): Flow<List<FoodItem>> = items

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> = items

    override fun searchLocal(query: String): Flow<List<FoodItem>> = items

    override suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local> =
        Result.Success(items.value.firstOrNull { it.id == id })

    override suspend fun searchRemote(query: String): Result<List<FoodItem>, DataError.Network> =
        Result.Success(emptyList())

    override suspend fun lookupByBarcode(barcode: String): Result<FoodItem?, DataError> = lookupResult

    override suspend fun saveUserEdit(foodItem: FoodItem): EmptyResult<DataError.Local> {
        savedEdits += foodItem
        return Result.Success(Unit)
    }

    override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun refetchFromOff(id: FoodId): Result<FoodItem?, DataError> = Result.Success(null)

    override suspend fun toggleFavorite(id: FoodId): EmptyResult<DataError.Local> {
        lastToggledFavorite = id
        return Result.Success(Unit)
    }

    override suspend fun markUsed(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun cacheRemoteItem(item: FoodItem): EmptyResult<DataError.Local> {
        items.value = items.value.filter { it.id != item.id } + item
        return Result.Success(Unit)
    }

    private val sessionItems = mutableMapOf<FoodId, FoodItem>()

    fun rememberSessionItem(item: FoodItem) {
        sessionItems[item.id] = item
    }

    override suspend fun rememberRemoteItem(item: FoodItem) {
        sessionItems[item.id] = item
    }

    override suspend fun getSessionItem(id: FoodId): FoodItem? = sessionItems[id]

    companion object {
        fun food(
            id: String = "1",
            name: String = "Apple",
            kcal: Double = 52.0,
            isFavorite: Boolean = false,
        ): FoodItem =
            FoodItem(
                id = FoodId(id),
                name = name,
                barcode = null,
                nutritionPer100g =
                    NutritionValues(
                        kcal = kcal,
                        proteinG = 0.0,
                        fatG = 0.0,
                        carbsG = 0.0,
                        fiberG = 0.0,
                    ),
                servingUnit = null,
                isUserCreated = false,
                cachedStatus = CachedFoodItemStatus.Original,
                originalSnapshot = null,
                isFavorite = isFavorite,
            )
    }
}
