package com.example.ezmakro.feature.food.presentation.detail

import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.feature.food.presentation.model.FoodUi

data class FoodDetailState(
    val food: FoodUi? = null,
    val isLoading: Boolean = true,
    val isRefetching: Boolean = false,
    val showRefetchConfirm: Boolean = false,
    val error: UiText? = null,
)
