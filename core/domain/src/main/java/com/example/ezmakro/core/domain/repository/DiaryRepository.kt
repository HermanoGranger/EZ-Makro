package com.example.ezmakro.core.domain.repository

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Offline-first coordination for the meal log (ADR-002): the UI observes local
 * flows only. Diary entries are immutable historical records with materialized
 * food/recipe snapshots, so this repository has no remote source.
 */
interface DiaryRepository {
    /** Observe all entries for [date], slot-ordered. */
    fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>>

    /** Get a single entry by id. */
    suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local>

    /** Persist a new or updated diary entry. */
    suspend fun save(entry: DiaryEntry): EmptyResult<DataError.Local>

    /** Delete a diary entry. */
    suspend fun delete(id: DiaryEntryId): EmptyResult<DataError.Local>

    /** Get all entries within [range] (inclusive), newest first, for history. */
    suspend fun getHistory(range: LocalDateRange): Result<List<DiaryEntry>, DataError.Local>
}
