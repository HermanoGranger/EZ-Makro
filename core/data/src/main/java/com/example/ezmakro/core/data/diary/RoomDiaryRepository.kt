package com.example.ezmakro.core.data.diary

import com.example.ezmakro.core.domain.datasource.DiaryLocalDataSource
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Offline-first meal-log repository (ADR-002): diary entries have no remote
 * source, so [local] is the sole Single Source of Truth. The UI observes only
 * the local flows. Entries are immutable historical records with materialized
 * food/recipe snapshots.
 */
class RoomDiaryRepository(
    private val local: DiaryLocalDataSource,
) : DiaryRepository {
    override fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>> = local.observeByDate(date)

    override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> = local.getById(id)

    override suspend fun save(entry: DiaryEntry): EmptyResult<DataError.Local> = local.upsert(entry)

    override suspend fun delete(id: DiaryEntryId): EmptyResult<DataError.Local> = local.deleteById(id)

    override fun observeByDateRange(range: LocalDateRange): Flow<List<DiaryEntry>> =
        local.observeByDateRange(range.from, range.to)
}
