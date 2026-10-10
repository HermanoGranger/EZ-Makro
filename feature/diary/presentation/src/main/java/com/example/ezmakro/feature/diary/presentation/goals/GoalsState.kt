package com.example.ezmakro.feature.diary.presentation.goals

import com.example.ezmakro.core.presentation.uitext.UiText

/**
 * Form state for the daily goals screen. Numeric fields are kept as raw text so
 * the user can type freely; validation happens on save (mirrors FoodEditState).
 */
data class GoalsState(
    val kcal: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val fiber: String = "",
    /**
     * True once the user typed in a field. From then on goal emissions do not
     * overwrite the draft any more.
     */
    val isDirty: Boolean = false,
    val numberError: UiText? = null,
    /** Persistence error — set when [GoalsAction.OnSaveClick]/[GoalsAction.OnResetToDefaultClick] fails. */
    val saveError: UiText? = null,
    val isSaving: Boolean = false,
)
