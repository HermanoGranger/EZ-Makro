package com.example.ezmakro.feature.food.presentation.detail

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ezmakro.feature.food.presentation.model.FoodEditStatusUi
import com.example.ezmakro.feature.food.presentation.model.FoodUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
}
