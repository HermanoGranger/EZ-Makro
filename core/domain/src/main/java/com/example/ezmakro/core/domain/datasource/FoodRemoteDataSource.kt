package com.example.ezmakro.core.domain.datasource

import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result

/**
 * Remote source for food data (OpenFoodFacts). Returns freshly mapped
 * [FoodItem]s with [com.example.ezmakro.core.domain.model.CachedFoodItemStatus.Original]
 * status; persistence and offline-first coordination is the repository's job.
 *
 * Implementations MUST send a meaningful User-Agent header on every request
 * (App name, version, contact) — OFF rate-limits/blocks otherwise (scope/ADR).
 */
interface FoodRemoteDataSource {
    /** Full-text search on OFF for the given [query]. */
    suspend fun searchByName(query: String): Result<List<FoodItem>, DataError.Network>

    /**
     * Look up a single product by [barcode]. Returns `Success(null)` when OFF
     * has no product for the barcode (distinct from a network failure), so the
     * caller can offer the "create own food" fallback flow.
     */
    suspend fun getByBarcode(barcode: String): Result<FoodItem?, DataError.Network>
}
