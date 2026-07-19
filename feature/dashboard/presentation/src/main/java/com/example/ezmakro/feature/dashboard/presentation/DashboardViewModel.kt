package com.example.ezmakro.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.calculation.Macro
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.repository.GoalRepository
import com.example.ezmakro.feature.dashboard.presentation.DashboardAction.OnConfigureGoalsClick
import com.example.ezmakro.feature.dashboard.presentation.DashboardAction.OnOpenDiaryClick
import com.example.ezmakro.feature.dashboard.presentation.model.buildDashboardUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(
    private val diaryRepository: DiaryRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {
    private val date: LocalDate = LocalDate.now()

    private val _state = MutableStateFlow(DashboardState(date = date))
    val state = _state.asStateFlow()

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        diaryRepository
            .observeByDate(date)
            .combine(goalRepository.observeGoal()) { entries, goal -> entries to goal }
            .onEach { (entries, goal) ->
                val ui =
                    buildDashboardUi(
                        entries = entries,
                        goal = goal,
                        macroLabels = ::macroLabel,
                    )
                _state.update { it.copy(dashboard = ui, isLoading = false) }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            OnOpenDiaryClick -> emit(DashboardEvent.NavigateToDiary(date.toString()))
            OnConfigureGoalsClick -> emit(DashboardEvent.NavigateToGoals)
        }
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
