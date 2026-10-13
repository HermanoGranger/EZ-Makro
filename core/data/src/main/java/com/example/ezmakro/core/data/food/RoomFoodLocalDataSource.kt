package com.example.ezmakro.core.data.food

import com.example.ezmakro.core.data.food.mapper.toFoodItem
import com.example.ezmakro.core.data.food.mapper.upsertFood
import com.example.ezmakro.core.database.dao.FoodItemDao
import com.example.ezmakro.core.database.dao.FtsQuerySanitizer
import com.example.ezmakro.core.domain.datasource.FoodLocalDataSource
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed Single Source of Truth for foods (ADR-002). Wraps [FoodItemDao],
 * mapping entities to domain [FoodItem]s and DAO exceptions to
 * [DataError.Local]. ADR-004 status transitions are delegated to the DAO.
 */
@Suppress("TooManyFunctions")
class RoomFoodLocalDataSource(
    private val dao: FoodItemDao,
) : FoodLocalDataSource {
    override fun observeAll(): Flow<List<FoodItem>> = dao.observeAll().map { list -> list.map { it.toFoodItem() } }

    override fun observeFavorites(): Flow<List<FoodItem>> =
        dao.observeFavorites().map { list -> list.map { it.toFoodItem() } }

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> =
        dao.observeRecent(limit).map { list -> list.map { it.toFoodItem() } }

    override suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local> =
        runCatchingLocal { dao.getById(id.value)?.toFoodItem() }

    override fun observeById(id: FoodId): Flow<FoodItem?> = dao.observeById(id.value).map { it?.toFoodItem() }

    override suspend fun getByBarcode(barcode: String): Result<FoodItem?, DataError.Local> =
        runCatchingLocal { dao.getByBarcode(barcode)?.toFoodItem() }

    /**
     * Hybrid local search over the observed table:
     * - **Multi-term contains (in-memory):** every whitespace-separated term
     *   must appear case-insensitively in the name — "mehl 550" finds
     *   "Weizenmehl Type 550", independent of order and intervening words.
     * - **FTS4 per-term prefix:** `mehl* 550*` adds umlaut folding the
     *   in-memory part cannot do ("musli" finds "Müsli").
     *
     * Results are merged, deduplicated by row id and ordered by name. The
     * in-memory part is safe because the table (a few hundred to low
     * thousands of short names) is already fully loaded by [observeAll].
     */
    override fun searchByName(query: String): Flow<List<FoodItem>> =
        dao
            .observeAll()
            .map { all ->
                val normalized = query.trim().lowercase()
                val terms = normalized.split(WHITESPACE).filter { it.isNotEmpty() }
                if (terms.isEmpty()) {
                    emptyList()
                } else {
                    runCatching {
                        val ftsMatches =
                            terms
                                .mapNotNull { term ->
                                    FtsQuerySanitizer.sanitize(term).takeIf { sanitized -> sanitized.isNotEmpty() }
                                }.joinToString(separator = " ") { "$it*" }
                                .takeIf { ftsQuery -> ftsQuery.isNotEmpty() }
                                ?.let { ftsQuery -> dao.getByRowIds(dao.searchRowIds(ftsQuery)) }
                                .orEmpty()
                        val containsMatches =
                            all.filter { entity ->
                                val name = entity.name.lowercase()
                                terms.all { term -> name.contains(term) }
                            }
                        (ftsMatches + containsMatches)
                            .distinctBy { entity -> entity.rowId }
                            .sortedBy { entity -> entity.name.lowercase() }
                            .map { entity -> entity.toFoodItem() }
                    }.getOrDefault(emptyList())
                }
            }.flowOn(Dispatchers.IO)

    override suspend fun upsert(foodItem: FoodItem): EmptyResult<DataError.Local> =
        runCatchingLocal { dao.upsertFood(foodItem) }

    override suspend fun upsertAll(foodItems: List<FoodItem>): EmptyResult<DataError.Local> =
        runCatchingLocal { foodItems.forEach { dao.upsertFood(it) } }

    override suspend fun deleteById(id: FoodId): EmptyResult<DataError.Local> =
        runCatchingLocal { dao.deleteByIdAndCleanup(id.value) }

    override suspend fun markUserModified(id: FoodId): EmptyResult<DataError.Local> =
        runCatchingLocal { dao.markUserModified(id.value) }

    override suspend fun markUpdateAvailable(id: FoodId): EmptyResult<DataError.Local> =
        runCatchingLocal { dao.markUpdateAvailable(id.value) }

    override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> =
        runCatchingLocal {
            val snapshot =
                dao.getById(id.value)?.toFoodItem()?.originalSnapshot
                    ?: error("No original snapshot to reset to for id=${id.value}")
            dao.resetToOriginal(
                id = id.value,
                origName = snapshot.name,
                origBarcode = snapshot.barcode,
                origKcal = snapshot.nutritionPer100g?.kcal,
                origProteinG = snapshot.nutritionPer100g?.proteinG,
                origFatG = snapshot.nutritionPer100g?.fatG,
                origCarbsG = snapshot.nutritionPer100g?.carbsG,
                origFiberG = snapshot.nutritionPer100g?.fiberG,
                origServingLabel = snapshot.servingUnit?.label,
                origServingGrams = snapshot.servingUnit?.gramsEquivalent,
                origIsUserCreated = snapshot.isUserCreated,
            )
        }

    override suspend fun setFavorite(
        id: FoodId,
        favorite: Boolean,
    ): EmptyResult<DataError.Local> = runCatchingLocal { dao.setFavorite(id.value, favorite) }

    override suspend fun markUsed(
        id: FoodId,
        usedAtEpochMillis: Long,
    ): EmptyResult<DataError.Local> = runCatchingLocal { dao.markUsed(id.value, usedAtEpochMillis) }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** Map DAO exceptions to a typed [DataError.Local], preserving cancellation. */
@Suppress("TooGenericExceptionCaught")
private suspend inline fun <T> runCatchingLocal(crossinline block: () -> T): Result<T, DataError.Local> =
    withContext(Dispatchers.IO) {
        try {
            Result.Success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.example.ezmakro.core.data.logging.AppLogger.e(
                "Local data error",
                e,
                tag = "RoomFoodLocalDataSource",
            )
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
