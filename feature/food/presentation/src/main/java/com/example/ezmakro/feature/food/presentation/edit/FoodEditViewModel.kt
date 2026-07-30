package com.example.ezmakro.feature.food.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.feature.food.presentation.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Edits an existing food ([foodId] non-null) or creates a new user food
 * ([foodId] null). When arriving from a barcode miss, [prefillBarcode] seeds the
 * barcode field (scope/Phase 4 fallback). Form fields survive process death via
 * [SavedStateHandle].
 */
class FoodEditViewModel(
    private val foodId: String?,
    private val prefillBarcode: String?,
    private val repository: FoodRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            FoodEditState(
                isEditingExisting = foodId != null,
                barcode = savedStateHandle[KEY_BARCODE] ?: prefillBarcode.orEmpty(),
                name = savedStateHandle[KEY_NAME] ?: "",
            ),
        )
    val state = _state.asStateFlow()

    private val _events = Channel<FoodEditEvent>()
    val events = _events.receiveAsFlow()

    init {
        if (foodId != null) loadExisting(foodId)
    }

    fun onAction(action: FoodEditAction) {
        when (action) {
            is FoodEditAction.OnNameChange -> {
                savedStateHandle[KEY_NAME] = action.value
                _state.update { it.copy(name = action.value, nameError = null) }
            }

            is FoodEditAction.OnBarcodeChange -> {
                savedStateHandle[KEY_BARCODE] = action.value
                _state.update { it.copy(barcode = action.value) }
            }

            is FoodEditAction.OnKcalChange -> _state.update { it.copy(kcal = action.value, numberError = null) }
            is FoodEditAction.OnProteinChange -> _state.update { it.copy(protein = action.value, numberError = null) }
            is FoodEditAction.OnFatChange -> _state.update { it.copy(fat = action.value, numberError = null) }
            is FoodEditAction.OnCarbsChange -> _state.update { it.copy(carbs = action.value, numberError = null) }
            is FoodEditAction.OnFiberChange -> _state.update { it.copy(fiber = action.value, numberError = null) }
            is FoodEditAction.OnPortionGramsChange -> _state.update { it.copy(portionGrams = action.value) }

            FoodEditAction.OnToggleFavorite -> _state.update { it.copy(isFavorite = !it.isFavorite) }

            FoodEditAction.OnSaveClick -> save()
            FoodEditAction.OnBackClick ->
                viewModelScope.launch { _events.send(FoodEditEvent.NavigateBack) }
        }
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val food = repository.observeAll().first().firstOrNull { it.id.value == id } ?: return@launch
            _state.update {
                it.copy(
                    name = it.name.ifEmpty { food.name },
                    barcode = it.barcode.ifEmpty { food.barcode.orEmpty() },
                    kcal = food.nutritionPer100g?.kcal?.toString() ?: "",
                    protein = food.nutritionPer100g?.proteinG?.toString() ?: "",
                    fat = food.nutritionPer100g?.fatG?.toString() ?: "",
                    carbs = food.nutritionPer100g?.carbsG?.toString() ?: "",
                    fiber = food.nutritionPer100g?.fiberG?.toString() ?: "",
                    portionGrams = food.servingUnit?.gramsEquivalent?.toString() ?: "",
                    isFavorite = food.isFavorite,
                )
            }
        }
    }

    private fun save() {
        val name = _state.value.name.trim()
        if (name.isBlank()) {
            _state.update { it.copy(nameError = UiText.StringResource(R.string.food_edit_error_name_blank)) }
            return
        }
        val nutrition = parseNutrition()
        if (_state.value.numberError != null) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val id = foodId ?: UUID.randomUUID().toString()
            val trimmedBarcode = _state.value.barcode.trim()
            val barcode = trimmedBarcode.ifBlank { null }
            val servingUnit = parseServingUnit()
            val existingFood =
                if (foodId != null) {
                    (repository.getById(FoodId(foodId)) as? Result.Success)?.data
                } else {
                    null
                }
            val isFavorite = _state.value.isFavorite
            val item =
                if (existingFood != null) {
                    existingFood.copy(
                        name = name,
                        barcode = barcode,
                        nutritionPer100g = nutrition,
                        servingUnit = servingUnit,
                        isFavorite = isFavorite,
                    )
                } else {
                    FoodItem(
                        id = FoodId(id),
                        name = name,
                        barcode = barcode,
                        nutritionPer100g = nutrition,
                        servingUnit = servingUnit,
                        isFavorite = isFavorite,
                        isUserCreated = true,
                        cachedStatus = CachedFoodItemStatus.Original,
                        originalSnapshot = null,
                    )
                }
            repository
                .saveUserEdit(item)
                .onSuccess {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(FoodEditEvent.Saved(id))
                }.onFailure {
                    _state.update { it.copy(isSaving = false) }
                }
        }
    }

    private fun parseNutrition(): NutritionValues? {
        val allBlank =
            _state.value.kcal.isBlank() &&
                _state.value.protein.isBlank() &&
                _state.value.fat.isBlank() &&
                _state.value.carbs.isBlank() &&
                _state.value.fiber.isBlank()
        if (allBlank) return null

        val anyInvalid =
            listOf(
                _state.value.kcal,
                _state.value.protein,
                _state.value.fat,
                _state.value.carbs,
                _state.value.fiber,
            ).any { it.isNotBlank() && !it.isValidNonNegativeNumber() }
        if (anyInvalid) {
            _state.update {
                it.copy(numberError = UiText.StringResource(R.string.food_edit_error_invalid_number))
            }
            return null
        }

        return NutritionValues(
            kcal = _state.value.kcal.parseOrZero(),
            proteinG = _state.value.protein.parseOrZero(),
            fatG = _state.value.fat.parseOrZero(),
            carbsG = _state.value.carbs.parseOrZero(),
            fiberG = _state.value.fiber.parseOrZero(),
        )
    }

    private fun parseServingUnit(): ServingUnit? {
        val gramsText = _state.value.portionGrams.trim()
        if (gramsText.isBlank()) return null
        val grams = gramsText.replace(',', '.').toDoubleOrNull() ?: return null
        return if (grams > 0.0) {
            val label = "1 Portion (${
                if (grams % 1.0 == 0.0) grams.toInt().toString() else grams.toString()
            } g)"
            ServingUnit(label = label, gramsEquivalent = grams)
        } else {
            null
        }
    }

    private fun String.isValidNonNegativeNumber(): Boolean =
        try {
            val value = trim().replace(',', '.').ifBlank { "0" }.toDouble()
            value >= 0.0
        } catch (_: NumberFormatException) {
            false
        }

    private fun String.parseOrZero(): Double =
        trim()
            .replace(',', '.')
            .ifBlank { "0" }
            .toDouble()

    private companion object {
        const val KEY_NAME = "food_edit_name"
        const val KEY_BARCODE = "food_edit_barcode"
    }
}
