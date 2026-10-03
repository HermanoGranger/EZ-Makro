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
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline-first food repository (ADR-002): the UI observes local flows only;
 * OFF results are persisted into Room and never observed directly.
 *
 * ADR-004: a remote refresh never overwrites a [CachedFoodItemStatus.UserModified]
 * entry automatically. [refetchFromOff] is the explicit, user-confirmed override.
 */
@Suppress("TooManyFunctions")
class OfflineFirstFoodRepository(
    private val local: FoodLocalDataSource,
    private val remote: FoodRemoteDataSource,
    private val now: () -> Long = { System.currentTimeMillis() },
) : FoodRepository {
    private val sessionItems = ConcurrentHashMap<FoodId, FoodItem>()

    override fun observeAll(): Flow<List<FoodItem>> = local.observeAll()

    override fun observeFavorites(): Flow<List<FoodItem>> = local.observeFavorites()

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> = local.observeRecent(limit)

    override fun searchLocal(query: String): Flow<List<FoodItem>> = local.searchByName(query)

    override suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local> = local.getById(id)

    override fun observeById(id: FoodId): Flow<FoodItem?> = local.observeById(id)

    override suspend fun searchRemote(query: String): Result<List<FoodItem>, DataError.Network> =
        remote.searchByName(query)

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
        // previously edited one. For session-only items the session cache holds
        // the untouched OFF data and serves as snapshot source.
        val existingSnapshot =
            (local.getById(foodItem.id) as? Result.Success)
                ?.data
                ?.originalSnapshot
                ?: sessionItems[foodItem.id]?.originalSnapshot
        // On first edit of a freshly cached OFF entry there is no snapshot yet
        // (cacheWithoutOverwritingUserEdits stores the OFF response as-is with
        // originalSnapshot = null). In that case snapshot the current state so
        // "reset to original" restores what the user saw before their first edit.
        val snapshot =
            existingSnapshot
                ?: sessionItems[foodItem.id]
                ?: foodItem.originalSnapshot
                ?: foodItem.copy(cachedStatus = CachedFoodItemStatus.Original)
        val edited =
            foodItem.copy(
                cachedStatus = CachedFoodItemStatus.UserModified,
                originalSnapshot = snapshot,
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

    override suspend fun toggleFavorite(id: FoodId): EmptyResult<DataError.Local> =
        when (val result = local.getById(id)) {
            is Result.Error -> Result.Error(result.error)
            is Result.Success ->
                result.data?.let { local.setFavorite(id, !it.isFavorite) }
                    ?: sessionItems[id]?.let { remembered ->
                        // Online item viewed via session cache: favoriting is an
                        // explicit use, so persist it permanently first.
                        when (val cached = cacheRemoteItem(remembered)) {
                            is Result.Error -> cached
                            is Result.Success -> local.setFavorite(id, true)
                        }
                    }
                    ?: Result.Error(DataError.Local.NOT_FOUND)
        }

    override suspend fun markUsed(id: FoodId): EmptyResult<DataError.Local> = local.markUsed(id, now())

    override suspend fun cacheRemoteItem(item: FoodItem): EmptyResult<DataError.Local> {
        val existing = local.getById(item.id)
        if (existing is Result.Success && existing.data?.cachedStatus == CachedFoodItemStatus.UserModified) {
            return Result.Success(Unit)
        }
        val toSave =
            item.copy(
                cachedStatus = CachedFoodItemStatus.Original,
                originalSnapshot = item,
            )
        val result = local.upsert(toSave)
        if (result is Result.Success) {
            local.markUsed(item.id, System.currentTimeMillis())
        }
        return result
    }

    override suspend fun rememberRemoteItem(item: FoodItem) {
        sessionItems[item.id] = item
    }

    override suspend fun getSessionItem(id: FoodId): FoodItem? = sessionItems[id]

    /**
     * ADR-004: persist an OFF result only if there is no existing UserModified
     * entry for the same id. Existing user edits are left untouched.
     *
     * The entry is stored with an [originalSnapshot] of itself so that
     * "reset to original" always restores the first cached OFF version.
     */
    private suspend fun cacheWithoutOverwritingUserEdits(item: FoodItem) {
        val existing = (local.getById(item.id) as? Result.Success)?.data
        if (existing?.cachedStatus == CachedFoodItemStatus.UserModified) return
        val withSnapshot =
            item.copy(
                originalSnapshot =
                    item.copy(
                        cachedStatus = CachedFoodItemStatus.Original,
                        originalSnapshot = null,
                    ),
            )
        local.upsert(withSnapshot)
    }
}
