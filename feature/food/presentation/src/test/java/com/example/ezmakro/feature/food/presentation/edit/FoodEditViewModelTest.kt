package com.example.ezmakro.feature.food.presentation.edit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.feature.food.presentation.fake.FakeFoodRepository
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
class FoodEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeFoodRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeFoodRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun prefillBarcode_seedsBarcodeField() {
        val viewModel =
            FoodEditViewModel(
                foodId = null,
                prefillBarcode = "12345",
                repository = repository,
                savedStateHandle = SavedStateHandle(),
            )

        assertEquals("12345", viewModel.state.value.barcode)
        assertEquals(false, viewModel.state.value.isEditingExisting)
    }

    @Test
    fun blankName_showsNameError_andDoesNotSave() =
        runTest {
            val viewModel = newViewModel(foodId = null)
            viewModel.onAction(FoodEditAction.OnNameChange("   "))
            viewModel.onAction(FoodEditAction.OnKcalChange("50"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            assertTrue(viewModel.state.value.nameError != null)
            assertTrue(repository.savedEdits.isEmpty())
        }

    @Test
    fun invalidNumber_showsNumberError_andDoesNotSave() =
        runTest {
            val viewModel = newViewModel(foodId = null)
            viewModel.onAction(FoodEditAction.OnNameChange("Banana"))
            viewModel.onAction(FoodEditAction.OnKcalChange("abc"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            assertTrue(viewModel.state.value.numberError != null)
            assertTrue(repository.savedEdits.isEmpty())
        }

    @Test
    fun validSave_emitsSavedEvent_andPersistsUserCreatedFood() =
        runTest {
            val viewModel = newViewModel(foodId = null)
            viewModel.onAction(FoodEditAction.OnNameChange("Banana"))
            viewModel.onAction(FoodEditAction.OnKcalChange("89"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            viewModel.events.test {
                val event = awaitItem()
                assertTrue(event is FoodEditEvent.Saved)
            }

            val saved = repository.savedEdits.single()
            assertEquals("Banana", saved.name)
            assertEquals(89.0, saved.nutritionPer100g!!.kcal, 0.0)
            assertEquals(true, saved.isUserCreated)
            assertEquals(CachedFoodItemStatus.Original, saved.cachedStatus)
        }

    @Test
    fun saveWithoutPer100g_savesWithNullNutrition() =
        runTest {
            val viewModel = newViewModel(foodId = null)
            viewModel.onAction(FoodEditAction.OnNameChange("Water"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            viewModel.events.test {
                val event = awaitItem()
                assertTrue(event is FoodEditEvent.Saved)
            }

            val saved = repository.savedEdits.single()
            assertEquals("Water", saved.name)
            assertEquals(null, saved.nutritionPer100g)
        }

    @Test
    fun editingExisting_loadsFoodIntoFields() =
        runTest {
            repository.emitFoods(
                listOf(FakeFoodRepository.food(id = "1", name = "Apple", kcal = 52.0, isFavorite = true)),
            )
            val viewModel = newViewModel(foodId = "1")
            dispatcher.scheduler.runCurrent()

            assertEquals("Apple", viewModel.state.value.name)
            assertEquals("52.0", viewModel.state.value.kcal)
            assertEquals(true, viewModel.state.value.isEditingExisting)
        }

    @Test
    fun backClick_emitsNavigateBackEvent() =
        runTest {
            val viewModel = newViewModel(foodId = null)
            viewModel.events.test {
                viewModel.onAction(FoodEditAction.OnBackClick)
                assertEquals(FoodEditEvent.NavigateBack, awaitItem())
            }
        }

    private fun newViewModel(foodId: String?): FoodEditViewModel =
        FoodEditViewModel(
            foodId = foodId,
            prefillBarcode = null,
            repository = repository,
            savedStateHandle = SavedStateHandle(),
        )
}
