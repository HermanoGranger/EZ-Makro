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
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.food.presentation.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Draft ownership: the form fields are seeded from the first emission and are never
 * overwritten afterwards (see [FoodEditState.isDirty]). The server-owned
 * [FoodEditState.isFavorite] and [FoodEditState.cachedStatus] are refreshed on every
 * emission.
 */
private fun FoodEditState.mergedWith(food: FoodItem): FoodEditState =
    copy(
        name = if (isDirty) name else food.name,
        barcode = if (isDirty) barcode else food.barcode.orEmpty(),
        kcal = if (isDirty) kcal else food.nutritionPer100g?.kcal?.formatValue() ?: "",
        protein = if (isDirty) protein else food.nutritionPer100g?.proteinG?.formatValue() ?: "",
        fat = if (isDirty) fat else food.nutritionPer100g?.fatG?.formatValue() ?: "",
        carbs = if (isDirty) carbs else food.nutritionPer100g?.carbsG?.formatValue() ?: "",
        fiber = if (isDirty) fiber else food.nutritionPer100g?.fiberG?.formatValue() ?: "",
        portionGrams = if (isDirty) portionGrams else food.servingUnit?.gramsEquivalent?.formatValue() ?: "",
        isFavorite = food.isFavorite,
        cachedStatus = food.cachedStatus,
    )

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
                // A restored draft (process death) is user input and must survive the
                // first emission, so it claims ownership of the form fields.
                isDirty = hasRestoredDraft,
            ),
        )
    val state = _state.asStateFlow()

    private val _events = Channel<FoodEditEvent>()
    val events = _events.receiveAsFlow()

    init {
        if (foodId != null) observeExisting(FoodId(foodId))
    }

    private val hasRestoredDraft: Boolean
        get() =
            savedStateHandle.contains(KEY_DIRTY) ||
                savedStateHandle.contains(KEY_NAME) ||
                savedStateHandle.contains(KEY_BARCODE)

    fun onAction(action: FoodEditAction) {
        when (action) {
            is FoodEditAction.OnNameChange -> {
                savedStateHandle[KEY_NAME] = action.value
                onUserInput { copy(name = action.value, nameError = null) }
            }

            is FoodEditAction.OnBarcodeChange -> {
                savedStateHandle[KEY_BARCODE] = action.value
                onUserInput { copy(barcode = action.value) }
            }

            is FoodEditAction.OnKcalChange -> onUserInput { copy(kcal = action.value, numberError = null) }
            is FoodEditAction.OnProteinChange -> onUserInput { copy(protein = action.value, numberError = null) }
            is FoodEditAction.OnFatChange -> onUserInput { copy(fat = action.value, numberError = null) }
            is FoodEditAction.OnCarbsChange -> onUserInput { copy(carbs = action.value, numberError = null) }
            is FoodEditAction.OnFiberChange -> onUserInput { copy(fiber = action.value, numberError = null) }
            is FoodEditAction.OnPortionGramsChange -> onUserInput { copy(portionGrams = action.value) }

            FoodEditAction.OnToggleFavorite -> toggleFavorite()

            FoodEditAction.OnSaveClick -> save()
            FoodEditAction.OnBackClick ->
                viewModelScope.launch { _events.send(FoodEditEvent.NavigateBack) }
        }
    }

    /**
     * Observe the edited food instead of reading it once, so changes made
     * elsewhere (e.g. a favorite toggled on the detail screen) are mirrored
     * while the editor stays open. A session-only food (not in Room) falls back
     * to the session cache.
     */
    private fun observeExisting(id: FoodId) {
        repository
            .observeById(id)
            .transform { roomFood -> emit(roomFood ?: repository.getSessionItem(id)) }
            .filterNotNull()
            .onEach { food -> _state.update { state -> state.mergedWith(food) } }
            .launchIn(viewModelScope)
    }

    /** Marks the form as user-owned so later emissions no longer seed the fields. */
    private fun onUserInput(update: FoodEditState.() -> FoodEditState) {
        savedStateHandle[KEY_DIRTY] = true
        _state.update { it.copy(isDirty = true).update() }
    }

    /**
     * Persists the favorite immediately instead of only flipping local state: the
     * observed flow is the Single Source of Truth for
     * [FoodEditState.isFavorite]. A new (not yet persisted) food has no id, so
     * only its draft flag is flipped.
     */
    private fun toggleFavorite() {
        val id = foodId
        if (id == null) {
            onUserInput { copy(isFavorite = !isFavorite) }
            return
        }
        viewModelScope.launch { repository.toggleFavorite(FoodId(id)) }
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
            // The favorite flag is owned by [toggleFavorite] and mirrored into the
            // state via the observed flow. Writing the (possibly stale) draft value
            // back here would revert a favorite toggled elsewhere in the meantime.
            val draftFavorite = _state.value.isFavorite
            val item =
                if (existingFood != null) {
                    existingFood.copy(
                        name = name,
                        barcode = barcode,
                        nutritionPer100g = nutrition,
                        servingUnit = servingUnit,
                    )
                } else if (foodId != null) {
                    val sessionItem = repository.getSessionItem(FoodId(foodId))
                    if (sessionItem != null) {
                        sessionItem.copy(
                            name = name,
                            barcode = barcode,
                            nutritionPer100g = nutrition,
                            servingUnit = servingUnit,
                        )
                    } else {
                        newFood(id, name, barcode, nutrition, servingUnit, draftFavorite)
                    }
                } else {
                    newFood(id, name, barcode, nutrition, servingUnit, draftFavorite)
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
            val label = "1 Portion (${grams.formatValue()} g)"
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

    private fun newFood(
        id: String,
        name: String,
        barcode: String?,
        nutrition: NutritionValues?,
        servingUnit: ServingUnit?,
        isFavorite: Boolean,
    ) = FoodItem(
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

    private companion object {
        const val KEY_NAME = "food_edit_name"
        const val KEY_BARCODE = "food_edit_barcode"
        const val KEY_DIRTY = "food_edit_dirty"
    }
}
