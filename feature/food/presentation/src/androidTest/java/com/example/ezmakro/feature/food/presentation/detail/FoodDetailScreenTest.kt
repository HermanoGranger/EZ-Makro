package com.example.ezmakro.feature.food.presentation.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.feature.food.presentation.model.FoodEditStatusUi
import com.example.ezmakro.feature.food.presentation.model.FoodUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class FoodDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val food =
        FoodUi(
            id = "1",
            name = "Apple",
            barcode = "123",
            kcalPer100g = "52",
            proteinPer100g = "0",
            fatPer100g = "0",
            carbsPer100g = "14",
            fiberPer100g = "2",
            servingLabel = null,
            isUserCreated = false,
            isFavorite = false,
            editStatus = FoodEditStatusUi.Original,
        )

    @Test
    fun displaysNutritionValues_whenFoodLoaded() {
        composeTestRule.setContent {
            FoodDetailScreen(state = FoodDetailState(food = food, isLoading = false), onAction = {})
        }

        composeTestRule.onNodeWithText("Apple").assertIsDisplayed()
        composeTestRule.onNodeWithText("52 kcal").assertIsDisplayed()
        composeTestRule.onNodeWithText("14 g").assertIsDisplayed()
    }

    @Test
    fun displaysServingNutrition_whenServingUnitPresent() {
        val servingFood =
            food.copy(
                servingLabel = "100 ml",
                servingGrams = 200.0,
                servingAmount = "100 ml",
                kcalPerServing = "104",
                proteinPerServing = "0",
                fatPerServing = "0",
                carbsPerServing = "28",
                fiberPerServing = "4",
            )

        composeTestRule.setContent {
            FoodDetailScreen(state = FoodDetailState(food = servingFood, isLoading = false), onAction = {})
        }

        composeTestRule.onNodeWithText("Pro Portion (100 ml)").assertIsDisplayed()
        composeTestRule.onNodeWithText("104 kcal").assertIsDisplayed()
        composeTestRule.onNodeWithText("28 g").assertIsDisplayed()
    }

    @Test
    fun loadingState_showsProgressIndicator() {
        composeTestRule.setContent {
            FoodDetailScreen(state = FoodDetailState(isLoading = true), onAction = {})
        }

        composeTestRule.onNodeWithText("Apple").assertDoesNotExist()
    }

    @Test
    fun clickingFavorite_triggersToggleAction() {
        var toggled = false
        composeTestRule.setContent {
            FoodDetailScreen(
                state = FoodDetailState(food = food, isLoading = false),
                onAction = { if (it is FoodDetailAction.OnToggleFavorite) toggled = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Zu Favoriten").performClick()

        assertTrue(toggled)
    }

    @Test
    fun clickingEdit_triggersEditAction() {
        var edited = false
        composeTestRule.setContent {
            FoodDetailScreen(
                state = FoodDetailState(food = food, isLoading = false),
                onAction = { if (it is FoodDetailAction.OnEditClick) edited = true },
            )
        }

        composeTestRule.onNodeWithText("Bearbeiten").performClick()

        assertTrue(edited)
    }

    @Test
    fun userModifiedStatus_showsResetAndRefetchActions() {
        val modifiedFood = food.copy(editStatus = FoodEditStatusUi.UserModified)
        composeTestRule.setContent {
            FoodDetailScreen(
                state = FoodDetailState(food = modifiedFood, isLoading = false),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Auf Original zurücksetzen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Neu von OpenFoodFacts").assertIsDisplayed()
    }

    @Test
    fun refetchDialog_appearsAndConfirms() {
        var confirmed = false
        composeTestRule.setContent {
            FoodDetailScreen(
                state =
                    FoodDetailState(
                        food = food.copy(editStatus = FoodEditStatusUi.UserModified),
                        isLoading = false,
                        showRefetchConfirm = true,
                    ),
                onAction = { if (it is FoodDetailAction.OnRefetchConfirm) confirmed = true },
            )
        }

        composeTestRule.onNodeWithText("Neu von OpenFoodFacts laden?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fortfahren").performClick()

        assertEquals(true, confirmed)
    }

    @Test
    fun addButton_openAddToDiarySheet_withFixedSlot_hidesSlotSelector() {
        composeTestRule.setContent {
            FoodDetailScreen(
                state = FoodDetailState(food = food, isLoading = false),
                onAction = {},
                initialSlot = MealSlot.LUNCH,
            )
        }

        composeTestRule.onNodeWithContentDescription("Hinzufügen").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Hinzufügen").performClick()

        composeTestRule.onNodeWithText("Lebensmittel hinzufügen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mahlzeit").assertDoesNotExist()
    }

    @Test
    fun addButton_withoutSlot_showsSlotSelector() {
        composeTestRule.setContent {
            FoodDetailScreen(
                state = FoodDetailState(food = food, isLoading = false),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Hinzufügen").performClick()

        composeTestRule.onNodeWithText("Mahlzeit").assertIsDisplayed()
    }

    @Test
    fun addSheet_confirm_callsOnAddToDiaryWithFixedSlotAndTodayDate() {
        var addedFoodId: String? = null
        var addedDate: String? = null
        var addedSlot: MealSlot? = null
        var addedAmount: Double? = null
        var addedIsGrams: Boolean? = null
        composeTestRule.setContent {
            FoodDetailScreen(
                state = FoodDetailState(food = food, isLoading = false),
                onAction = {},
                onAddToDiary = { foodId, date, slot, amount, isGrams ->
                    addedFoodId = foodId
                    addedDate = date
                    addedSlot = slot
                    addedAmount = amount
                    addedIsGrams = isGrams
                },
                initialSlot = MealSlot.DINNER,
                date = "",
            )
        }

        composeTestRule.onNodeWithContentDescription("Hinzufügen").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals("1", addedFoodId)
        assertEquals(LocalDate.now().toString(), addedDate)
        assertEquals(MealSlot.DINNER, addedSlot)
        assertEquals(100.0, addedAmount ?: -1.0, 0.0)
        assertEquals(true, addedIsGrams)
    }

    @Test
    fun addSheet_confirm_withoutSlot_usesUserSelectedSlot() {
        var addedSlot: MealSlot? = null
        composeTestRule.setContent {
            FoodDetailScreen(
                state = FoodDetailState(food = food, isLoading = false),
                onAction = {},
                onAddToDiary = { _, date, slot, amount, isGrams ->
                    addedSlot = slot
                },
            )
        }

        composeTestRule.onNodeWithContentDescription("Hinzufügen").performClick()
        composeTestRule.onNodeWithText("Mittag").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(MealSlot.LUNCH, addedSlot)
    }
}
