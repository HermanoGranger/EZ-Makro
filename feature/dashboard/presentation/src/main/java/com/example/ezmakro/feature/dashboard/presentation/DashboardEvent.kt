package com.example.ezmakro.feature.dashboard.presentation

sealed interface DashboardEvent {
    data class NavigateToDiary(
        val date: String,
    ) : DashboardEvent

    object NavigateToGoals : DashboardEvent
}
