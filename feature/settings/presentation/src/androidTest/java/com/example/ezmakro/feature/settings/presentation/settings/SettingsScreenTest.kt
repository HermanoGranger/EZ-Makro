package com.example.ezmakro.feature.settings.presentation.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var backCount = 0
    private var goalsCount = 0
    private var backupCount = 0

    private fun setScreen() {
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = { backCount++ },
                onNavigateToGoals = { goalsCount++ },
                onNavigateToBackup = { backupCount++ },
            )
        }
    }

    @Test
    fun showsTitleAndBothRows() {
        setScreen()

        composeTestRule.onNodeWithText("Einstellungen").assertExists()
        composeTestRule.onNodeWithText("Tagesziele").assertExists()
        composeTestRule.onNodeWithText("Kalorien und Makros pro Tag").assertExists()
        composeTestRule.onNodeWithText("Backup").assertExists()
        composeTestRule.onNodeWithText("Daten sichern und wiederherstellen").assertExists()
    }

    @Test
    fun goalsRowClick_invokesNavigateToGoals() {
        setScreen()

        composeTestRule.onNodeWithText("Tagesziele").performClick()

        assertEquals(1, goalsCount)
        assertEquals(0, backupCount)
        assertEquals(0, backCount)
    }

    @Test
    fun backupRowClick_invokesNavigateToBackup() {
        setScreen()

        composeTestRule.onNodeWithText("Backup").performClick()

        assertEquals(1, backupCount)
        assertEquals(0, goalsCount)
    }

    @Test
    fun backButton_invokesNavigateBack() {
        setScreen()

        composeTestRule.onNodeWithContentDescription("Zurück").performClick()

        assertEquals(1, backCount)
    }
}
