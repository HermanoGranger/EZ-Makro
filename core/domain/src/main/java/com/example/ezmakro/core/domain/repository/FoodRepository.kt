package com.example.ezmakro.core.domain.repository

import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first coordination between the local Room cache and the OpenFoodFacts
 * remote source (ADR-002). The UI always observes local flows; remote calls
 * only refresh the cache.
 *
 * ADR-004: a remote refresh MUST NOT silently overwrite a [FoodItem] whose
 * status is [com.example.ezmakro.core.domain.model.CachedFoodItemStatus.UserModified];
 * the user resets or re-fetches explicitly.
 */
@Suppress("TooManyFunctions")
interface FoodRepository {
    /** Observe all locally cached / user-created foods (Single Source of Truth). */
    fun observeAll(): Flow<List<FoodItem>>

    /** Observe favorited foods for the favorites section of the food picker. */
    fun observeFavorites(): Flow<List<FoodItem>>

    /**
     * Observe the [limit] most recently used foods, newest first. Used-tracking
     * is wired with the meal log in Phase 7; until then this is empty.
     */
    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<FoodItem>>

    /** Observe local FTS results for [query]. */
    fun searchLocal(query: String): Flow<List<FoodItem>>

    /** Get a single food by id, or null when it does not exist. */
    suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local>

    /**
     * Observe a single food by [id], emitting null when it does not exist. The
     * editor uses this instead of a one-shot read so external changes (e.g. a
     * favorite toggled on the detail screen) are mirrored while it stays open.
     */
    fun observeById(id: FoodId): Flow<FoodItem?>

    /**
     * Search OFF and persist the results into the local cache. Existing
     * UserModified entries are preserved (never overwritten). Returns the
     * freshly fetched items on success.
     */
    suspend fun searchRemote(query: String): Result<List<FoodItem>, DataError.Network>

    /**
     * Look up [barcode] locally first, then OFF. Persists a remote hit. Returns
     * `Success(null)` when neither source has the product, so callers can route
     * into the "create own food" fallback with the barcode prefilled.
     */
    suspend fun lookupByBarcode(barcode: String): Result<FoodItem?, DataError>

    /** Persist a user edit and flag the entry UserModified (ADR-004). */
    suspend fun saveUserEdit(foodItem: FoodItem): EmptyResult<DataError.Local>

    /**
     * Permanently remove a food from the local database. Also drops any
     * session-only copy. Recipes that referenced it lose that ingredient; a
     * recipe left without any resolvable ingredient is removed as well. Diary
     * entries keep their materialized snapshot (FK `SET NULL` only clears the
     * traceability id).
     */
    suspend fun delete(id: FoodId): EmptyResult<DataError.Local>

    /** ADR-004: restore the untouched OFF snapshot. */
    suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local>

    /**
     * ADR-004: explicit, user-confirmed re-fetch of the current OFF version.
     * Because the user confirmed, a locally edited ([FoodItem.cachedStatus] ==
     * UserModified) entry **is** replaced and its local changes are discarded —
     * this is the difference between a manual refetch and an automatic sync,
     * which never overwrites UserModified data. User-created foods are never
     * replaced. Callers confirm with the user beforehand.
     */
    suspend fun refetchFromOff(id: FoodId): Result<FoodItem?, DataError>

    /** Toggle the favorite flag for a food. */
    suspend fun toggleFavorite(id: FoodId): EmptyResult<DataError.Local>

    /**
     * Stamp a food as just used (recent ordering). Wired with the meal log in
     * Phase 7; defined now so the data contract is complete.
     */
    suspend fun markUsed(id: FoodId): EmptyResult<DataError.Local>

    /** Explicitly cache a remote [FoodItem] locally (deferred caching). */
    suspend fun cacheRemoteItem(item: FoodItem): EmptyResult<DataError.Local>

    /**
     * Keep a remote [FoodItem] available for the current session in memory only,
     * WITHOUT persisting it locally. Used so the detail screen can render online
     * results before the user decides to use them; a later [cacheRemoteItem]
     * (favorite, diary entry, recipe ingredient) persists them permanently.
     */
    suspend fun rememberRemoteItem(item: FoodItem)

    /**
     * Read an entry that may only exist in the session cache (see
     * [rememberRemoteItem]). Returns null when the item is not remembered.
     */
    suspend fun getSessionItem(id: FoodId): FoodItem?

    companion object {
        const val DEFAULT_RECENT_LIMIT = 20
    }
}
