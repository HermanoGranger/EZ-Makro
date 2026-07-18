package com.example.ezmakro.feature.food.presentation.search

import app.cash.turbine.test
import com.example.ezmakro.core.domain.model.FoodId
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
class FoodSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeFoodRepository
    private lateinit var viewModel: FoodSearchViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeFoodRepository()
        viewModel = FoodSearchViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun favorites_areLoadedIntoState() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", name = "Apple", isFavorite = true)))
            dispatcher.scheduler.runCurrent()

            val favorites = viewModel.state.value.favorites
            assertEquals("Apple", favorites.first().name)
            assertEquals(true, favorites.first().isFavorite)
        }

    @Test
    fun queryChange_updatesQuery() =
        runTest {
            viewModel.onAction(FoodSearchAction.OnQueryChange("app"))

            assertEquals("app", viewModel.state.value.query)
        }

    @Test
    fun foodClick_emitsNavigateToDetailEvent() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(FoodSearchAction.OnFoodClick("42"))
                assertEquals(FoodSearchEvent.NavigateToDetail("42"), awaitItem())
            }
        }

    @Test
    fun scanClick_emitsNavigateToScannerEvent() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(FoodSearchAction.OnScanBarcodeClick)
                assertEquals(FoodSearchEvent.NavigateToScanner, awaitItem())
            }
        }

    @Test
    fun createOwnClick_emitsNavigateToCreateEvent() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(FoodSearchAction.OnCreateOwnClick)
                assertEquals(FoodSearchEvent.NavigateToCreate(barcode = null), awaitItem())
            }
        }

    @Test
    fun toggleFavorite_callsRepository() =
        runTest {
            viewModel.onAction(FoodSearchAction.OnToggleFavorite("7"))
            dispatcher.scheduler.runCurrent()

            assertEquals(FoodId("7"), repository.lastToggledFavorite)
        }
}
