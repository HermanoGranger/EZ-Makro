package com.example.ezmakro.feature.diary.presentation.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.usecase.diary.CopyYesterdayUseCase
import com.example.ezmakro.core.domain.usecase.diary.DeleteDiaryEntryUseCase
import com.example.ezmakro.feature.diary.presentation.R
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnAddFoodClick
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnAddRecipeClick
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnConfirmDelete
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnCopyYesterdayClick
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnDeleteEntry
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnHistoryClick
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnMessageShown
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnQuickAddClick
import com.example.ezmakro.feature.diary.presentation.model.SlotSummaryUi
import com.example.ezmakro.feature.diary.presentation.model.formatMacro
import com.example.ezmakro.feature.diary.presentation.model.sumNutrition
import com.example.ezmakro.feature.diary.presentation.model.toDiaryEntryUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class DiaryDayViewModel(
    private val repository: DiaryRepository,
    private val copyYesterdayUseCase: CopyYesterdayUseCase,
    private val deleteEntryUseCase: DeleteDiaryEntryUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val date: LocalDate =
        savedStateHandle.get<String>(ARG_DATE)?.let { LocalDate.parse(it) }
            ?: LocalDate.now()

    private val _state = MutableStateFlow(DiaryDayState(date = date))
    val state = _state.asStateFlow()

    private val _events = Channel<DiaryDayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        repository
            .observeByDate(date)
            .onEach { entries ->
                val ui = entries.map { it.toDiaryEntryUi() }
                val total = ui.sumNutrition()
                _state.update {
                    it.copy(
                        breakfast = ui.filter { e -> e.slot == MealSlot.BREAKFAST },
                        lunch = ui.filter { e -> e.slot == MealSlot.LUNCH },
                        dinner = ui.filter { e -> e.slot == MealSlot.DINNER },
                        snacks = ui.filter { e -> e.slot == MealSlot.SNACKS },
                        dayTotal =
                            SlotSummaryUi(
                                slot = null,
                                title = "",
                                kcal = total.kcal.formatMacro(),
                                protein = total.proteinG.formatMacro(),
                                fat = total.fatG.formatMacro(),
                                carbs = total.carbsG.formatMacro(),
                                fiber = total.fiberG.formatMacro(),
                            ),
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: DiaryDayAction) {
        when (action) {
            OnCopyYesterdayClick -> copyYesterday()
            is OnDeleteEntry -> emit(DiaryDayEvent.ConfirmDelete(action.entryId, action.slot))
            is OnConfirmDelete -> delete(action.entryId)
            OnAddFoodClick -> emit(DiaryDayEvent.NavigateToAddFood(date.toString()))
            OnAddRecipeClick -> emit(DiaryDayEvent.NavigateToAddRecipe(date.toString()))
            OnQuickAddClick -> emit(DiaryDayEvent.NavigateToQuickAdd)
            OnHistoryClick -> emit(DiaryDayEvent.NavigateToHistory)
            OnMessageShown -> _state.update { it.copy(copyYesterdayMessage = null) }
        }
    }

    private fun copyYesterday() {
        _state.update { it.copy(isCopyingYesterday = true) }
        viewModelScope.launch {
            when (val result = copyYesterdayUseCase(date)) {
                is Result.Success -> {
                    val msg =
                        android.content.res.Resources
                            .getSystem()
                            .getString(R.string.diary_copy_yesterday_success, result.data)
                    _state.update { it.copy(isCopyingYesterday = false, copyYesterdayMessage = msg) }
                }
                is Result.Error -> {
                    _state.update { it.copy(isCopyingYesterday = false) }
                }
            }
        }
    }

    private fun delete(entryId: String) {
        viewModelScope.launch { deleteEntryUseCase(DiaryEntryId(entryId)) }
    }

    private fun emit(event: DiaryDayEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    companion object {
        const val ARG_DATE = "date"
    }
}
