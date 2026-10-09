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
import com.example.ezmakro.core.presentation.uitext.toUiText
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
@Suppress("TooManyFunctions")
class FoodEditViewModel(
    private val foodId: String?,
    private val prefillBarcode: String?,
    private val repository: FoodRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(createInitialState())

    private fun createInitialState(): FoodEditState =
        FoodEditState(
            isEditingExisting = foodId != null,
            barcode = savedStateHandle[KEY_BARCODE] ?: prefillBarcode.orEmpty(),
            name = savedStateHandle[KEY_NAME] ?: "",
            kcal = savedStateHandle[KEY_KCAL] ?: "",
            protein = savedStateHandle[KEY_PROTEIN] ?: "",
            fat = savedStateHandle[KEY_FAT] ?: "",
            carbs = savedStateHandle[KEY_CARBS] ?: "",
            fiber = savedStateHandle[KEY_FIBER] ?: "",
            portionGrams = savedStateHandle[KEY_PORTION_GRAMS] ?: "",
            isFavorite = savedStateHandle[KEY_FAVORITE] ?: false,
            isDirty = isDraftRestored(),
            saveError = getRestoredSaveError(),
        )

    val state = _state.asStateFlow()

    private val _events = Channel<FoodEditEvent>()
    val events = _events.receiveAsFlow()

    init {
        if (foodId != null) observeExisting(FoodId(foodId))
    }

    private fun isDraftRestored(): Boolean =
        savedStateHandle.contains(KEY_DIRTY) ||
            savedStateHandle.contains(KEY_NAME) ||
            savedStateHandle.contains(KEY_BARCODE)

    private fun getRestoredSaveError(): UiText? =
        savedStateHandle.get<Int>(KEY_SAVE_ERROR)?.let { UiText.StringResource(it) }

    fun onAction(action: FoodEditAction) {
        when (action) {
            is FoodEditAction.OnNameChange -> onUserInput { copy(name = action.value, nameError = null) }
            is FoodEditAction.OnBarcodeChange -> onUserInput { copy(barcode = action.value) }
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
        val updated = _state.value.copy(isDirty = true, saveError = null).update()
        _state.value = updated
        persistDraft(updated)
    }

    /**
     * Persists every form field, not just the one being edited, so a partially
     * edited existing food still restores its untouched (seeded) values after
     * process death. [FoodEditState.isDirty] then keeps the whole draft from being
     * overwritten by the observed food.
     */
    private fun persistDraft(state: FoodEditState) {
        val draft = state.toDraft()
        savedStateHandle[KEY_DIRTY] = true
        savedStateHandle[KEY_NAME] = draft.name
        savedStateHandle[KEY_BARCODE] = draft.barcode
        savedStateHandle[KEY_KCAL] = draft.kcal
        savedStateHandle[KEY_PROTEIN] = draft.protein
        savedStateHandle[KEY_FAT] = draft.fat
        savedStateHandle[KEY_CARBS] = draft.carbs
        savedStateHandle[KEY_FIBER] = draft.fiber
        savedStateHandle[KEY_PORTION_GRAMS] = draft.portionGrams
        savedStateHandle[KEY_FAVORITE] = draft.isFavorite
        savedStateHandle.remove<Int>(KEY_SAVE_ERROR)
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
        savedStateHandle.remove<Int>(KEY_SAVE_ERROR)
        _state.update { it.copy(saveError = null) }
        val name = _state.value.name.trim()
        if (name.isBlank()) {
            _state.update { it.copy(nameError = UiText.StringResource(R.string.food_edit_error_name_blank)) }
            return
        }
        val nutrition = parseNutrition()
        if (_state.value.numberError != null) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            performSave(name, nutrition)
        }
    }

    private suspend fun performSave(
        name: String,
        nutrition: NutritionValues?,
    ) {
        val trimmedBarcode = _state.value.barcode.trim()
        val barcode = trimmedBarcode.ifBlank { null }
        val servingUnit = NutritionParser.parseServingUnit(_state.value.portionGrams)
        val item = buildFoodToSave(name, barcode, nutrition, servingUnit)
        val savedId = item.id.value
        repository
            .saveUserEdit(item)
            .onSuccess {
                savedStateHandle.remove<Int>(KEY_SAVE_ERROR)
                _state.update { it.copy(isSaving = false, saveError = null) }
                _events.send(FoodEditEvent.Saved(savedId))
            }.onFailure { error ->
                val saveError = error.toUiText()
                if (saveError is UiText.StringResource) {
                    savedStateHandle[KEY_SAVE_ERROR] = saveError.id
                }
                _state.update { it.copy(isSaving = false, saveError = saveError) }
            }
    }

    private suspend fun buildFoodToSave(
        name: String,
        barcode: String?,
        nutrition: NutritionValues?,
        servingUnit: ServingUnit?,
    ): FoodItem {
        val draftFavorite = _state.value.isFavorite
        val existingFood =
            if (foodId != null) {
                (repository.getById(FoodId(foodId)) as? Result.Success)?.data
            } else {
                null
            }
        return when {
            existingFood != null -> {
                existingFood.copy(
                    name = name,
                    barcode = barcode,
                    nutritionPer100g = nutrition,
                    servingUnit = servingUnit,
                )
            }
            foodId != null -> {
                val sessionItem = repository.getSessionItem(FoodId(foodId))
                if (sessionItem != null) {
                    sessionItem.copy(
                        name = name,
                        barcode = barcode,
                        nutritionPer100g = nutrition,
                        servingUnit = servingUnit,
                    )
                } else {
                    newFood(foodId, name, barcode, nutrition, servingUnit, draftFavorite)
                }
            }
            else -> {
                val id = UUID.randomUUID().toString()
                newFood(id, name, barcode, nutrition, servingUnit, draftFavorite)
            }
        }
    }

    private fun parseNutrition(): NutritionValues? =
        NutritionParser.parseNutrition(_state.value) { error ->
            _state.update { it.copy(numberError = error) }
        }

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

    private data class DraftState(
        val name: String,
        val barcode: String,
        val kcal: String,
        val protein: String,
        val fat: String,
        val carbs: String,
        val fiber: String,
        val portionGrams: String,
        val isFavorite: Boolean,
    )

    private fun FoodEditState.toDraft() =
        DraftState(
            name = name,
            barcode = barcode,
            kcal = kcal,
            protein = protein,
            fat = fat,
            carbs = carbs,
            fiber = fiber,
            portionGrams = portionGrams,
            isFavorite = isFavorite,
        )

    private companion object {
        const val KEY_NAME = "food_edit_name"
        const val KEY_BARCODE = "food_edit_barcode"
        const val KEY_DIRTY = "food_edit_dirty"
        const val KEY_KCAL = "food_edit_kcal"
        const val KEY_PROTEIN = "food_edit_protein"
        const val KEY_FAT = "food_edit_fat"
        const val KEY_CARBS = "food_edit_carbs"
        const val KEY_FIBER = "food_edit_fiber"
        const val KEY_PORTION_GRAMS = "food_edit_portion_grams"
        const val KEY_FAVORITE = "food_edit_favorite"
        const val KEY_SAVE_ERROR = "food_edit_save_error"
    }
}
