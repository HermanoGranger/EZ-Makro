package com.example.ezmakro.feature.dashboard.presentation.fake

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * In-memory [DiaryRepository] for dashboard ViewModel tests. Mirrors the diary
 * feature's fake; kept local so the dashboard test sources don't depend on
 * another feature module's test fixtures.
 */
class FakeDiaryRepository : DiaryRepository {
    val entries = MutableStateFlow<List<DiaryEntry>>(emptyList())

    fun emitEntry(entry: DiaryEntry) {
        entries.value = entries.value.filter { it.id != entry.id } + entry
    }

    override fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>> =
        entries.map { list -> list.filter { it.date == date } }

    override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> =
        Result.Success(entries.value.firstOrNull { it.id == id })

    override suspend fun save(entry: DiaryEntry): EmptyResult<DataError.Local> {
        entries.value = entries.value.filter { it.id != entry.id } + entry
        return Result.Success(Unit)
    }

    override suspend fun delete(id: DiaryEntryId): EmptyResult<DataError.Local> {
        entries.value = entries.value.filter { it.id != id }
        return Result.Success(Unit)
    }

    override suspend fun getHistory(range: LocalDateRange): Result<List<DiaryEntry>, DataError.Local> =
        Result.Success(entries.value.filter { !it.date.isBefore(range.from) && !it.date.isAfter(range.to) })
}
