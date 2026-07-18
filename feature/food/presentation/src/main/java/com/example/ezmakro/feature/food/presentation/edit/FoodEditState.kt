package com.example.ezmakro.feature.food.presentation.edit

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
    val nameError: UiText? = null,
    val numberError: UiText? = null,
    val isSaving: Boolean = false,
)
