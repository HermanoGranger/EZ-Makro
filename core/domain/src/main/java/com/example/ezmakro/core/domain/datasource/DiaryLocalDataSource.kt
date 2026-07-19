package com.example.ezmakro.core.domain.datasource

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Local Single Source of Truth for the meal log (Room, ADR-002).
 *
 * A [DiaryEntry] is an immutable historical record: its [DiaryEntry.food] /
 * [DiaryEntry.recipe] snapshots are persisted as JSON so the entry never needs
 * to reload the source entity to render or calculate (see `DiaryEntryEntity`).
 */
interface DiaryLocalDataSource {
    /** Observe all entries for [date], slot-ordered, for the diary day view. */
    fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>>

    /** Get a single entry by id, or null when it does not exist. */
    suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local>

    /** Persist a diary entry (insert or replace) as one atomic unit. */
    suspend fun upsert(entry: DiaryEntry): EmptyResult<DataError.Local>

    /** Delete a diary entry by id. */
    suspend fun deleteById(id: DiaryEntryId): EmptyResult<DataError.Local>

    /** Get all entries whose date falls in [from]..[to] (inclusive), for history. */
    suspend fun getByDateRange(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<DiaryEntry>, DataError.Local>
}
