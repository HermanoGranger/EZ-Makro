package com.example.ezmakro.feature.diary.presentation.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.calculation.Macro
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.repository.GoalRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.usecase.diary.CopyYesterdayUseCase
import com.example.ezmakro.core.domain.usecase.diary.DeleteDiaryEntryUseCase
import com.example.ezmakro.core.domain.usecase.diary.UpdateDiaryEntryUseCase
import com.example.ezmakro.core.presentation.date.CurrentDateProvider
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.core.presentation.uitext.toUiText
import com.example.ezmakro.feature.diary.presentation.R
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnConfirmDelete
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnCopyYesterdayClick
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnDeleteEntry
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnMessageShown
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnSaveEdit
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayAction.OnToggleSlot
import com.example.ezmakro.feature.diary.presentation.model.buildDiarySummary
import com.example.ezmakro.feature.diary.presentation.model.toDiaryEntryUi
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
class DiaryDayViewModel(
    private val repository: DiaryRepository,
    private val goalRepository: GoalRepository,
    private val recipeRepository: RecipeRepository,
    private val copyYesterdayUseCase: CopyYesterdayUseCase,
    private val deleteEntryUseCase: DeleteDiaryEntryUseCase,
    private val updateEntryUseCase: UpdateDiaryEntryUseCase,
    private val currentDateProvider: CurrentDateProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryDayState(date = currentDateProvider.date.value))
    val state = _state.asStateFlow()

    private val _events = Channel<DiaryDayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        savedStateHandle.get<String>(ARG_DATE)?.takeIf { it.isNotEmpty() }?.let {
            currentDateProvider.setDate(LocalDate.parse(it))
        }

        currentDateProvider.date
            .flatMapLatest { d ->
                repository
                    .observeByDate(d)
                    .combine(goalRepository.observeGoal()) { entries, goal -> entries to goal }
                    .combine(recipeRepository.observeAll()) { (entries, goal), recipes ->
                        Triple(entries, goal, recipes)
                    }
            }.onEach { (entries, goal, recipes) ->
                val modifiedAt = recipes.associate { it.id.value to it.lastModifiedAt }
                val ui =
                    entries.map { entry ->
                        val base = entry.toDiaryEntryUi()
                        val snapshot = entry.recipe
                        val liveAt = snapshot?.let { modifiedAt[it.id.value] }
                        base.copy(
                            isSourceRecipeOutdated =
                                snapshot != null &&
                                    liveAt != null &&
                                    liveAt > snapshot.lastModifiedAt,
                        )
                    }
                _state.update {
                    it.copy(
                        date = currentDateProvider.date.value,
                        breakfast = ui.filter { e -> e.slot == MealSlot.BREAKFAST },
                        lunch = ui.filter { e -> e.slot == MealSlot.LUNCH },
                        dinner = ui.filter { e -> e.slot == MealSlot.DINNER },
                        snacks = ui.filter { e -> e.slot == MealSlot.SNACKS },
                        summary = buildDiarySummary(entries, goal, ::macroLabel),
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: DiaryDayAction) {
        when (action) {
            OnCopyYesterdayClick -> copyYesterday()
            is OnDeleteEntry -> emit(DiaryDayEvent.ConfirmDelete(action.entryId, action.slot))
            is OnConfirmDelete -> delete(action.entryId)
            OnMessageShown -> _state.update { it.copy(copyYesterdayMessage = null, actionError = null) }
            is OnToggleSlot -> {
                _state.update {
                    it.copy(
                        expandedSlots =
                            if (it.expandedSlots.contains(action.slot)) {
                                it.expandedSlots - action.slot
                            } else {
                                it.expandedSlots + action.slot
                            },
                    )
                }
            }
            is OnSaveEdit -> {
                updateEntry(action.entryId, action.amountGrams, action.portionCount)
            }
            DiaryDayAction.OnPreviousDay -> changeDateBy(-1)
            DiaryDayAction.OnNextDay -> changeDateBy(1)
            DiaryDayAction.OnTodayClick -> updateDate(LocalDate.now())
            is DiaryDayAction.OnGoToDate -> updateDate(action.date)
        }
    }

    private fun changeDateBy(days: Long) {
        updateDate(currentDateProvider.date.value.plusDays(days))
    }

    private fun updateDate(newDate: LocalDate) {
        currentDateProvider.setDate(newDate)
        savedStateHandle[ARG_DATE] = newDate.toString()
        _state.update { it.copy(date = newDate, expandedSlots = MealSlot.entries.toSet()) }
    }

    private fun copyYesterday() {
        _state.update { it.copy(isCopyingYesterday = true) }
        viewModelScope.launch {
            when (val result = copyYesterdayUseCase(currentDateProvider.date.value)) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            isCopyingYesterday = false,
                            copyYesterdayMessage =
                                UiText.StringResource(
                                    R.string.diary_copy_yesterday_success,
                                    arrayOf(result.data),
                                ),
                        )
                    }
                }
                is Result.Error -> {
                    _state.update { it.copy(isCopyingYesterday = false) }
                }
            }
        }
    }

    private fun delete(entryId: String) {
        viewModelScope.launch {
            deleteEntryUseCase(DiaryEntryId(entryId))
                .onFailure { error -> _state.update { it.copy(actionError = error.toUiText()) } }
        }
    }

    private fun updateEntry(
        entryId: String,
        amountGrams: Double?,
        portionCount: Double?,
    ) {
        viewModelScope.launch {
            updateEntryUseCase(DiaryEntryId(entryId), amountGrams, portionCount)
                .onFailure { error -> _state.update { it.copy(actionError = error.toUiText()) } }
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

    private fun emit(event: DiaryDayEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    companion object {
        const val ARG_DATE = "date"
    }
}
