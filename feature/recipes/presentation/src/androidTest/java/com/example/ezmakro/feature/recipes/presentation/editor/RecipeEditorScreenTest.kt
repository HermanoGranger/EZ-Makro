package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecipeEditorScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun state(
        name: String = "",
        ingredients: List<IngredientDraft> = emptyList(),
        finalWeight: String = "",
    ) = RecipeEditorState(
        isEditingExisting = false,
        name = name,
        ingredients = ingredients,
        finalWeight = finalWeight,
    )

    @Test
    fun showsNameField() {
        composeTestRule.setContent { RecipeEditorScreen(state = state(), onAction = {}, onOpenPicker = {}) }
        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
    }

    @Test
    fun typingName_triggersNameChange() {
        var changed: String? = null
        composeTestRule.setContent {
            RecipeEditorScreen(
                state = state(),
                onAction = { if (it is RecipeEditorAction.OnNameChange) changed = it.value },
                onOpenPicker = {},
            )
        }
        composeTestRule.onNodeWithText("Name").performTextInput("Brot")
        assertEquals("Brot", changed)
    }

    @Test
    fun clickingAddIngredient_opensPicker() {
        var opened = false
        composeTestRule.setContent {
            RecipeEditorScreen(state = state(), onAction = {}, onOpenPicker = { opened = true })
        }
        composeTestRule.onNodeWithText("Zutat hinzufügen").performClick()
        assertTrue(opened)
    }

    @Test
    fun ingredientRowShowsFoodName() {
        composeTestRule.setContent {
            RecipeEditorScreen(
                state =
                    state(
                        ingredients =
                            listOf(
                                IngredientDraft(
                                    foodId = "f1",
                                    foodName = "Apple",
                                    servingLabel = null,
                                    rawWeight = "100",
                                ),
                            ),
                    ),
                onAction = {},
                onOpenPicker = {},
            )
        }
        composeTestRule.onNodeWithText("Apple").assertIsDisplayed()
    }

    @Test
    fun finalWeightFieldIsAlwaysVisible() {
        composeTestRule.setContent {
            RecipeEditorScreen(
                state = state(),
                onAction = {},
                onOpenPicker = {},
            )
        }
        composeTestRule.onNodeWithText("Endgewicht (g)").assertIsDisplayed()
    }
}
