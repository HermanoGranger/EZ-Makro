package com.example.ezmakro.core.barcode

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.ezmakro.core.domain.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * CameraX + ML Kit implementation of [BarcodeScanner]. Collecting [scan] binds
 * an [ImageAnalysis] use case to [lifecycleOwner] and emits detected raw barcode
 * values; cancelling the collection unbinds the camera and releases resources.
 *
 * Camera permission handling is the caller's responsibility (feature UI, Phase 5).
 */
class MlKitBarcodeScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) : BarcodeScanner {
    private val scanner = BarcodeScanning.getClient()

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun scan(): Flow<String> =
        callbackFlow {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val executor = ContextCompat.getMainExecutor(context)

            val analysis =
                ImageAnalysis
                    .Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

            analysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val input =
                    InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                scanner
                    .process(input)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstNotNullOfOrNull { it.rawValue }?.let { trySend(it) }
                    }.addOnCompleteListener { imageProxy.close() }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                analysis,
            )

            awaitClose {
                analysis.clearAnalyzer()
                cameraProvider.unbindAll()
            }
        }
}
