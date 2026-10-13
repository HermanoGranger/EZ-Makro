package com.example.ezmakro.feature.food.presentation.detail

import app.cash.turbine.test
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.feature.food.presentation.fake.FakeFoodRepository
import com.example.ezmakro.feature.food.presentation.fake.FakeRecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeFoodRepository
    private lateinit var recipeRepository: FakeRecipeRepository
    private lateinit var viewModel: FoodDetailViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeFoodRepository()
        recipeRepository = FakeRecipeRepository()
        viewModel =
            FoodDetailViewModel(
                foodId = "1",
                repository = repository,
                recipeRepository = recipeRepository,
            )
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
            val rm =
                FoodDetailViewModel(
                    foodId = "1",
                    repository = repository,
                    recipeRepository = recipeRepository,
                )
            dispatcher.scheduler.runCurrent()

            val food = rm.state.value.food
            assertEquals("Alpro", food?.name)
            assertEquals(false, rm.state.value.isLoading)
            assertEquals(false, rm.state.value.isPersisted)
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

    @Test
    fun resetToOriginal_failureEmitsShowMessage() =
        runTest {
            repository.shouldFailResetToOriginal = true

            viewModel.events.test {
                viewModel.onAction(FoodDetailAction.OnResetToOriginalClick)
                dispatcher.scheduler.advanceUntilIdle()

                assertTrue(awaitItem() is FoodDetailEvent.ShowMessage)
            }
        }

    @Test
    fun refetch_failureEmitsShowMessage() =
        runTest {
            repository.shouldFailRefetch = true

            viewModel.events.test {
                viewModel.onAction(FoodDetailAction.OnRefetchConfirm)
                dispatcher.scheduler.advanceUntilIdle()

                assertTrue(awaitItem() is FoodDetailEvent.ShowMessage)
            }
        }

    @Test
    fun persistedFood_isMarkedPersisted() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", name = "Apple")))
            dispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.state.value.isPersisted)
        }

    @Test
    fun deleteClick_loadsRecipeUsageAndShowsDialog() =
        runTest {
            recipeRepository.namesUsingFood = listOf("Pfannkuchen", "Omelett")
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", name = "Apple")))

            viewModel.onAction(FoodDetailAction.OnDeleteClick)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(true, viewModel.state.value.showDeleteConfirm)
            assertEquals(listOf("Pfannkuchen", "Omelett"), viewModel.state.value.usedInRecipes)
            assertEquals(FoodId("1"), recipeRepository.lastQueriedFood)
        }

    @Test
    fun deleteDismiss_hidesDialog() =
        runTest {
            viewModel.onAction(FoodDetailAction.OnDeleteClick)
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(FoodDetailAction.OnDeleteDismiss)

            assertEquals(false, viewModel.state.value.showDeleteConfirm)
        }

    @Test
    fun deleteConfirm_callsRepositoryAndEmitsNavigateBack() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", name = "Apple")))

            viewModel.events.test {
                viewModel.onAction(FoodDetailAction.OnDeleteClick)
                dispatcher.scheduler.advanceUntilIdle()

                viewModel.onAction(FoodDetailAction.OnDeleteConfirm)
                dispatcher.scheduler.advanceUntilIdle()

                assertEquals(FoodDetailEvent.NavigateBack, awaitItem())
            }
            assertEquals(FoodId("1"), repository.lastDeleted)
            assertEquals(false, viewModel.state.value.showDeleteConfirm)
        }

    @Test
    fun deleteConfirm_failureEmitsShowMessage() =
        runTest {
            repository.shouldFailDelete = true

            viewModel.events.test {
                viewModel.onAction(FoodDetailAction.OnDeleteClick)
                dispatcher.scheduler.advanceUntilIdle()

                viewModel.onAction(FoodDetailAction.OnDeleteConfirm)
                dispatcher.scheduler.advanceUntilIdle()

                assertTrue(awaitItem() is FoodDetailEvent.ShowMessage)
            }
            assertEquals(false, viewModel.state.value.isDeleting)
        }
}
