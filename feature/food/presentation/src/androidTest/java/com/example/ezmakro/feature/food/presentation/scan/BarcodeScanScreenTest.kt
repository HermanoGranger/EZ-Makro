package com.example.ezmakro.feature.food.presentation.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ezmakro.core.presentation.uitext.UiText
import org.junit.Rule
import org.junit.Test

class BarcodeScanScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsPermissionRequired_whenNoCameraPermission() {
        composeTestRule.setContent {
            BarcodeScanScreen(state = BarcodeScanState(hasCameraPermission = false), onAction = {})
        }

        composeTestRule.onNodeWithText("Kamerazugriff erforderlich").assertIsDisplayed()
    }

    @Test
    fun showsProgress_whenLookingUp() {
        composeTestRule.setContent {
            BarcodeScanScreen(
                state = BarcodeScanState(hasCameraPermission = true, isLookingUp = true),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Kamerazugriff erforderlich").assertDoesNotExist()
    }

    @Test
    fun showsError_whenLookupFails() {
        composeTestRule.setContent {
            BarcodeScanScreen(
                state =
                    BarcodeScanState(
                        hasCameraPermission = true,
                        error = UiText.DynamicString("Kein Treffer – lege ein eigenes Lebensmittel an"),
                    ),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Kein Treffer – lege ein eigenes Lebensmittel an").assertIsDisplayed()
    }
}
