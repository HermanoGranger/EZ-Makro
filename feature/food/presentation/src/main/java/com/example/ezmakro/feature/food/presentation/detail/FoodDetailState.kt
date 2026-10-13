package com.example.ezmakro.feature.food.presentation.detail

import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.feature.food.presentation.model.FoodUi

data class FoodDetailState(
    val food: FoodUi? = null,
    val isLoading: Boolean = true,
    val isRefetching: Boolean = false,
    val showRefetchConfirm: Boolean = false,
    /** True when the food is persisted in the database (not just session-only). */
    val isPersisted: Boolean = true,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    /** Names of recipes that use this food; shown as a warning before deleting. */
    val usedInRecipes: List<String> = emptyList(),
    val error: UiText? = null,
)
