package com.example.ezmakro.feature.diary.presentation.goals

sealed interface GoalsEvent {
    data object NavigateBack : GoalsEvent
}
