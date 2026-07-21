package com.example.ezmakro.feature.dashboard.presentation.goals

sealed interface GoalsEvent {
    data object NavigateBack : GoalsEvent
}
