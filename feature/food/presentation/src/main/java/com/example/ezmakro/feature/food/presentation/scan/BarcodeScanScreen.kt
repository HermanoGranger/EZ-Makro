package com.example.ezmakro.feature.food.presentation.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.barcode.BarcodeScanner
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.food.presentation.R
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun BarcodeScanRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (String) -> Unit,
    viewModel: BarcodeScanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onAction(BarcodeScanAction.OnPermissionResult(granted))
        }

    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onAction(BarcodeScanAction.OnPermissionResult(true))
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            BarcodeScanEvent.NavigateBack -> onNavigateBack()
            is BarcodeScanEvent.NavigateToDetail -> onNavigateToDetail(event.foodId)
            is BarcodeScanEvent.NavigateToCreate -> onNavigateToCreate(event.barcode)
        }
    }

    if (state.hasCameraPermission) {
        val scanner: BarcodeScanner = koinInject { parametersOf(context, lifecycleOwner) }
        LaunchedEffect(scanner) {
            scanner.scan().collect { barcode ->
                viewModel.onAction(BarcodeScanAction.OnBarcodeDetected(barcode))
            }
        }
    }

    BarcodeScanScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    state: BarcodeScanState,
    onAction: (BarcodeScanAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.food_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(BarcodeScanAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !state.hasCameraPermission ->
                    Text(stringResource(R.string.food_scan_permission_required))

                state.isLookingUp -> CircularProgressIndicator()

                state.error != null -> Text(state.error.asString())

                else -> Text(stringResource(R.string.food_scan_title))
            }
        }
    }
}
