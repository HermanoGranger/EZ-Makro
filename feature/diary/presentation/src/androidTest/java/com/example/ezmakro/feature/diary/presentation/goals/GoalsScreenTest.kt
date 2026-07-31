package com.example.ezmakro.feature.diary.presentation.goals

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GoalsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysAllGoalFields() {
        composeTestRule.setContent {
            GoalsScreen(
                state = GoalsState(kcal = "2000", protein = "150", fat = "56", carbs = "225", fiber = "30"),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Kalorien (kcal)").assertExists()
        composeTestRule.onNodeWithText("Protein (g)").assertExists()
        composeTestRule.onNodeWithText("Fett (g)").assertExists()
        composeTestRule.onNodeWithText("Kohlenhydrate (g)").assertExists()
        composeTestRule.onNodeWithText("Ballaststoffe (g)").assertExists()
    }

    @Test
    fun saveButtonInvokesSaveAction() {
        var saved = false
        composeTestRule.setContent {
            GoalsScreen(state = GoalsState(), onAction = { if (it is GoalsAction.OnSaveClick) saved = true })
        }

        composeTestRule.onNodeWithText("Speichern").performClick()
        assertEquals(true, saved)
    }

    @Test
    fun resetButtonInvokesResetAction() {
        var reset = false
        composeTestRule.setContent {
            GoalsScreen(state = GoalsState(), onAction = { if (it is GoalsAction.OnResetToDefaultClick) reset = true })
        }

        composeTestRule.onNodeWithText("Auf Standard zurücksetzen").performClick()
        assertEquals(true, reset)
    }

    @Test
    fun typingInKcalFieldInvokesChangeAction() {
        var lastValue = ""
        composeTestRule.setContent {
            GoalsScreen(state = GoalsState(), onAction = { if (it is GoalsAction.OnKcalChange) lastValue = it.value })
        }

        composeTestRule.onNodeWithText("Kalorien (kcal)").performTextInput("1800")
        assertEquals("1800", lastValue)
    }
}
