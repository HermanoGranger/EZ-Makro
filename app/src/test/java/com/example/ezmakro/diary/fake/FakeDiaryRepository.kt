package com.example.ezmakro.diary.fake

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

class FakeDiaryRepository : DiaryRepository {
    val saved = mutableListOf<DiaryEntry>()
    var saveResult: EmptyResult<DataError.Local> = Result.Success(Unit)

    override fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>> = flowOf(saved.filter { it.date == date })

    override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> =
        Result.Success(saved.firstOrNull { it.id == id })

    override suspend fun save(entry: DiaryEntry): EmptyResult<DataError.Local> {
        if (saveResult is Result.Error) return saveResult
        saved += entry
        return Result.Success(Unit)
    }

    override suspend fun delete(id: DiaryEntryId): EmptyResult<DataError.Local> {
        saved.removeIf { it.id == id }
        return Result.Success(Unit)
    }

    override fun observeByDateRange(range: LocalDateRange): Flow<List<DiaryEntry>> = flowOf(emptyList())
}
