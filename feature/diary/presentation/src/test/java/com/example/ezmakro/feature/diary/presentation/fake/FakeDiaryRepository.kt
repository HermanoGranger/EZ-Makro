package com.example.ezmakro.feature.diary.presentation.fake

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeDiaryRepository : DiaryRepository {
    val entries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    private val deleted = mutableListOf<DiaryEntryId>()
    private val saved = mutableListOf<DiaryEntry>()

    fun emitEntry(entry: DiaryEntry) {
        entries.value = entries.value.filter { it.id != entry.id } + entry
    }

    fun entriesForDate(date: LocalDate): List<DiaryEntry> = entries.value.filter { it.date == date }

    fun savedCount(): Int = saved.size

    fun deletedIds(): List<DiaryEntryId> = deleted

    override fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>> =
        entries.map { list -> list.filter { it.date == date } }

    override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> =
        Result.Success(entries.value.firstOrNull { it.id == id })

    override suspend fun save(entry: DiaryEntry): com.example.ezmakro.core.domain.result.EmptyResult<DataError.Local> {
        saved.add(entry)
        entries.value = entries.value.filter { it.id != entry.id } + entry
        return Result.Success(Unit)
    }

    override suspend fun delete(id: DiaryEntryId): com.example.ezmakro.core.domain.result.EmptyResult<DataError.Local> {
        deleted.add(id)
        entries.value = entries.value.filter { it.id != id }
        return Result.Success(Unit)
    }

    override fun observeByDateRange(range: LocalDateRange): Flow<List<DiaryEntry>> =
        entries.map { list ->
            list
                .filter { !it.date.isBefore(range.from) && !it.date.isAfter(range.to) }
                .sortedWith(compareBy({ it.date }, { it.slot }))
        }
}
