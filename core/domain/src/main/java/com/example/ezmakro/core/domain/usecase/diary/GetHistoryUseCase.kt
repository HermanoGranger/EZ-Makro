package com.example.ezmakro.core.domain.usecase.diary

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observe the diary history (entries within an inclusive [range]) for the history
 * screen. The repository returns them slot-ordered within each day; callers may
 * further group by date. Returns a flow so the screen updates on its own after
 * diary changes made on a pushed day screen.
 */
class GetHistoryUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(range: LocalDateRange): Flow<List<DiaryEntry>> = repository.observeByDateRange(range)
}
