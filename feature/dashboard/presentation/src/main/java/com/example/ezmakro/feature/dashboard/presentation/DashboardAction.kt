package com.example.ezmakro.feature.dashboard.presentation

sealed interface DashboardAction {
    data object OnOpenDiaryClick : DashboardAction

    data object OnConfigureGoalsClick : DashboardAction

    data object OnPreviousDay : DashboardAction

    data object OnNextDay : DashboardAction

    data object OnTodayClick : DashboardAction
}
