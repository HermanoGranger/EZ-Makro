package com.example.ezmakro.feature.dashboard.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ezmakro.feature.dashboard.presentation.model.DashboardUi
import com.example.ezmakro.feature.dashboard.presentation.model.MacroRowUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleUi(): DashboardUi =
        DashboardUi(
            remainingKcal = "+1700",
            consumedKcal = "300",
            goalKcal = "2000",
            kcalProgress = 0.15f,
            isOverGoal = false,
            macros =
                listOf(
                    MacroRowUi(
                        macro = com.example.ezmakro.core.domain.calculation.Macro.PROTEIN,
                        label = "Protein",
                        consumed = "30",
                        goal = "150",
                        progress = 0.2f,
                    ),
                    MacroRowUi(
                        macro = com.example.ezmakro.core.domain.calculation.Macro.FAT,
                        label = "Fett",
                        consumed = "15",
                        goal = "56",
                        progress = 0.27f,
                    ),
                    MacroRowUi(
                        macro = com.example.ezmakro.core.domain.calculation.Macro.CARBS,
                        label = "Kohlenhydrate",
                        consumed = "60",
                        goal = "225",
                        progress = 0.27f,
                    ),
                    MacroRowUi(
                        macro = com.example.ezmakro.core.domain.calculation.Macro.FIBER,
                        label = "Ballaststoffe",
                        consumed = "8",
                        goal = "30",
                        progress = 0.27f,
                    ),
                ),
        )

    @Test
    fun displaysRemainingKcalAndMacros() {
        composeTestRule.setContent {
            DashboardScreen(
                state = DashboardState(dashboard = sampleUi(), isLoading = false),
                onOpenDiary = {},
                onConfigureGoals = {},
            )
        }

        composeTestRule.onNodeWithText("+1700").assertExists()
        composeTestRule.onNodeWithText("Restkalorien").assertExists()
        composeTestRule.onNodeWithText("Protein").assertExists()
        composeTestRule.onNodeWithText("Kohlenhydrate").assertExists()
        composeTestRule.onNodeWithText("Ballaststoffe").assertExists()
    }

    @Test
    fun openDiaryButtonInvokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            DashboardScreen(
                state = DashboardState(dashboard = sampleUi(), isLoading = false),
                onOpenDiary = { clicked = true },
                onConfigureGoals = {},
            )
        }

        composeTestRule.onNodeWithText("Zum Tagebuch").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun overGoalShowsNegativeRemaining() {
        val overUi = sampleUi().copy(remainingKcal = "-500", isOverGoal = true)
        composeTestRule.setContent {
            DashboardScreen(
                state = DashboardState(dashboard = overUi, isLoading = false),
                onOpenDiary = {},
                onConfigureGoals = {},
            )
        }

        composeTestRule.onNodeWithText("-500").assertExists()
    }
}
