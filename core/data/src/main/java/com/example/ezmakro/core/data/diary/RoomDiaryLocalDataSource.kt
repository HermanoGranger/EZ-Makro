package com.example.ezmakro.core.data.diary

import com.example.ezmakro.core.data.diary.mapper.toDiaryEntry
import com.example.ezmakro.core.data.diary.mapper.toEntity
import com.example.ezmakro.core.database.dao.DiaryEntryDao
import com.example.ezmakro.core.domain.datasource.DiaryLocalDataSource
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Room-backed Single Source of Truth for the meal log (ADR-002). Wraps
 * [DiaryEntryDao], mapping entities to domain [DiaryEntry]s (with their
 * materialized food/recipe JSON snapshots) and DAO exceptions to
 * [DataError.Local].
 */
class RoomDiaryLocalDataSource(
    private val dao: DiaryEntryDao,
) : DiaryLocalDataSource {
    override fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>> =
        dao
            .observeByDate(date.toString())
            .map { list -> list.map { it.toDiaryEntry() } }

    override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> =
        runCatchingLocal { dao.getById(id.value)?.toDiaryEntry() }

    override suspend fun upsert(entry: DiaryEntry): EmptyResult<DataError.Local> =
        runCatchingLocal {
            val existing = dao.getById(entry.id.value)
            val entity = entry.toEntity()
            // Preserve existing FK values — if the referenced recipe or food was
            // deleted the ON DELETE SET NULL trigger already nulled them out, and
            // setting them back to the deleted ID would cause a FK violation.
            dao.upsert(
                if (existing != null) {
                    entity.copy(
                        foodItemId = existing.foodItemId,
                        recipeId = existing.recipeId,
                    )
                } else {
                    entity
                },
            )
        }

    override suspend fun deleteById(id: DiaryEntryId): EmptyResult<DataError.Local> =
        runCatchingLocal { dao.deleteById(id.value) }

    override suspend fun getByDateRange(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<DiaryEntry>, DataError.Local> =
        runCatchingLocal {
            dao
                .getByDateRange(from.toString(), to.toString())
                .map { it.toDiaryEntry() }
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
                tag = "RoomDiaryLocalDataSource",
            )
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
