package com.example.ezmakro.feature.diary.presentation.quickadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.usecase.diary.QuickAddUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class QuickAddState(
    val name: String = "",
    val kcal: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val fiber: String = "",
    val amountGrams: String = "",
    val slot: MealSlot = MealSlot.BREAKFAST,
    val isSaving: Boolean = false,
    val error: String? = null,
)

sealed interface QuickAddAction {
    data class OnNameChange(
        val value: String,
    ) : QuickAddAction

    data class OnKcalChange(
        val value: String,
    ) : QuickAddAction

    data class OnProteinChange(
        val value: String,
    ) : QuickAddAction

    data class OnFatChange(
        val value: String,
    ) : QuickAddAction

    data class OnCarbsChange(
        val value: String,
    ) : QuickAddAction

    data class OnFiberChange(
        val value: String,
    ) : QuickAddAction

    data class OnAmountChange(
        val value: String,
    ) : QuickAddAction

    data class OnSlotChange(
        val slot: MealSlot,
    ) : QuickAddAction

    data object OnSaveClick : QuickAddAction
}

sealed interface QuickAddEvent {
    data object Saved : QuickAddEvent
}

class QuickAddViewModel(
    private val quickAdd: QuickAddUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val date: LocalDate =
        savedStateHandle.get<String>("date")?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
            ?: LocalDate.now()

    private val _state = MutableStateFlow(QuickAddState())
    val state = _state.asStateFlow()

    private val _events = Channel<QuickAddEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: QuickAddAction) {
        when (action) {
            is QuickAddAction.OnNameChange -> _state.update { it.copy(name = action.value, error = null) }
            is QuickAddAction.OnKcalChange -> _state.update { it.copy(kcal = action.value, error = null) }
            is QuickAddAction.OnProteinChange -> _state.update { it.copy(protein = action.value, error = null) }
            is QuickAddAction.OnFatChange -> _state.update { it.copy(fat = action.value, error = null) }
            is QuickAddAction.OnCarbsChange -> _state.update { it.copy(carbs = action.value, error = null) }
            is QuickAddAction.OnFiberChange -> _state.update { it.copy(fiber = action.value, error = null) }
            is QuickAddAction.OnAmountChange -> _state.update { it.copy(amountGrams = action.value, error = null) }
            is QuickAddAction.OnSlotChange -> _state.update { it.copy(slot = action.slot) }
            QuickAddAction.OnSaveClick -> save()
        }
    }

    private fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(error = "Name darf nicht leer sein") }
            return
        }
        val amount = s.amountGrams.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _state.update { it.copy(error = "Menge muss größer als 0 sein") }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result =
                quickAdd(
                    name = s.name.trim(),
                    nutritionPer100g =
                        com.example.ezmakro.core.domain.model.NutritionValues(
                            kcal = s.kcal.toDoubleOrNull() ?: 0.0,
                            proteinG = s.protein.toDoubleOrNull() ?: 0.0,
                            fatG = s.fat.toDoubleOrNull() ?: 0.0,
                            carbsG = s.carbs.toDoubleOrNull() ?: 0.0,
                            fiberG = s.fiber.toDoubleOrNull() ?: 0.0,
                        ),
                    slot = s.slot,
                    date = date,
                    amountGrams = amount,
                )
            when (result) {
                is Result.Success -> _events.send(QuickAddEvent.Saved)
                is Result.Error -> _state.update { it.copy(isSaving = false, error = "Speichern fehlgeschlagen") }
            }
        }
    }
}
