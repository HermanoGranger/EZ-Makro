package com.example.ezmakro.core.domain.barcode

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic barcode scanning abstraction. The concrete implementation
 * (CameraX + ML Kit) lives in :core:barcode; the domain stays free of Android
 * and camera dependencies.
 */
interface BarcodeScanner {
    /**
     * Emits detected barcode raw values as they are recognized. Collecting
     * starts the scan; cancelling the collection stops it and releases camera
     * resources.
     */
    fun scan(): Flow<String>
}
