package com.example.ezmakro.feature.food.presentation.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerAction
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerState
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerTab
import com.example.ezmakro.feature.foodpicker.presentation.picker.PickerFoodUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FoodSearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val food =
        PickerFoodUi(
            id = "1",
            name = "Apple",
            kcalPer100g = 52.0,
            servingLabel = null,
            isFavorite = true,
        )

    @Test
    fun showsTitleAndTabs() {
        composeTestRule.setContent {
            FoodSearchScreen(
                state = FoodPickerState(),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Lebensmittel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Barcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Suche").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favoriten").assertIsDisplayed()
    }

    @Test
    fun showsRecentFoods_inSearchTab() {
        composeTestRule.setContent {
            FoodSearchScreen(
                state = FoodPickerState(recentFoods = listOf(food)),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Zuletzt verwendet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apple").assertIsDisplayed()
    }

    @Test
    fun clickingRecentFood_triggersFoodClickAction() {
        var clickedId: String? = null
        composeTestRule.setContent {
            FoodSearchScreen(
                state = FoodPickerState(recentFoods = listOf(food)),
                onAction = { action ->
                    if (action is FoodPickerAction.OnFoodClick) clickedId = action.foodId
                },
            )
        }

        composeTestRule.onNodeWithText("Apple").performClick()

        assertEquals("1", clickedId)
    }

    @Test
    fun clickingFavoriteFood_triggersFoodClickAction() {
        var clickedId: String? = null
        composeTestRule.setContent {
            FoodSearchScreen(
                state =
                    FoodPickerState(
                        activeTab = FoodPickerTab.FAVORITES,
                        favoriteFoods = listOf(food),
                    ),
                onAction = { action ->
                    if (action is FoodPickerAction.OnFoodClick) clickedId = action.foodId
                },
            )
        }

        composeTestRule.onNodeWithText("Apple").performClick()

        assertEquals("1", clickedId)
    }
}
