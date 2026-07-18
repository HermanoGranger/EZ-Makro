package com.example.ezmakro.feature.food.presentation.scan

sealed interface BarcodeScanEvent {
    data object NavigateBack : BarcodeScanEvent

    data class NavigateToDetail(
        val foodId: String,
    ) : BarcodeScanEvent

    /** Barcode had no hit locally or on OFF: open create with it prefilled. */
    data class NavigateToCreate(
        val barcode: String,
    ) : BarcodeScanEvent
}
