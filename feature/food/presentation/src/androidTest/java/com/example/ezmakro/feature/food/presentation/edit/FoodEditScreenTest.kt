package com.example.ezmakro.feature.food.presentation.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.feature.food.presentation.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FoodEditScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsPrefilledBarcode_whenProvided() {
        composeTestRule.setContent {
            FoodEditScreen(
                state = FoodEditState(barcode = "987654321"),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("987654321").assertIsDisplayed()
    }

    @Test
    fun displaysNameError_whenSet() {
        composeTestRule.setContent {
            FoodEditScreen(
                state =
                    FoodEditState(
                        nameError = UiText.StringResource(R.string.food_edit_error_name_blank),
                    ),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Name darf nicht leer sein").assertIsDisplayed()
    }

    @Test
    fun typingName_triggersNameChangeAction() {
        var changedTo: String? = null
        composeTestRule.setContent {
            FoodEditScreen(
                state = FoodEditState(),
                onAction = { if (it is FoodEditAction.OnNameChange) changedTo = it.value },
            )
        }

        composeTestRule.onNodeWithText("Name").performTextInput("Banana")

        assertEquals("Banana", changedTo)
    }

    @Test
    fun clickingSave_triggersSaveAction() {
        var saved = false
        composeTestRule.setContent {
            FoodEditScreen(
                state = FoodEditState(),
                onAction = { if (it is FoodEditAction.OnSaveClick) saved = true },
            )
        }

        composeTestRule.onNodeWithText("Speichern").performClick()

        assertTrue(saved)
    }

    @Test
    fun saveDisabled_whileSaving() {
        composeTestRule.setContent {
            FoodEditScreen(
                state = FoodEditState(isSaving = true),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Speichern").assertIsNotEnabled()
    }

    @Test
    fun showsUserModifiedBadge_whenCachedStatusIsUserModified() {
        composeTestRule.setContent {
            FoodEditScreen(
                state = FoodEditState(cachedStatus = CachedFoodItemStatus.UserModified),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Lokal angepasst").assertIsDisplayed()
    }

    @Test
    fun showsUpdateAvailableBadge_whenCachedStatusIsUpdateAvailable() {
        composeTestRule.setContent {
            FoodEditScreen(
                state = FoodEditState(cachedStatus = CachedFoodItemStatus.UpdateAvailable),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Update verfügbar").assertIsDisplayed()
    }

    @Test
    fun showsNoBadge_forAnUnmodifiedOrNotYetLoadedFood() {
        composeTestRule.setContent {
            FoodEditScreen(
                state = FoodEditState(cachedStatus = CachedFoodItemStatus.Original),
                onAction = {},
            )
        }

        composeTestRule.onNodeWithText("Lokal angepasst").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Update verfügbar").assertIsNotDisplayed()
    }
}
