package com.example.ezmakro.feature.food.presentation.scan

import com.example.ezmakro.core.presentation.uitext.UiText

data class BarcodeScanState(
    val hasCameraPermission: Boolean = false,
    val isLookingUp: Boolean = false,
    val error: UiText? = null,
)
