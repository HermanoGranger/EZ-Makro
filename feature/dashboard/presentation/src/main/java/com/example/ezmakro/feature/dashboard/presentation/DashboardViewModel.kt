package com.example.ezmakro.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.calculation.Macro
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.repository.GoalRepository
import com.example.ezmakro.core.presentation.date.CurrentDateProvider
import com.example.ezmakro.feature.dashboard.presentation.DashboardAction.OnConfigureGoalsClick
import com.example.ezmakro.feature.dashboard.presentation.DashboardAction.OnOpenDiaryClick
import com.example.ezmakro.feature.dashboard.presentation.model.buildDashboardUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val diaryRepository: DiaryRepository,
    private val goalRepository: GoalRepository,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState(date = currentDateProvider.date.value))
    val state = _state.asStateFlow()

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        currentDateProvider.date
            .flatMapLatest { d ->
                diaryRepository
                    .observeByDate(d)
                    .combine(goalRepository.observeGoal()) { entries, goal -> entries to goal }
            }.onEach { (entries, goal) ->
                val ui =
                    buildDashboardUi(
                        entries = entries,
                        goal = goal,
                        macroLabels = ::macroLabel,
                    )
                _state.update { it.copy(date = currentDateProvider.date.value, dashboard = ui, isLoading = false) }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            OnOpenDiaryClick -> emit(DashboardEvent.NavigateToDiary(currentDateProvider.date.value.toString()))
            OnConfigureGoalsClick -> emit(DashboardEvent.NavigateToGoals)
            DashboardAction.OnPreviousDay -> changeDateBy(-1)
            DashboardAction.OnNextDay -> changeDateBy(1)
            DashboardAction.OnTodayClick -> updateDate(LocalDate.now())
        }
    }

    private fun changeDateBy(days: Long) {
        updateDate(currentDateProvider.date.value.plusDays(days))
    }

    private fun updateDate(newDate: LocalDate) {
        currentDateProvider.setDate(newDate)
        _state.update { it.copy(date = newDate) }
    }

    private fun macroLabel(macro: Macro): String =
        when (macro) {
            Macro.PROTEIN -> "Protein"
            Macro.FAT -> "Fett"
            Macro.CARBS -> "Kohlenhydrate"
            Macro.FIBER -> "Ballaststoffe"
            Macro.KCAL -> "Kalorien"
        }

    private fun emit(event: DashboardEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
