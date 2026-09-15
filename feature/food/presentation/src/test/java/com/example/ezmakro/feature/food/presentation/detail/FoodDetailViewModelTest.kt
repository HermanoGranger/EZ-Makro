package com.example.ezmakro.feature.food.presentation.detail

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
class FoodDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeFoodRepository
    private lateinit var viewModel: FoodDetailViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeFoodRepository()
        viewModel = FoodDetailViewModel(foodId = "1", repository = repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun observedFood_isLoadedIntoState() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", name = "Apple")))
            dispatcher.scheduler.runCurrent()

            val food = viewModel.state.value.food
            assertEquals("Apple", food?.name)
            assertEquals(false, viewModel.state.value.isLoading)
        }

    @Test
    fun sessionItem_fallbackRendersOnlineFood() =
        runTest {
            repository.rememberSessionItem(FakeFoodRepository.food(id = "1", name = "Alpro"))
            val rm = FoodDetailViewModel(foodId = "1", repository = repository)
            dispatcher.scheduler.runCurrent()

            val food = rm.state.value.food
            assertEquals("Alpro", food?.name)
            assertEquals(false, rm.state.value.isLoading)
        }

    @Test
    fun backClick_emitsNavigateBackEvent() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(FoodDetailAction.OnBackClick)
                assertEquals(FoodDetailEvent.NavigateBack, awaitItem())
            }
        }

    @Test
    fun editClick_emitsNavigateToEditEvent() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(FoodDetailAction.OnEditClick)
                assertEquals(FoodDetailEvent.NavigateToEdit("1"), awaitItem())
            }
        }

    @Test
    fun toggleFavorite_callsRepository() =
        runTest {
            viewModel.onAction(FoodDetailAction.OnToggleFavorite)
            dispatcher.scheduler.runCurrent()

            assertEquals(FoodId("1"), repository.lastToggledFavorite)
        }

    @Test
    fun refetchConfirm_setsAndResetsFlag() =
        runTest {
            viewModel.onAction(FoodDetailAction.OnRefetchClick)
            assertEquals(true, viewModel.state.value.showRefetchConfirm)

            viewModel.onAction(FoodDetailAction.OnRefetchConfirm)
            dispatcher.scheduler.runCurrent()
            assertEquals(false, viewModel.state.value.showRefetchConfirm)
            assertEquals(false, viewModel.state.value.isRefetching)
        }
}
