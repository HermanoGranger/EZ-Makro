package com.example.ezmakro.feature.food.presentation.scan

sealed interface BarcodeScanAction {
    data class OnPermissionResult(
        val granted: Boolean,
    ) : BarcodeScanAction

    data class OnBarcodeDetected(
        val barcode: String,
    ) : BarcodeScanAction

    data object OnBackClick : BarcodeScanAction
}
