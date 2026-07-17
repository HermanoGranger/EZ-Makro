package com.example.ezmakro.core.barcode.di

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.example.ezmakro.core.barcode.MlKitBarcodeScanner
import com.example.ezmakro.core.domain.barcode.BarcodeScanner
import org.koin.dsl.module

/**
 * A [BarcodeScanner] is bound to a [Context] and a [LifecycleOwner], both of
 * which are runtime values owned by the scanning screen. It is therefore
 * provided as a [factory] taking those parameters rather than a singleton.
 */
val barcodeModule =
    module {
        factory<BarcodeScanner> { (context: Context, lifecycleOwner: LifecycleOwner) ->
            MlKitBarcodeScanner(context = context, lifecycleOwner = lifecycleOwner)
        }
    }
