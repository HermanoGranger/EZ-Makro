package com.example.ezmakro.feature.diary.presentation.day

import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.feature.diary.presentation.model.DiaryEntryUi
import com.example.ezmakro.feature.diary.presentation.model.SlotSummaryUi
import java.time.LocalDate

data class DiaryDayState(
    val date: LocalDate = LocalDate.now(),
    val breakfast: List<DiaryEntryUi> = emptyList(),
    val lunch: List<DiaryEntryUi> = emptyList(),
    val dinner: List<DiaryEntryUi> = emptyList(),
    val snacks: List<DiaryEntryUi> = emptyList(),
    val dayTotal: SlotSummaryUi? = null,
    val isCopyingYesterday: Boolean = false,
    val copyYesterdayMessage: UiText? = null,
    val expandedSlots: Set<MealSlot> = emptySet(),
)

fun DiaryDayState.entriesForSlot(slot: MealSlot): List<DiaryEntryUi> =
    when (slot) {
        MealSlot.BREAKFAST -> breakfast
        MealSlot.LUNCH -> lunch
        MealSlot.DINNER -> dinner
        MealSlot.SNACKS -> snacks
    }
