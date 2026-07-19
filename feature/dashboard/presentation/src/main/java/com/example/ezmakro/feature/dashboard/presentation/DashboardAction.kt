package com.example.ezmakro.feature.dashboard.presentation

sealed interface DashboardAction {
    data object OnOpenDiaryClick : DashboardAction

    data object OnConfigureGoalsClick : DashboardAction
}
