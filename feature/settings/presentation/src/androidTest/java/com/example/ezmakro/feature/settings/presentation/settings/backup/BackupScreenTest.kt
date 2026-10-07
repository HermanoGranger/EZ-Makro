package com.example.ezmakro.feature.settings.presentation.settings.backup

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ezmakro.core.domain.model.backup.BackupPreview
import com.example.ezmakro.core.domain.model.backup.BackupWarning
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.model.backup.ImportReport
import com.example.ezmakro.core.domain.model.backup.SkippedRow
import com.example.ezmakro.core.domain.model.backup.TableCounts
import com.example.ezmakro.core.presentation.uitext.UiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Compose tests of the visible backup states (A-plan §3.2): idle/exporting/
 * importing cards, preview dialog, REPLACE second confirmation, report dialog
 * with the four separate counters and the one-shot events. The SAF roundtrip
 * itself cannot be instrumented meaningfully.
 */
class BackupScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val actions = mutableListOf<BackupAction>()

    private val confirmImport = hasText("Importieren") and hasClickAction()

    private val replaceWarning =
        "Alle lokal gespeicherten Lebensmittel, Rezepte, Tagebucheinträge und Gewichtsdaten " +
            "werden gelöscht."

    private fun setScreen(
        state: BackupState = BackupState(),
        events: Flow<BackupEvent> = emptyFlow(),
        onNavigateBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BackupScreen(
                state = state,
                onAction = { actions += it },
                events = events,
                snackbarHostState = SnackbarHostState(),
                onNavigateBack = onNavigateBack,
            )
        }
    }

    private fun samplePreview() =
        BackupPreview(
            formatVersion = 1,
            appVersionName = "1.0",
            exportedAtEpochMillis = 0,
            foodItemCount = 7,
            recipeCount = 1,
            diaryEntryCount = 2,
            weightLogCount = 0,
            earliestDate = LocalDate.of(2026, 1, 1),
            latestDate = LocalDate.of(2026, 1, 31),
            warnings = listOf(BackupWarning.ChecksumMismatch),
        )

    private fun sampleReport() =
        ImportReport(
            mode = ImportMode.MERGE,
            inserted = TableCounts(foodItems = 2, recipes = 1, recipeIngredients = 0, diaryEntries = 3, weightLogs = 1),
            skippedDuplicates =
                TableCounts(
                    foodItems = 1,
                    recipes = 0,
                    recipeIngredients = 0,
                    diaryEntries = 0,
                    weightLogs = 0,
                ),
            skippedRows =
                listOf(
                    SkippedRow(table = "food_items", id = "f1", reason = "kcal fehlt"),
                    SkippedRow(table = "recipes", id = "r1", reason = "Name fehlt"),
                ),
            droppedReferences = 4,
            settingsRestored = 1,
            warnings = listOf(BackupWarning.InvalidRow(table = "food_items", id = "f1", reason = "kcal fehlt")),
        )

    private fun exportedAt(): String =
        "Exportiert: " +
            Instant
                .ofEpochMilli(0)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))

    private fun okLabel(): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(android.R.string.ok)

    @Test
    fun idleState_showsCountsAndEnabledButtons() {
        setScreen(BackupState(foodItemCount = 10, recipeCount = 3, diaryEntryCount = 5, weightLogCount = 2))

        composeTestRule.onNodeWithText("10 Lebensmittel · 3 Rezepte · 5 Tage · 2 Gewichtseinträge").assertExists()
        composeTestRule.onNodeWithText("Backup erstellen").assertIsEnabled()
        composeTestRule.onNodeWithText("Backup einspielen").assertIsEnabled()
        composeTestRule.onNodeWithText("Bitte warten…").assertDoesNotExist()
    }

    @Test
    fun exportingState_disablesButtonsAndShowsWorkingHint() {
        setScreen(BackupState(isExporting = true))

        composeTestRule.onNodeWithText("Backup erstellen").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Backup einspielen").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Bitte warten…").assertExists()
    }

    @Test
    fun importingState_disablesButtonsAndShowsWorkingHint() {
        setScreen(BackupState(isImporting = true))

        composeTestRule.onNodeWithText("Backup erstellen").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Backup einspielen").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Bitte warten…").assertExists()
    }

    @Test
    fun switchClick_dispatchesIncludeCachedItemsChange() {
        setScreen()

        composeTestRule.onNode(isToggleable()).performClick()

        assertEquals(BackupAction.OnIncludeCachedItemsChange(true), actions.last())
    }

    @Test
    fun exportButton_dispatchesOnExportClick() {
        setScreen()

        composeTestRule.onNodeWithText("Backup erstellen").performClick()

        assertEquals(BackupAction.OnExportClick, actions.last())
    }

    @Test
    fun importButton_dispatchesOnImportClick() {
        setScreen()

        composeTestRule.onNodeWithText("Backup einspielen").performClick()

        assertEquals(BackupAction.OnImportClick, actions.last())
    }

    @Test
    fun backButton_dispatchesOnBackClick() {
        setScreen()

        composeTestRule.onNodeWithContentDescription("Zurück").performClick()

        assertEquals(BackupAction.OnBackClick, actions.last())
    }

    @Test
    fun previewDialog_showsMetadataRangeWarningsAndModes() {
        val state = BackupState(foodItemCount = 10, recipeCount = 3, diaryEntryCount = 5, weightLogCount = 2)
        setScreen(state.copy(preview = samplePreview()))

        composeTestRule.onNodeWithText(exportedAt()).assertExists()
        composeTestRule.onNodeWithText("App-Version: 1.0").assertExists()
        composeTestRule.onNodeWithText("Format-Version: 1").assertExists()
        composeTestRule.onNodeWithText("7 Lebensmittel · 1 Rezepte · 2 Tage · 0 Gewichtseinträge").assertExists()
        composeTestRule.onNodeWithText("Zeitraum: 01.01.2026 – 31.01.2026").assertExists()
        composeTestRule.onNodeWithText("Prüfsumme fehlt oder stimmt nicht überein").assertExists()
        composeTestRule.onNodeWithText("Ergänzen").assertExists()
        composeTestRule.onNodeWithText("Ersetzen").assertExists()
        composeTestRule.onNodeWithText("Abbrechen").assertExists()
    }

    @Test
    fun previewDialog_replaceModeRow_dispatchesModeChange() {
        setScreen(BackupState(preview = samplePreview()))

        composeTestRule.onNodeWithText("Ersetzen").performClick()

        assertEquals(BackupAction.OnModeChange(ImportMode.REPLACE), actions.last())
    }

    @Test
    fun previewDialog_mergeMode_confirmDispatchesImmediately() {
        setScreen(BackupState(preview = samplePreview()))

        composeTestRule.onNode(confirmImport).performClick()

        assertEquals(BackupAction.OnConfirmImportClick, actions.last())
        composeTestRule.onAllNodesWithText(replaceWarning).assertCountEquals(1)
    }

    @Test
    fun previewDialog_replaceMode_requiresSecondConfirmation() {
        setScreen(BackupState(pendingMode = ImportMode.REPLACE, preview = samplePreview()))

        composeTestRule.onAllNodes(confirmImport)[0].performClick()

        composeTestRule.onAllNodesWithText(replaceWarning).assertCountEquals(2)
        assertNull(actions.lastOrNull())

        composeTestRule.onAllNodes(confirmImport)[1].performClick()

        assertEquals(BackupAction.OnConfirmImportClick, actions.last())
    }

    @Test
    fun previewDialog_cancelButton_dispatchesDismissPreview() {
        setScreen(BackupState(preview = samplePreview()))

        composeTestRule.onNodeWithText("Abbrechen").performClick()

        assertEquals(BackupAction.OnDismissPreviewClick, actions.last())
    }

    @Test
    fun reportDialog_showsAllCountersSeparately() {
        setScreen(BackupState(importReport = sampleReport()))

        composeTestRule.onNodeWithText("Import abgeschlossen").assertExists()
        composeTestRule.onNodeWithText("Eingefügt: 7").assertExists()
        composeTestRule.onNodeWithText("Duplikate übersprungen: 1").assertExists()
        composeTestRule.onNodeWithText("Ungültige Zeilen: 2").assertExists()
        composeTestRule.onNodeWithText("Verworfene Referenzen: 4").assertExists()
        composeTestRule.onNodeWithText("Zeile übersprungen: food_items/f1 – kcal fehlt").assertExists()
        composeTestRule.onNodeWithText("food_items/f1: kcal fehlt").assertExists()
        composeTestRule.onNodeWithText("recipes/r1: Name fehlt").assertExists()
    }

    @Test
    fun reportDialog_okButton_dispatchesDismissReport() {
        setScreen(BackupState(importReport = sampleReport()))

        composeTestRule.onNodeWithText(okLabel()).performClick()

        assertEquals(BackupAction.OnDismissReportClick, actions.last())
    }

    @Test
    fun navigateBackEvent_invokesNavigationCallback() {
        val events = MutableSharedFlow<BackupEvent>(replay = 1)
        events.tryEmit(BackupEvent.NavigateBack)
        var navigated = false

        setScreen(events = events, onNavigateBack = { navigated = true })

        composeTestRule.waitUntil(5_000) { navigated }
        assertEquals(true, navigated)
    }

    @Test
    fun showMessageEvent_isShownInSnackbar() {
        val events = MutableSharedFlow<BackupEvent>(replay = 1)
        events.tryEmit(BackupEvent.ShowMessage(UiText.DynamicString("Backup wurde erstellt")))

        setScreen(events = events)

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithText("Backup wurde erstellt")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
