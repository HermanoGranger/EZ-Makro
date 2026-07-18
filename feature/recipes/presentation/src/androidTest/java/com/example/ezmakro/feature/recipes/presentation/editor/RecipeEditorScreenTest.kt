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
        calculationMode: RecipeCalculationMode = RecipeCalculationMode.Portion,
        finalWeight: String = "",
    ) = RecipeEditorState(
        isEditingExisting = false,
        isQuickMeal = false,
        name = name,
        ingredients = ingredients,
        calculationMode = calculationMode,
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
    fun quickMealHidesMealPrepSwitch() {
        composeTestRule.setContent {
            RecipeEditorScreen(
                state = state().copy(isQuickMeal = true),
                onAction = {},
                onOpenPicker = {},
            )
        }
        composeTestRule.onNodeWithText("Meal Prep").assertDoesNotExist()
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
    fun portionMode_hidesFinalWeightField() {
        composeTestRule.setContent {
            RecipeEditorScreen(
                state = state(calculationMode = RecipeCalculationMode.Portion),
                onAction = {},
                onOpenPicker = {},
            )
        }
        composeTestRule.onNodeWithText("Endgewicht (g)").assertDoesNotExist()
    }

    @Test
    fun weightMode_showsFinalWeightField() {
        composeTestRule.setContent {
            RecipeEditorScreen(
                state =
                    state(
                        calculationMode = RecipeCalculationMode.Weight,
                        finalWeight = "1800",
                    ),
                onAction = {},
                onOpenPicker = {},
            )
        }
        composeTestRule.onNodeWithText("Endgewicht (g)").assertIsDisplayed()
    }

    @Test
    fun switchingToWeightMode_emitsCalculationModeChange() {
        var mode: RecipeCalculationMode? = null
        composeTestRule.setContent {
            RecipeEditorScreen(
                state = state(calculationMode = RecipeCalculationMode.Portion),
                onAction = { if (it is RecipeEditorAction.OnCalculationModeChange) mode = it.mode },
                onOpenPicker = {},
            )
        }
        composeTestRule.onNodeWithText("Gewicht").assertIsDisplayed().performClick()
        assertEquals(RecipeCalculationMode.Weight, mode)
    }
}
