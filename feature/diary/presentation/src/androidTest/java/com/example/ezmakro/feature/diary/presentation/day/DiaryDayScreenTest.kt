package com.example.ezmakro.feature.diary.presentation.day

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ezmakro.core.domain.calculation.Macro
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.feature.diary.presentation.model.DiaryEntryUi
import com.example.ezmakro.feature.diary.presentation.model.DiarySummaryUi
import com.example.ezmakro.feature.diary.presentation.model.MacroRowUi
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
            amountGrams = 150.0,
            portionCount = null,
            hasServingUnit = false,
            kcal = kcal,
            protein = "15",
            fat = "3",
            carbs = "7",
            fiber = "1",
        )

    @Test
    fun displaysEntriesGroupedBySlot_withSummaryHeader() {
        val state =
            DiaryDayState(
                date = LocalDate.of(2026, 7, 19),
                breakfast = listOf(entryUi("a", MealSlot.BREAKFAST, "Haferflocken", "150")),
                lunch = listOf(entryUi("b", MealSlot.LUNCH, "Reis", "150")),
                dinner = emptyList(),
                snacks = emptyList(),
                summary = sampleSummary(),
            )

        composeTestRule.setContent {
            DiaryDayScreen(
                state = state,
                snackbarHostState = SnackbarHostState(),
                callbacks =
                    DiaryDayCallbacks(
                        onAction = {},
                        onRequestDelete = { _, _ -> },
                        onRequestEdit = {},
                        onNavigateToAddFood = {},
                        onNavigateToQuickAdd = {},
                        onNavigateToHistory = {},
                    ),
            )
        }

        composeTestRule.onNodeWithText("Haferflocken").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reis").assertIsDisplayed()
        composeTestRule.onNodeWithText("+1700").assertIsDisplayed()
        composeTestRule.onNodeWithText("Restkalorien").assertIsDisplayed()
        composeTestRule.onNodeWithText("Protein").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kohlenhydrate").assertIsDisplayed()
    }

    @Test
    fun summaryHeader_showsNegativeRemaining_whenOverGoal() {
        val state =
            DiaryDayState(
                date = LocalDate.of(2026, 7, 19),
                summary = sampleSummary(remainingKcal = "-500", isOverGoal = true),
            )

        composeTestRule.setContent {
            DiaryDayScreen(
                state = state,
                snackbarHostState = SnackbarHostState(),
                callbacks =
                    DiaryDayCallbacks(
                        onAction = {},
                        onRequestDelete = { _, _ -> },
                        onRequestEdit = {},
                        onNavigateToAddFood = {},
                        onNavigateToQuickAdd = {},
                        onNavigateToHistory = {},
                    ),
            )
        }

        composeTestRule.onNodeWithText("-500").assertIsDisplayed()
    }

    @Test
    fun collapsedSummaryHeader_showsExpandChevronAndBars() {
        composeTestRule.setContent {
            CollapsingSummaryHeaderContent(
                summary = sampleSummary(),
                fraction = 1f,
                onToggleCollapse = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Ausklappen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Protein").assertIsDisplayed()
    }

    @Test
    fun summaryHeader_chevronToggleInvokesCallback() {
        var toggled = false
        composeTestRule.setContent {
            CollapsingSummaryHeaderContent(
                summary = sampleSummary(),
                fraction = 0f,
                onToggleCollapse = { toggled = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Zusammenklappen").performClick()
        assertEquals(true, toggled)
    }

    private fun sampleSummary(
        remainingKcal: String = "+1700",
        isOverGoal: Boolean = false,
    ): DiarySummaryUi =
        DiarySummaryUi(
            remainingKcal = remainingKcal,
            consumedKcal = "300",
            goalKcal = "2000",
            kcalProgress = 0.15f,
            isOverGoal = isOverGoal,
            macros =
                listOf(
                    MacroRowUi(Macro.PROTEIN, "Protein", "30", "150", 0.2f),
                    MacroRowUi(Macro.FAT, "Fett", "15", "56", 0.27f),
                    MacroRowUi(Macro.CARBS, "Kohlenhydrate", "60", "225", 0.27f),
                    MacroRowUi(Macro.FIBER, "Ballaststoffe", "8", "30", 0.27f),
                ),
        )

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
                        onRequestEdit = {},
                        onNavigateToAddFood = {},
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
                        onRequestEdit = {},
                        onNavigateToAddFood = {},
                        onNavigateToQuickAdd = {},
                        onNavigateToHistory = {},
                    ),
            )
        }

        composeTestRule.onNodeWithText("Gestern kopieren").performClick()
        assertEquals(true, clicked)
    }
}
