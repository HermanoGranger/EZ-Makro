package com.example.ezmakro.core.data.food

import com.example.ezmakro.core.domain.datasource.FoodLocalDataSource
import com.example.ezmakro.core.domain.datasource.FoodRemoteDataSource
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.map
import com.example.ezmakro.core.domain.result.onSuccess
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first food repository (ADR-002): the UI observes local flows only;
 * OFF results are persisted into Room and never observed directly.
 *
 * ADR-004: a remote refresh never overwrites a [CachedFoodItemStatus.UserModified]
 * entry automatically. [refetchFromOff] is the explicit, user-confirmed override.
 */
class OfflineFirstFoodRepository(
    private val local: FoodLocalDataSource,
    private val remote: FoodRemoteDataSource,
) : FoodRepository {
    override fun observeAll(): Flow<List<FoodItem>> = local.observeAll()

    override fun searchLocal(query: String): Flow<List<FoodItem>> = local.searchByName(query)

    override suspend fun searchRemote(query: String): Result<List<FoodItem>, DataError.Network> =
        remote.searchByName(query).onSuccess { items ->
            items.forEach { cacheWithoutOverwritingUserEdits(it) }
        }

    override suspend fun lookupByBarcode(barcode: String): Result<FoodItem?, DataError> {
        when (val localHit = local.getByBarcode(barcode)) {
            is Result.Success -> localHit.data?.let { return Result.Success(it) }
            is Result.Error -> return Result.Error(localHit.error)
        }
        return when (val remoteHit = remote.getByBarcode(barcode)) {
            is Result.Success -> {
                remoteHit.data?.let { cacheWithoutOverwritingUserEdits(it) }
                Result.Success(remoteHit.data)
            }
            is Result.Error -> Result.Error(remoteHit.error)
        }
    }

    override suspend fun saveUserEdit(foodItem: FoodItem): EmptyResult<DataError.Local> {
        if (foodItem.isUserCreated) {
            return local.upsert(foodItem).map { }
        }
        // ADR-004: preserve the untouched OFF snapshot across edits so
        // "reset to original" always restores the first cached version, never a
        // previously edited one.
        val existingSnapshot =
            (local.getById(foodItem.id) as? Result.Success)
                ?.data
                ?.originalSnapshot
        val edited =
            foodItem.copy(
                cachedStatus = CachedFoodItemStatus.UserModified,
                originalSnapshot = existingSnapshot ?: foodItem.originalSnapshot,
            )
        return local.upsert(edited).map { }
    }

    override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> = local.resetToOriginal(id)

    override suspend fun refetchFromOff(id: FoodId): Result<FoodItem?, DataError> {
        val existing =
            when (val result = local.getById(id)) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.error)
            }
        val barcode =
            existing?.barcode
                ?: return Result.Error(DataError.Local.NOT_FOUND)
        return when (val remoteHit = remote.getByBarcode(barcode)) {
            is Result.Success -> {
                remoteHit.data?.let { fresh ->
                    // Explicit user-confirmed override: replace local entry as ORIGINAL.
                    local.upsert(fresh.copy(id = id))
                }
                Result.Success(remoteHit.data)
            }
            is Result.Error -> Result.Error(remoteHit.error)
        }
    }

    /**
     * ADR-004: persist an OFF result only if there is no existing UserModified
     * entry for the same id. Existing user edits are left untouched.
     */
    private suspend fun cacheWithoutOverwritingUserEdits(item: FoodItem) {
        val existing = (local.getById(item.id) as? Result.Success)?.data
        if (existing?.cachedStatus == CachedFoodItemStatus.UserModified) return
        local.upsert(item)
    }
}
