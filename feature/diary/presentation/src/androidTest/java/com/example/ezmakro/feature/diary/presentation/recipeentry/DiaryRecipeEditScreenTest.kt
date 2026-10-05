package com.example.ezmakro.feature.diary.presentation.recipeentry

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.domain.usecase.diary.DiaryRecipeEditScope
import com.example.ezmakro.core.presentation.uitext.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiaryRecipeEditScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val actions = mutableListOf<DiaryRecipeEditAction>()

    private fun draft(
        id: String,
        name: String,
        rawWeight: String,
    ) = RecipeIngredientDraft(
        foodId = id,
        foodName = name,
        servingLabel = null,
        rawWeight = rawWeight,
    )

    private fun state(
        recipeName: String = "Bulgur Test",
        ingredients: List<RecipeIngredientDraft> = listOf(draft("f1", "Bulgur", "100")),
        ingredientNutrition: List<NutritionValues> = listOf(NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0)),
        totalNutrition: NutritionValues = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
        originalConsumed: NutritionValues = NutritionValues(50.0, 5.0, 2.5, 1.0, 0.5),
        consumed: NutritionValues = NutritionValues(60.0, 6.0, 3.0, 1.2, 0.6),
        canUpdateRecipe: Boolean = true,
        isSourceRecipeOutdated: Boolean = false,
        sourceRecipeId: String? = "recipe-1",
        amountText: String = "1",
        ingredientError: com.example.ezmakro.core.presentation.uitext.UiText? = null,
        amountError: com.example.ezmakro.core.presentation.uitext.UiText? = null,
        saveError: com.example.ezmakro.core.presentation.uitext.UiText? = null,
        isSaveScopeDialogOpen: Boolean = false,
    ) = DiaryRecipeEditState(
        isLoading = false,
        entryId = "entry-1",
        recipeName = recipeName,
        date = "2026-07-19",
        sourceRecipeId = sourceRecipeId,
        canUpdateRecipe = canUpdateRecipe,
        isSourceRecipeOutdated = isSourceRecipeOutdated,
        amountMode = DiaryAmountMode.PORTIONS,
        amountText = amountText,
        portions = "2",
        finalWeight = "200",
        ingredients = ingredients,
        ingredientNutrition = ingredientNutrition,
        totalNutrition = totalNutrition,
        originalConsumedNutrition = originalConsumed,
        consumedNutrition = consumed,
        ingredientError = ingredientError,
        amountError = amountError,
        saveError = saveError,
        isSaveScopeDialogOpen = isSaveScopeDialogOpen,
    )

    private fun setContent(state: DiaryRecipeEditState) {
        actions.clear()
        composeTestRule.setContent {
            DiaryRecipeEditScreen(
                state = state,
                snackbarHostState = SnackbarHostState(),
                onAction = { actions += it },
            )
        }
    }

    @Test
    fun headerShowsRecipeNameDateAndComparison() {
        setContent(state())

        composeTestRule.onNodeWithText("Bulgur Test").assertIsDisplayed()
        composeTestRule.onNodeWithText("ins Tagebuch: 19.07.2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("50 kcal · P 5 · F 2.5 · KH 1 · B 0.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("60 kcal · P 6 · F 3 · KH 1.2 · B 0.6").assertIsDisplayed()
    }

    @Test
    fun outdatedSourceRecipeShowsHint() {
        setContent(state(isSourceRecipeOutdated = true))

        composeTestRule
            .onNodeWithText(
                "Das Quellrezept wurde inzwischen geändert",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun deletedSourceRecipeShowsHint() {
        setContent(state(canUpdateRecipe = false))

        composeTestRule.onNodeWithText("Das Quellrezept wurde gelöscht").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noDeletedHintForPlainFoodEntries() {
        setContent(state(sourceRecipeId = null, canUpdateRecipe = false))

        composeTestRule.onNodeWithText("Das Quellrezept wurde gelöscht").assertDoesNotExist()
    }

    @Test
    fun addIngredientButtonDispatchesAddClick() {
        setContent(state())

        composeTestRule.onNodeWithText("Zutat hinzufügen").performScrollTo().performClick()

        assertEquals(listOf(DiaryRecipeEditAction.OnAddIngredientClick), actions)
    }

    @Test
    fun replaceFallsBackToRemoveAndAdd_becauseListHasNoHook() {
        setContent(
            state(
                ingredients = listOf(draft("f1", "Bulgur", "100"), draft("f2", "Tomatenmark", "75")),
                ingredientNutrition =
                    listOf(
                        NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
                        NutritionValues(60.0, 6.0, 3.0, 12.0, 1.5),
                    ),
            ),
        )

        composeTestRule
            .onNodeWithText("Zum Ersetzen: Zutat entfernen und neu hinzufügen")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Ersetzen").assertCountEquals(0)
    }

    @Test
    fun ingredientRowClickDispatchesIngredientClick() {
        setContent(state())

        composeTestRule.onNodeWithText("Bulgur").performScrollTo().performClick()

        assertEquals(listOf(DiaryRecipeEditAction.OnIngredientClick(0)), actions)
    }

    @Test
    fun rawWeightFieldDispatchesChange() {
        setContent(state())

        composeTestRule.onNodeWithText("100").performScrollTo().performTextReplacement("250")

        assertEquals(listOf(DiaryRecipeEditAction.OnIngredientRawWeightChange(0, "250")), actions)
    }

    @Test
    fun errorsAreRendered() {
        setContent(
            state(
                ingredientError =
                    UiText.DynamicString(
                        "Zutat braucht Rohgewicht oder Anzahl",
                    ),
                amountError =
                    UiText.DynamicString(
                        "Menge muss größer als 0 sein",
                    ),
                saveError =
                    UiText.DynamicString(
                        "Fehler beim Speichern",
                    ),
            ),
        )

        composeTestRule.onNodeWithText("Zutat braucht Rohgewicht oder Anzahl").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Menge muss größer als 0 sein").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Fehler beim Speichern").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saveButtonDispatchesSaveClick() {
        setContent(state())

        composeTestRule.onNodeWithText("Speichern").performScrollTo().performClick()

        assertTrue(actions.contains(DiaryRecipeEditAction.OnSaveClick))
    }

    @Test
    fun scopeDialog_defaultsToEntryOnly_andConfirmsWithOk() {
        setContent(state(isSaveScopeDialogOpen = true))

        composeTestRule.onNodeWithText("Übernehmen").performClick()

        assertEquals(
            listOf(DiaryRecipeEditAction.OnConfirmSave(DiaryRecipeEditScope.ENTRY_ONLY)),
            actions,
        )
    }

    @Test
    fun scopeDialog_selectingRecipeScope_confirmsWithRecipeScope() {
        setContent(state(isSaveScopeDialogOpen = true))

        composeTestRule.onNodeWithText("Rezept dauerhaft ändern").performClick()
        composeTestRule.onNodeWithText("Übernehmen").performClick()

        assertEquals(
            listOf(DiaryRecipeEditAction.OnConfirmSave(DiaryRecipeEditScope.RECIPE_AND_ENTRY)),
            actions,
        )
    }

    @Test
    fun scopeDialog_recipeOptionDisabled_whenSourceRecipeIsGone() {
        setContent(state(canUpdateRecipe = false, isSaveScopeDialogOpen = true))

        composeTestRule.onNodeWithText("Rezept dauerhaft ändern").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Nur für diesen Tag").assertIsEnabled()
        composeTestRule.onNodeWithText("Übernehmen").assertIsEnabled()
    }

    @Test
    fun scopeDialog_cancelDispatchesDismiss() {
        setContent(state(isSaveScopeDialogOpen = true))

        composeTestRule.onNodeWithText("Abbrechen").performClick()

        assertEquals(listOf(DiaryRecipeEditAction.OnDismissSaveScopeDialog), actions)
    }

    @Test
    fun amountModeButtonDispatchesGramsMode() {
        setContent(state())

        composeTestRule.onAllNodes(hasText("Gramm") and hasClickAction())[0].performScrollTo().performClick()

        assertTrue(actions.contains(DiaryRecipeEditAction.OnAmountModeChange(DiaryAmountMode.GRAMS)))
    }

    @Test
    fun activeAmountModeButtonIsDisabled() {
        setContent(state())

        composeTestRule
            .onAllNodes(hasText("Portionen") and hasClickAction())[0]
            .performScrollTo()
            .assertIsNotEnabled()
    }
}
