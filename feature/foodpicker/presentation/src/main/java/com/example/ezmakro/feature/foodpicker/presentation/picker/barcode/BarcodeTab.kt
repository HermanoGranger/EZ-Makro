package com.example.ezmakro.feature.foodpicker.presentation.picker.barcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ezmakro.core.domain.barcode.BarcodeScanner
import com.example.ezmakro.feature.foodpicker.presentation.R
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerAction
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerState
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun BarcodeTab(
    state: FoodPickerState,
    onAction: (FoodPickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onAction(FoodPickerAction.OnPermissionResult(granted))
        }

    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            onAction(FoodPickerAction.OnPermissionResult(true))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewView =
        remember {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

    var isPreviewReady by remember { mutableStateOf(false) }

    BarcodeTabBody(
        state = state,
        previewView = previewView,
        isPreviewReady = isPreviewReady,
        onIsPreviewReady = { isPreviewReady = true },
        modifier = modifier,
    )

    if (isPreviewReady && state.isCameraPermissionGranted) {
        val scanner: BarcodeScanner = koinInject { parametersOf(context, lifecycleOwner, previewView) }
        LaunchedEffect(scanner) {
            scanner.scan().collect { barcode ->
                onAction(FoodPickerAction.OnBarcodeDetected(barcode))
            }
        }
    }
}

@Composable
private fun BarcodeTabBody(
    state: FoodPickerState,
    previewView: PreviewView,
    isPreviewReady: Boolean,
    onIsPreviewReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isCameraPermissionGranted) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .onGloballyPositioned { coordinates ->
                            if (!isPreviewReady && coordinates.size.height > 0) {
                                onIsPreviewReady()
                            }
                        },
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (state.isLookingUpBarcode) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (!state.isCameraPermissionGranted) {
            Text(
                text = stringResource(R.string.foodpicker_barcode_permission),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        state.barcodeError?.let { error ->
            Text(
                text = error.asString(),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
