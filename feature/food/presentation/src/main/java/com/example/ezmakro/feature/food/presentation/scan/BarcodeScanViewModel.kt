package com.example.ezmakro.feature.food.presentation.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.presentation.uitext.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BarcodeScanViewModel(
    private val repository: FoodRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BarcodeScanState())
    val state = _state.asStateFlow()

    private val _events = Channel<BarcodeScanEvent>()
    val events = _events.receiveAsFlow()

    private var handledBarcode = false

    fun onAction(action: BarcodeScanAction) {
        when (action) {
            is BarcodeScanAction.OnPermissionResult ->
                _state.update { it.copy(hasCameraPermission = action.granted) }

            is BarcodeScanAction.OnBarcodeDetected -> lookup(action.barcode)

            BarcodeScanAction.OnBackClick ->
                viewModelScope.launch { _events.send(BarcodeScanEvent.NavigateBack) }
        }
    }

    private fun lookup(barcode: String) {
        if (handledBarcode || _state.value.isLookingUp) return
        handledBarcode = true
        viewModelScope.launch {
            _state.update { it.copy(isLookingUp = true, error = null) }
            when (val result = repository.lookupByBarcode(barcode)) {
                is Result.Success -> {
                    val food = result.data
                    if (food != null) {
                        _events.send(BarcodeScanEvent.NavigateToDetail(food.id.value))
                    } else {
                        _events.send(BarcodeScanEvent.NavigateToCreate(barcode))
                    }
                }

                is Result.Error -> {
                    handledBarcode = false
                    _state.update { it.copy(isLookingUp = false, error = result.error.toUiText()) }
                }
            }
        }
    }
}
