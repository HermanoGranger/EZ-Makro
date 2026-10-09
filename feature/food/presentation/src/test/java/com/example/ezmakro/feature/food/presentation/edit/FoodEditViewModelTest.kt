package com.example.ezmakro.feature.food.presentation.edit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.feature.food.presentation.fake.FakeFoodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
            assertEquals("52", viewModel.state.value.kcal)
            assertEquals(true, viewModel.state.value.isEditingExisting)
        }

    @Test
    fun editingUserCreatedFood_preservesIsUserCreated() =
        runTest {
            val userFood =
                FoodItem(
                    id = FoodId("1"),
                    name = "Apple",
                    barcode = null,
                    nutritionPer100g = NutritionValues(52.0, 0.3, 0.2, 14.0, 2.4),
                    servingUnit = null,
                    isUserCreated = true,
                    cachedStatus = CachedFoodItemStatus.Original,
                    originalSnapshot = null,
                )
            repository.emitFoods(listOf(userFood))
            val viewModel = newViewModel(foodId = "1")
            dispatcher.scheduler.runCurrent()

            viewModel.onAction(FoodEditAction.OnNameChange("Apple (Edited)"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            val saved = repository.savedEdits.single()
            assertTrue(saved.isUserCreated)
            assertEquals("Apple (Edited)", saved.name)
        }

    @Test
    fun editingExistingOffFood_preservesOriginalSnapshot() =
        runTest {
            val snapshot =
                FoodItem(
                    id = FoodId("1"),
                    name = "OFF Apple",
                    barcode = null,
                    nutritionPer100g = NutritionValues(52.0, 0.3, 0.2, 14.0, 2.4),
                    servingUnit = null,
                    isUserCreated = false,
                    cachedStatus = CachedFoodItemStatus.Original,
                    originalSnapshot = null,
                )
            val offFood =
                snapshot.copy(
                    originalSnapshot = snapshot,
                )
            repository.emitFoods(listOf(offFood))
            val viewModel = newViewModel(foodId = "1")
            dispatcher.scheduler.runCurrent()

            viewModel.onAction(FoodEditAction.OnNameChange("My Apple"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            val saved = repository.savedEdits.single()
            assertFalse(saved.isUserCreated)
            assertEquals(CachedFoodItemStatus.Original, saved.cachedStatus)
            assertTrue(saved.originalSnapshot != null)
            assertEquals("OFF Apple", saved.originalSnapshot?.name)
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

    @Test
    fun editingSessionItem_loadsFoodIntoFields() =
        runTest {
            val snapshot =
                FoodItem(
                    id = FoodId("off-1"),
                    name = "OFF Milk",
                    barcode = "999",
                    nutritionPer100g = NutritionValues(42.0, 3.3, 1.0, 5.0, 0.0),
                    servingUnit = null,
                    isUserCreated = false,
                    cachedStatus = CachedFoodItemStatus.Original,
                    originalSnapshot = null,
                )
            repository.rememberSessionItem(snapshot)
            val viewModel = newViewModel(foodId = "off-1")
            dispatcher.scheduler.runCurrent()

            assertEquals("OFF Milk", viewModel.state.value.name)
            assertEquals("999", viewModel.state.value.barcode)
            assertEquals("42", viewModel.state.value.kcal)
            assertEquals("3.3", viewModel.state.value.protein)
            assertEquals("1", viewModel.state.value.fat)
            assertEquals("5", viewModel.state.value.carbs)
            assertEquals("0", viewModel.state.value.fiber)
            assertEquals(true, viewModel.state.value.isEditingExisting)
        }

    @Test
    fun editingSessionItem_save_emitsSavedEvent_withCorrectFlags() =
        runTest {
            val snapshot =
                FoodItem(
                    id = FoodId("off-1"),
                    name = "OFF Milk",
                    barcode = "999",
                    nutritionPer100g = NutritionValues(42.0, 3.3, 1.0, 5.0, 0.0),
                    servingUnit = null,
                    isUserCreated = false,
                    cachedStatus = CachedFoodItemStatus.Original,
                    originalSnapshot = null,
                )
            val sessionItem = snapshot.copy(originalSnapshot = snapshot)
            repository.rememberSessionItem(sessionItem)
            val viewModel = newViewModel(foodId = "off-1")
            dispatcher.scheduler.runCurrent()

            viewModel.onAction(FoodEditAction.OnNameChange("My Milk"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            viewModel.events.test {
                val event = awaitItem()
                assertTrue(event is FoodEditEvent.Saved)
                assertEquals("off-1", (event as FoodEditEvent.Saved).foodId)
            }

            val saved = repository.savedEdits.single()
            assertEquals("off-1", saved.id.value)
            assertEquals("My Milk", saved.name)
            assertEquals("999", saved.barcode)
            assertFalse(saved.isUserCreated)
            assertEquals(CachedFoodItemStatus.Original, saved.cachedStatus)
            assertEquals(snapshot, saved.originalSnapshot)
        }

    @Test
    fun favoriteToggledElsewhere_isReflectedInTheForm() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", isFavorite = false)))
            val viewModel = newViewModel(foodId = "1")
            dispatcher.scheduler.runCurrent()
            assertFalse(viewModel.state.value.isFavorite)

            repository.setFavorite("1", favorite = true)
            dispatcher.scheduler.runCurrent()

            assertTrue(viewModel.state.value.isFavorite)
        }

    @Test
    fun cachedStatusChangedElsewhere_isReflectedInTheForm() =
        runTest {
            val apple = FakeFoodRepository.food(id = "1")
            repository.emitFoods(listOf(apple))
            val viewModel = newViewModel(foodId = "1")
            dispatcher.scheduler.runCurrent()
            assertEquals(CachedFoodItemStatus.Original, viewModel.state.value.cachedStatus)

            // ADR-004: a locally edited OFF entry keeps its original snapshot.
            val edited =
                apple.copy(
                    cachedStatus = CachedFoodItemStatus.UserModified,
                    originalSnapshot = apple,
                )
            repository.emitFoods(listOf(edited))
            dispatcher.scheduler.runCurrent()

            assertEquals(CachedFoodItemStatus.UserModified, viewModel.state.value.cachedStatus)
        }

    @Test
    fun typedValues_areNotOverwrittenByALaterEmission() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", name = "Apple", kcal = 52.0)))
            val viewModel = newViewModel(foodId = "1")
            dispatcher.scheduler.runCurrent()
            viewModel.onAction(FoodEditAction.OnNameChange("My Apple"))
            viewModel.onAction(FoodEditAction.OnKcalChange("99"))

            repository.setFavorite("1", favorite = true)
            dispatcher.scheduler.runCurrent()

            assertEquals("My Apple", viewModel.state.value.name)
            assertEquals("99", viewModel.state.value.kcal)
            // server-owned fields still track the flow
            assertTrue(viewModel.state.value.isFavorite)
        }

    @Test
    fun onToggleFavorite_persistsImmediately() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1")))
            val viewModel = newViewModel(foodId = "1")
            dispatcher.scheduler.runCurrent()

            viewModel.onAction(FoodEditAction.OnToggleFavorite)
            dispatcher.scheduler.runCurrent()

            assertEquals(FoodId("1"), repository.lastToggledFavorite)
            assertTrue(viewModel.state.value.isFavorite)
        }

    @Test
    fun onToggleFavorite_forNewFood_flipsTheDraftWithoutPersisting() =
        runTest {
            val viewModel = newViewModel(foodId = null)

            viewModel.onAction(FoodEditAction.OnToggleFavorite)
            dispatcher.scheduler.runCurrent()

            assertTrue(viewModel.state.value.isFavorite)
            assertEquals(null, repository.lastToggledFavorite)
        }

    @Test
    fun save_doesNotOverwriteTheFavoriteFlagOfAnExistingFood() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", name = "Apple", isFavorite = true)))
            val viewModel = newViewModel(foodId = "1")
            dispatcher.scheduler.runCurrent()

            // The favorite is toggled off on the detail screen while the editor is open.
            repository.setFavorite("1", favorite = false)
            dispatcher.scheduler.runCurrent()

            viewModel.onAction(FoodEditAction.OnKcalChange("60"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            val saved = repository.savedEdits.single()
            assertEquals(60.0, saved.nutritionPer100g!!.kcal, 0.0)
            assertFalse(saved.isFavorite)
        }

    @Test
    fun newFood_saveKeepsTheDraftedFavoriteFlag() =
        runTest {
            val viewModel = newViewModel(foodId = null)
            viewModel.onAction(FoodEditAction.OnNameChange("Banana"))
            viewModel.onAction(FoodEditAction.OnToggleFavorite)
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            assertTrue(repository.savedEdits.single().isFavorite)
        }

    @Test
    fun saveFailure_setsSaveError_andDoesNotPersist() =
        runTest {
            repository.shouldFailSave = true
            val viewModel = newViewModel(foodId = null)
            viewModel.onAction(FoodEditAction.OnNameChange("Banana"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            assertTrue(viewModel.state.value.saveError != null)
            assertFalse(viewModel.state.value.isSaving)
            assertTrue(repository.savedEdits.isEmpty())
        }

    @Test
    fun retryAfterFailure_clearsSaveError_onSuccess() =
        runTest {
            repository.shouldFailSave = true
            val viewModel = newViewModel(foodId = null)
            viewModel.onAction(FoodEditAction.OnNameChange("Banana"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()
            assertTrue(viewModel.state.value.saveError != null)

            repository.shouldFailSave = false
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()

            assertEquals(null, viewModel.state.value.saveError)
        }

    @Test
    fun userInput_clearsSaveError() =
        runTest {
            repository.shouldFailSave = true
            val viewModel = newViewModel(foodId = null)
            viewModel.onAction(FoodEditAction.OnNameChange("Banana"))
            viewModel.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()
            assertTrue(viewModel.state.value.saveError != null)

            viewModel.onAction(FoodEditAction.OnKcalChange("10"))

            assertEquals(null, viewModel.state.value.saveError)
        }

    @Test
    fun processDeath_restoresAllDraftFields() {
        val handle = SavedStateHandle()
        val first = newViewModel(foodId = null, savedStateHandle = handle)
        first.onAction(FoodEditAction.OnNameChange("Banana"))
        first.onAction(FoodEditAction.OnBarcodeChange("123"))
        first.onAction(FoodEditAction.OnKcalChange("89"))
        first.onAction(FoodEditAction.OnProteinChange("1,1"))
        first.onAction(FoodEditAction.OnFatChange("0.3"))
        first.onAction(FoodEditAction.OnCarbsChange("22"))
        first.onAction(FoodEditAction.OnFiberChange("2.6"))
        first.onAction(FoodEditAction.OnPortionGramsChange("120"))
        first.onAction(FoodEditAction.OnToggleFavorite)

        val restored = newViewModel(foodId = null, savedStateHandle = handle)

        assertEquals("Banana", restored.state.value.name)
        assertEquals("123", restored.state.value.barcode)
        assertEquals("89", restored.state.value.kcal)
        assertEquals("1,1", restored.state.value.protein)
        assertEquals("0.3", restored.state.value.fat)
        assertEquals("22", restored.state.value.carbs)
        assertEquals("2.6", restored.state.value.fiber)
        assertEquals("120", restored.state.value.portionGrams)
        assertTrue(restored.state.value.isFavorite)
        assertTrue(restored.state.value.isDirty)
    }

    @Test
    fun processDeath_ofPartiallyEditedExistingFood_restoresSeededFields() =
        runTest {
            repository.emitFoods(listOf(FakeFoodRepository.food(id = "1", name = "Apple", kcal = 52.0)))
            val handle = SavedStateHandle()
            val first = newViewModel(foodId = "1", savedStateHandle = handle)
            dispatcher.scheduler.runCurrent()
            first.onAction(FoodEditAction.OnKcalChange("99"))

            val restored = newViewModel(foodId = "1", savedStateHandle = handle)
            dispatcher.scheduler.runCurrent()

            assertEquals("Apple", restored.state.value.name)
            assertEquals("99", restored.state.value.kcal)
        }

    @Test
    fun processDeath_restoresSaveError() =
        runTest {
            repository.shouldFailSave = true
            val handle = SavedStateHandle()
            val first = newViewModel(foodId = null, savedStateHandle = handle)
            first.onAction(FoodEditAction.OnNameChange("Banana"))
            first.onAction(FoodEditAction.OnSaveClick)
            dispatcher.scheduler.runCurrent()
            val expected = first.state.value.saveError
            assertTrue(expected != null)

            val restored = newViewModel(foodId = null, savedStateHandle = handle)

            assertEquals(expected, restored.state.value.saveError)
        }

    private fun newViewModel(
        foodId: String?,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): FoodEditViewModel =
        FoodEditViewModel(
            foodId = foodId,
            prefillBarcode = null,
            repository = repository,
            savedStateHandle = savedStateHandle,
        )
}
