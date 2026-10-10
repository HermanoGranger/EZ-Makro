package com.example.ezmakro.feature.diary.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.DefaultNutritionGoal
import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.repository.GoalRepository
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.diary.presentation.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Edits the active [NutritionGoal]. The form is pre-filled from the currently
 * persisted goal; saving validates the (non-negative) numbers and persists them
 * via [GoalRepository.saveGoal] (offline-first, ADR-002).
 */
class GoalsViewModel(
    private val repository: GoalRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GoalsState())
    val state = _state.asStateFlow()

    private val _events = Channel<GoalsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeGoal()
    }

    /**
     * Observe the persisted goal instead of reading it once. A pristine form
     * always mirrors the stored goal; once the user typed anything
     * ([GoalsState.isDirty]) the draft is left alone.
     */
    private fun observeGoal() {
        repository
            .observeGoal()
            .onEach { goal ->
                _state.update { state ->
                    if (state.isDirty) {
                        state
                    } else {
                        state.copy(
                            kcal = goal.kcal.formatValue(),
                            protein = goal.proteinG.formatValue(),
                            fat = goal.fatG.formatValue(),
                            carbs = goal.carbsG.formatValue(),
                            fiber = goal.fiberG.formatValue(),
                        )
                    }
                }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: GoalsAction) {
        when (action) {
            is GoalsAction.OnKcalChange ->
                onUserInput {
                    copy(
                        kcal = action.value,
                        numberError = null,
                        saveError = null,
                    )
                }

            is GoalsAction.OnProteinChange ->
                onUserInput {
                    copy(
                        protein = action.value,
                        numberError = null,
                        saveError = null,
                    )
                }

            is GoalsAction.OnFatChange ->
                onUserInput {
                    copy(
                        fat = action.value,
                        numberError = null,
                        saveError = null,
                    )
                }

            is GoalsAction.OnCarbsChange ->
                onUserInput {
                    copy(
                        carbs = action.value,
                        numberError = null,
                        saveError = null,
                    )
                }

            is GoalsAction.OnFiberChange ->
                onUserInput {
                    copy(
                        fiber = action.value,
                        numberError = null,
                        saveError = null,
                    )
                }

            GoalsAction.OnSaveClick -> save()
            GoalsAction.OnResetToDefaultClick -> resetToDefault()
            GoalsAction.OnBackClick -> emit(GoalsEvent.NavigateBack)
        }
    }

    private fun onUserInput(update: GoalsState.() -> GoalsState) {
        _state.update { it.copy(isDirty = true).update() }
    }

    private fun save() {
        val goal =
            parseGoal()
                ?: run {
                    _state.update {
                        it.copy(numberError = UiText.StringResource(R.string.goals_error_invalid))
                    }
                    return
                }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, numberError = null, saveError = null) }
            persist(goal)
        }
    }

    private fun resetToDefault() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, numberError = null, saveError = null) }
            persist(DefaultNutritionGoal)
        }
    }

    private suspend fun persist(goal: NutritionGoal) {
        try {
            repository.saveGoal(goal)
            _state.update { it.copy(isSaving = false) }
            emit(GoalsEvent.NavigateBack)
        } catch (_: IOException) {
            _state.update {
                it.copy(
                    isSaving = false,
                    saveError = UiText.StringResource(R.string.goals_error_save_failed),
                )
            }
        }
    }

    private fun parseGoal(): NutritionGoal? =
        try {
            NutritionGoal(
                kcal = _state.value.kcal.parseNonNegative(),
                proteinG = _state.value.protein.parseNonNegative(),
                fatG = _state.value.fat.parseNonNegative(),
                carbsG = _state.value.carbs.parseNonNegative(),
                fiberG = _state.value.fiber.parseNonNegative(),
            )
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun String.parseNonNegative(): Double {
        val value = trim().replace(',', '.').ifBlank { "0" }.toDouble()
        require(value >= 0.0)
        return value
    }

    private fun emit(event: GoalsEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
