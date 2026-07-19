package com.example.ezmakro.feature.diary.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.usecase.diary.GetHistoryUseCase
import com.example.ezmakro.feature.diary.presentation.model.formatMacro
import com.example.ezmakro.feature.diary.presentation.model.sumNutrition
import com.example.ezmakro.feature.diary.presentation.model.toDiaryEntryUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val HISTORY_DAYS = 29L

data class HistoryDayUi(
    val date: String,
    val kcal: String,
    val entryCount: Int,
)

data class HistoryState(
    val days: List<HistoryDayUi> = emptyList(),
    val isLoading: Boolean = false,
)

class HistoryViewModel(
    private val getHistory: GetHistoryUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState(isLoading = true))
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val end = LocalDate.now()
            val range = LocalDateRange(from = end.minusDays(HISTORY_DAYS), to = end)
            when (val result = getHistory(range)) {
                is Result.Success -> _state.update { it.copy(isLoading = false, days = group(result.data)) }
                is Result.Error -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun group(entries: List<DiaryEntry>): List<HistoryDayUi> {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        return entries
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                val total = list.map { it.toDiaryEntryUi() }.sumNutrition()
                HistoryDayUi(
                    date = date.format(fmt),
                    kcal = total.kcal.formatMacro(),
                    entryCount = list.size,
                )
            }
    }
}
