package com.example.ezmakro.core.barcode.di

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.example.ezmakro.core.barcode.MlKitBarcodeScanner
import com.example.ezmakro.core.domain.barcode.BarcodeScanner
import org.koin.dsl.module

val barcodeModule =
    module {
        factory<BarcodeScanner> { (context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView?) ->
            MlKitBarcodeScanner(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
            )
        }
    }
