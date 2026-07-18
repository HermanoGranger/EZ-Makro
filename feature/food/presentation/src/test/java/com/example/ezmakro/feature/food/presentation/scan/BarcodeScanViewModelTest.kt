package com.example.ezmakro.feature.food.presentation.scan

import app.cash.turbine.test
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.feature.food.presentation.fake.FakeFoodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BarcodeScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeFoodRepository
    private lateinit var viewModel: BarcodeScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeFoodRepository()
        viewModel = BarcodeScanViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun permissionGranted_updatesState() {
        viewModel.onAction(BarcodeScanAction.OnPermissionResult(granted = true))

        assertEquals(true, viewModel.state.value.hasCameraPermission)
    }

    @Test
    fun barcodeWithNoHit_emitsNavigateToCreate() =
        runTest {
            repository.lookupResult = Result.Success(null)
            viewModel.events.test {
                viewModel.onAction(BarcodeScanAction.OnBarcodeDetected("555"))
                dispatcher.scheduler.runCurrent()
                assertEquals(BarcodeScanEvent.NavigateToCreate("555"), awaitItem())
            }
        }

    @Test
    fun barcodeWithHit_emitsNavigateToDetail() =
        runTest {
            repository.lookupResult = Result.Success(FakeFoodRepository.food(id = "9"))
            viewModel.events.test {
                viewModel.onAction(BarcodeScanAction.OnBarcodeDetected("555"))
                dispatcher.scheduler.runCurrent()
                assertEquals(BarcodeScanEvent.NavigateToDetail("9"), awaitItem())
            }
        }

    @Test
    fun backClick_emitsNavigateBackEvent() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(BarcodeScanAction.OnBackClick)
                assertEquals(BarcodeScanEvent.NavigateBack, awaitItem())
            }
        }
}
