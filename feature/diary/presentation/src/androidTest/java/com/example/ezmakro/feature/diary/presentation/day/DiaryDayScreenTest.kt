package com.example.ezmakro.feature.diary.presentation.day

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.feature.diary.presentation.model.DiaryEntryUi
import com.example.ezmakro.feature.diary.presentation.model.SlotSummaryUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DiaryDayScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun food(id: String): FoodItem =
        FoodItem(
            id = FoodId(id),
            name = "Food $id",
            barcode = null,
            nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
            servingUnit = null,
            isUserCreated = true,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )

    private fun entryUi(
        id: String,
        slot: MealSlot,
        name: String,
        kcal: String,
    ): DiaryEntryUi =
        DiaryEntryUi(
            id = id,
            name = name,
            slot = slot,
            isRecipe = false,
            detail = "150 g",
            kcal = kcal,
            protein = "15",
            fat = "3",
            carbs = "7",
            fiber = "1",
        )

    @Test
    fun displaysEntriesGroupedBySlot_withDayTotal() {
        val state =
            DiaryDayState(
                date = LocalDate.of(2026, 7, 19),
                breakfast = listOf(entryUi("a", MealSlot.BREAKFAST, "Haferflocken", "150")),
                lunch = listOf(entryUi("b", MealSlot.LUNCH, "Reis", "150")),
                dinner = emptyList(),
                snacks = emptyList(),
                dayTotal =
                    SlotSummaryUi(
                        slot = null,
                        title = "Tagesgesamt",
                        kcal = "300",
                        protein = "30",
                        fat = "6",
                        carbs = "14",
                        fiber = "2",
                    ),
            )

        composeTestRule.setContent {
            DiaryDayScreen(
                state = state,
                snackbarHostState = SnackbarHostState(),
                callbacks =
                    DiaryDayCallbacks(
                        onAction = {},
                        onRequestDelete = { _, _ -> },
                        onNavigateToAddFood = {},
                        onNavigateToAddRecipe = {},
                        onNavigateToQuickAdd = {},
                        onNavigateToHistory = {},
                    ),
            )
        }

        composeTestRule.onNodeWithText("Haferflocken").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reis").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tagesgesamt").assertIsDisplayed()
        composeTestRule.onNodeWithText("300 kcal").assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNoEntries() {
        composeTestRule.setContent {
            DiaryDayScreen(
                state = DiaryDayState(date = LocalDate.of(2026, 7, 19)),
                snackbarHostState = SnackbarHostState(),
                callbacks =
                    DiaryDayCallbacks(
                        onAction = {},
                        onRequestDelete = { _, _ -> },
                        onNavigateToAddFood = {},
                        onNavigateToAddRecipe = {},
                        onNavigateToQuickAdd = {},
                        onNavigateToHistory = {},
                    ),
            )
        }

        composeTestRule.onNodeWithText("Haferflocken").assertDoesNotExist()
        composeTestRule.onNodeWithText("Noch keine Einträge für diesen Tag").assertIsDisplayed()
    }

    @Test
    fun copyYesterdayButton_triggersAction() {
        var clicked = false
        composeTestRule.setContent {
            DiaryDayScreen(
                state = DiaryDayState(date = LocalDate.of(2026, 7, 19)),
                snackbarHostState = SnackbarHostState(),
                callbacks =
                    DiaryDayCallbacks(
                        onAction = { if (it is DiaryDayAction.OnCopyYesterdayClick) clicked = true },
                        onRequestDelete = { _, _ -> },
                        onNavigateToAddFood = {},
                        onNavigateToAddRecipe = {},
                        onNavigateToQuickAdd = {},
                        onNavigateToHistory = {},
                    ),
            )
        }

        composeTestRule.onNodeWithText("Gestern kopieren").performClick()
        assertEquals(true, clicked)
    }
}
