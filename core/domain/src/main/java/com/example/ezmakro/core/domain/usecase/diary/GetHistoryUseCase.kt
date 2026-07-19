package com.example.ezmakro.core.domain.usecase.diary

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result

/**
 * Load the diary history (entries within an inclusive [range]), newest first.
 * Used by the history screen. The repository returns them slot-ordered within
 * each day; callers may further group by date.
 */
class GetHistoryUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(range: LocalDateRange): Result<List<DiaryEntry>, DataError.Local> =
        repository.getHistory(range)
}
