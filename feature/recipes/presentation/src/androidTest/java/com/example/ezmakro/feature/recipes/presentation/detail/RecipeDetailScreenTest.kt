package com.example.ezmakro.feature.recipes.presentation.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecipeDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun recipeUi(
        name: String = "Pfannkuchen",
        per100gKcal: String? = "100",
    ) = com.example.ezmakro.feature.recipes.presentation.model.RecipeUi(
        id = "r1",
        name = name,
        category = "Backen",
        isMealPrep = true,
        portions = 2,
        ingredientCount = 1,
        totalKcal = "200",
        perPortionKcal = "100",
        per100gKcal = per100gKcal,
    )

    @Test
    fun showsComputedNutrition() {
        composeTestRule.setContent {
            RecipeDetailScreen(
                state = RecipeDetailState(recipe = recipeUi()),
                onAction = {},
                onRequestDelete = {},
            )
        }
        composeTestRule.onNodeWithText("Pfannkuchen").assertIsDisplayed()
        composeTestRule.onNodeWithText("200 kcal").assertIsDisplayed()
        assertEquals(2, composeTestRule.onAllNodesWithText("100 kcal").fetchSemanticsNodes().size)
    }

    @Test
    fun showsHint_whenNoFinalWeight() {
        composeTestRule.setContent {
            RecipeDetailScreen(
                state = RecipeDetailState(recipe = recipeUi(per100gKcal = null)),
                onAction = {},
                onRequestDelete = {},
            )
        }
        composeTestRule.onNodeWithText("Endgewicht nicht erfasst – \"pro 100 g\" nicht verfügbar").assertIsDisplayed()
    }

    @Test
    fun clickingDuplicate_triggersAction() {
        var clicked = false
        composeTestRule.setContent {
            RecipeDetailScreen(
                state = RecipeDetailState(recipe = recipeUi()),
                onAction = { if (it is RecipeDetailAction.OnDuplicateClick) clicked = true },
                onRequestDelete = {},
            )
        }
        composeTestRule.onNodeWithText("Duplizieren").performClick()
        assertTrue(clicked)
    }
}
