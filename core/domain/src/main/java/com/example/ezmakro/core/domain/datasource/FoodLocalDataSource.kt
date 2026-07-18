package com.example.ezmakro.core.domain.datasource

import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Local Single Source of Truth for food data (Room, ADR-002). The repository
 * writes remote results here and the UI observes only these flows.
 */
@Suppress("TooManyFunctions")
interface FoodLocalDataSource {
    fun observeAll(): Flow<List<FoodItem>>

    /** Observe favorited foods, name-sorted. */
    fun observeFavorites(): Flow<List<FoodItem>>

    /**
     * Observe the [limit] most recently used foods, newest first. Foods that
     * were never used (lastUsedAt == null) are excluded.
     */
    fun observeRecent(limit: Int): Flow<List<FoodItem>>

    suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local>

    suspend fun getByBarcode(barcode: String): Result<FoodItem?, DataError.Local>

    fun searchByName(query: String): Flow<List<FoodItem>>

    suspend fun upsert(foodItem: FoodItem): EmptyResult<DataError.Local>

    suspend fun upsertAll(foodItems: List<FoodItem>): EmptyResult<DataError.Local>

    suspend fun deleteById(id: FoodId): EmptyResult<DataError.Local>

    /** ADR-004: flag a cached entry as locally edited so a sync never overwrites it. */
    suspend fun markUserModified(id: FoodId): EmptyResult<DataError.Local>

    /** ADR-004: a newer OFF version exists; only applied when not UserModified. */
    suspend fun markUpdateAvailable(id: FoodId): EmptyResult<DataError.Local>

    /** ADR-004: restore the untouched OFF snapshot and reset status to Original. */
    suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local>

    /** Set the favorite flag for a food. */
    suspend fun setFavorite(
        id: FoodId,
        favorite: Boolean,
    ): EmptyResult<DataError.Local>

    /** Stamp the food's last-used timestamp (epoch millis) for recent ordering. */
    suspend fun markUsed(
        id: FoodId,
        usedAtEpochMillis: Long,
    ): EmptyResult<DataError.Local>
}
