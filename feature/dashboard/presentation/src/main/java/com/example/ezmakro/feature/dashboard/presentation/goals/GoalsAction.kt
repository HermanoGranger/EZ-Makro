package com.example.ezmakro.feature.dashboard.presentation.goals

sealed interface GoalsAction {
    data class OnKcalChange(
        val value: String,
    ) : GoalsAction

    data class OnProteinChange(
        val value: String,
    ) : GoalsAction

    data class OnFatChange(
        val value: String,
    ) : GoalsAction

    data class OnCarbsChange(
        val value: String,
    ) : GoalsAction

    data class OnFiberChange(
        val value: String,
    ) : GoalsAction

    data object OnSaveClick : GoalsAction

    data object OnResetToDefaultClick : GoalsAction

    data object OnBackClick : GoalsAction
}
