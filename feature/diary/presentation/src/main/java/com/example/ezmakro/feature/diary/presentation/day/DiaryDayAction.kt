package com.example.ezmakro.feature.diary.presentation.day

import com.example.ezmakro.core.domain.model.MealSlot

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

    data object OnAddFoodClick : DiaryDayAction

    data object OnAddRecipeClick : DiaryDayAction

    data object OnQuickAddClick : DiaryDayAction

    data object OnHistoryClick : DiaryDayAction

    data object OnMessageShown : DiaryDayAction
}

sealed interface DiaryDayEvent {
    data class NavigateToAddFood(
        val date: String,
    ) : DiaryDayEvent

    data class NavigateToAddRecipe(
        val date: String,
    ) : DiaryDayEvent

    data object NavigateToQuickAdd : DiaryDayEvent

    data object NavigateToHistory : DiaryDayEvent

    data class ConfirmDelete(
        val entryId: String,
        val slot: MealSlot,
    ) : DiaryDayEvent
}
