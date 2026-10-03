package com.example.ezmakro.feature.food.presentation.edit

import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.presentation.uitext.UiText

/**
 * Form state for creating or editing a food. All numeric fields are kept as raw
 * text so the user can type freely; validation happens on save.
 */
data class FoodEditState(
    val isEditingExisting: Boolean = false,
    val name: String = "",
    val barcode: String = "",
    val kcal: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val fiber: String = "",
    val portionGrams: String = "",
    val isFavorite: Boolean = false,
    /**
     * True once the user edited a form field. From then on incoming food
     * emissions only refresh the server-owned fields ([isFavorite],
     * [cachedStatus]) so a live update never overwrites the draft.
     */
    val isDirty: Boolean = false,
    /** ADR-004 status of the edited food, mirrored from the observed flow. */
    val cachedStatus: CachedFoodItemStatus? = null,
    val nameError: UiText? = null,
    val numberError: UiText? = null,
    val isSaving: Boolean = false,
)
