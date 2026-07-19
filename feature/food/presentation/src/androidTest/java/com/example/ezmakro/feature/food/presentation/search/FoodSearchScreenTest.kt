package com.example.ezmakro.feature.food.presentation.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ezmakro.feature.food.presentation.model.FoodUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FoodSearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val favoriteFood =
        FoodUi(
            id = "1",
            name = "Apple",
            barcode = null,
            kcalPer100g = "52",
            proteinPer100g = "0",
            fatPer100g = "0",
            carbsPer100g = "14",
            fiberPer100g = "2",
            servingLabel = null,
            isUserCreated = false,
            isFavorite = true,
            editStatus = com.example.ezmakro.feature.food.presentation.model.FoodEditStatusUi.Original,
        )

    @Test
    fun displaysFavoritesSection_whenNotQuerying() {
        composeTestRule.setContent {
            FoodSearchScreen(
                state =
                    FoodSearchState(
                        favorites = listOf(favoriteFood),
                        recent = emptyList(),
                    ),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Apple").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favoriten").assertIsDisplayed()
    }

    @Test
    fun doesNotShowFavoritesSection_whenQuerying() {
        composeTestRule.setContent {
            FoodSearchScreen(
                state =
                    FoodSearchState(
                        query = "app",
                        favorites = listOf(favoriteFood),
                        localResults = listOf(favoriteFood),
                    ),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Favoriten").assertDoesNotExist()
        composeTestRule.onNodeWithText("Lokale Treffer").assertIsDisplayed()
    }

    @Test
    fun clickingFavoriteIcon_triggersToggleAction() {
        var toggledId: String? = null
        composeTestRule.setContent {
            FoodSearchScreen(
                state = FoodSearchState(favorites = listOf(favoriteFood)),
                onAction = { action ->
                    if (action is FoodSearchAction.OnToggleFavorite) toggledId = action.foodId
                },
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Aus Favoriten entfernen")
            .performClick()

        assertEquals("1", toggledId)
    }

    @Test
    fun clickingFoodRow_triggersNavigationAction() {
        var navigatedId: String? = null
        composeTestRule.setContent {
            FoodSearchScreen(
                state = FoodSearchState(favorites = listOf(favoriteFood)),
                onAction = { action ->
                    if (action is FoodSearchAction.OnFoodClick) navigatedId = action.foodId
                },
            )
        }

        composeTestRule.onNodeWithText("Apple").performClick()

        assertEquals("1", navigatedId)
    }

    @Test
    fun onlineSearchButton_triggersSearchAction() {
        var searched = false
        composeTestRule.setContent {
            FoodSearchScreen(
                state = FoodSearchState(query = "app", localResults = listOf(favoriteFood)),
                onAction = { action ->
                    if (action is FoodSearchAction.OnSearchOnlineClick) searched = true
                },
            )
        }

        composeTestRule.onNodeWithText("Online suchen").performClick()

        assertEquals(true, searched)
    }
}
