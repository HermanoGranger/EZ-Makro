package com.example.ezmakro.feature.diary.presentation.day

import com.example.ezmakro.core.domain.model.MealSlot
import java.time.LocalDate

sealed interface DiaryDayAction {
    data object OnCopyYesterdayClick : DiaryDayAction

    data class OnDeleteEntry(
        val entryId: String,
        val slot: MealSlot,
    ) : DiaryDayAction

    data class OnConfirmDelete(
        val entryId: String,
        val slot: MealSlot,
    ) : DiaryDayAction

    data object OnMessageShown : DiaryDayAction

    data class OnToggleSlot(
        val slot: MealSlot,
    ) : DiaryDayAction

    data class OnSaveEdit(
        val entryId: String,
        val amountGrams: Double?,
        val portionCount: Double?,
    ) : DiaryDayAction

    data object OnPreviousDay : DiaryDayAction

    data object OnNextDay : DiaryDayAction

    data object OnTodayClick : DiaryDayAction

    data class OnGoToDate(
        val date: LocalDate,
    ) : DiaryDayAction
}

sealed interface DiaryDayEvent {
    data class ConfirmDelete(
        val entryId: String,
        val slot: MealSlot,
    ) : DiaryDayEvent
}
