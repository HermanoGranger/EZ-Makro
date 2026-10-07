package com.example.ezmakro.feature.settings.presentation.settings.backup

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.feature.settings.presentation.R
import com.example.ezmakro.feature.settings.presentation.fake.FakeBackupRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixedClockMillis = 1_773_000_000_000L
    private val uri = "content://documents/backup.json"
    private lateinit var repository: FakeBackupRepository
    private lateinit var viewModel: BackupViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeBackupRepository()
        viewModel = BackupViewModel(repository, clock = { fixedClockMillis })
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads stock counts`() =
        runTest {
            val state = viewModel.state.value
            assertThat(state.foodItemCount).isEqualTo(FakeBackupRepository.DEFAULT_COUNTS.foodItemCount)
            assertThat(state.recipeCount).isEqualTo(FakeBackupRepository.DEFAULT_COUNTS.recipeCount)
            assertThat(state.diaryEntryCount).isEqualTo(FakeBackupRepository.DEFAULT_COUNTS.diaryEntryCount)
            assertThat(state.weightLogCount).isEqualTo(FakeBackupRepository.DEFAULT_COUNTS.weightLogCount)
            assertThat(repository.countsCallCount).isEqualTo(1)
        }

    @Test
    fun `OnExportClick emits RequestCreateDocument with deterministic file name`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(BackupAction.OnExportClick)

                val event = awaitItem()
                assertThat(event).isEqualTo(
                    BackupEvent.RequestCreateDocument(expectedFileName()),
                )
            }
        }

    @Test
    fun `OnDocumentCreated with null does not call the repository and emits nothing`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(BackupAction.OnDocumentCreated(null))

                assertThat(repository.exportCallCount).isEqualTo(0)
                assertThat(viewModel.state.value.isExporting).isFalse()
                expectNoEvents()
            }
        }

    @Test
    fun `OnDocumentCreated export success clears the flag and shows the success message`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(BackupAction.OnDocumentCreated(uri))

                assertThat(awaitItem()).isEqualTo(
                    BackupEvent.ShowMessage(UiText.StringResource(R.string.backup_export_success)),
                )
            }
            assertThat(viewModel.state.value.isExporting).isFalse()
            assertThat(repository.exportUris).containsExactly(uri)
        }

    @Test
    fun `OnDocumentCreated export error shows the mapped message and does not abort`() =
        runTest {
            repository.error = DataError.Local.INVALID_BACKUP

            viewModel.events.test {
                viewModel.onAction(BackupAction.OnDocumentCreated(uri))

                assertThat(awaitItem()).isEqualTo(
                    BackupEvent.ShowMessage(
                        UiText.StringResource(com.example.ezmakro.core.presentation.R.string.error_invalid_backup),
                    ),
                )
            }
            assertThat(viewModel.state.value.isExporting).isFalse()
            assertThat(repository.exportCallCount).isEqualTo(1)
        }

    @Test
    fun `OnBackupSelected shows the preview and confirm imports with MERGE`() =
        runTest {
            viewModel.onAction(BackupAction.OnBackupSelected(uri))
            assertThat(viewModel.state.value.preview).isEqualTo(repository.previewResult)

            viewModel.onAction(BackupAction.OnConfirmImportClick)

            assertThat(repository.importCallCount).isEqualTo(1)
            assertThat(repository.importModes).containsExactly(ImportMode.MERGE)
            assertThat(viewModel.state.value.importReport).isEqualTo(repository.report)
            assertThat(viewModel.state.value.preview).isNull()
            assertThat(viewModel.state.value.isImporting).isFalse()
        }

    @Test
    fun `OnBackupSelected with null keeps the preview empty and emits nothing`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(BackupAction.OnBackupSelected(null))

                assertThat(viewModel.state.value.preview).isNull()
                assertThat(repository.importCallCount).isEqualTo(0)
                expectNoEvents()
            }
        }

    @Test
    fun `stock counts are reloaded after a successful import`() =
        runTest {
            viewModel.onAction(BackupAction.OnBackupSelected(uri))
            assertThat(repository.countsCallCount).isEqualTo(1)

            viewModel.onAction(BackupAction.OnConfirmImportClick)

            assertThat(repository.countsCallCount).isEqualTo(2)
        }

    @Test
    fun `OnModeChange REPLACE is passed through to the import`() =
        runTest {
            viewModel.onAction(BackupAction.OnBackupSelected(uri))
            viewModel.onAction(BackupAction.OnModeChange(ImportMode.REPLACE))

            viewModel.onAction(BackupAction.OnConfirmImportClick)

            assertThat(repository.importModes).containsExactly(ImportMode.REPLACE)
        }

    @Test
    fun `OnDismissPreviewClick clears the preview without importing`() =
        runTest {
            viewModel.onAction(BackupAction.OnBackupSelected(uri))

            viewModel.onAction(BackupAction.OnDismissPreviewClick)

            assertThat(viewModel.state.value.preview).isNull()
            assertThat(repository.importCallCount).isEqualTo(0)
        }

    @Test
    fun `OnBackClick emits NavigateBack`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(BackupAction.OnBackClick)

                assertThat(awaitItem()).isEqualTo(BackupEvent.NavigateBack)
            }
        }

    @Test
    fun `second click while exporting does not reach the repository again`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.exportGate = gate

            viewModel.onAction(BackupAction.OnDocumentCreated(uri))
            assertThat(viewModel.state.value.isExporting).isTrue()

            viewModel.onAction(BackupAction.OnDocumentCreated(uri))
            assertThat(repository.exportCallCount).isEqualTo(1)

            gate.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.state.value.isExporting).isFalse()
        }

    @Test
    fun `second click while importing does not reach the repository again`() =
        runTest {
            viewModel.onAction(BackupAction.OnBackupSelected(uri))
            val gate = CompletableDeferred<Unit>()
            repository.importGate = gate

            viewModel.onAction(BackupAction.OnConfirmImportClick)
            assertThat(viewModel.state.value.isImporting).isTrue()

            viewModel.onAction(BackupAction.OnConfirmImportClick)
            assertThat(repository.importCallCount).isEqualTo(1)

            gate.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.state.value.isImporting).isFalse()
            assertThat(viewModel.state.value.importReport).isEqualTo(repository.report)
        }

    @Test
    fun `includeCachedItems switch value reaches BackupOptions`() =
        runTest {
            viewModel.onAction(BackupAction.OnIncludeCachedItemsChange(true))
            viewModel.onAction(BackupAction.OnDocumentCreated(uri))

            assertThat(repository.exportOptions.single().includeCachedOpenFoodFactsItems).isTrue()
        }

    @Test
    fun `includeCachedItems defaults to false in BackupOptions`() =
        runTest {
            viewModel.onAction(BackupAction.OnDocumentCreated(uri))

            assertThat(repository.exportOptions.single().includeCachedOpenFoodFactsItems).isFalse()
        }

    @Test
    fun `OnModeChange is reflected in the state`() =
        runTest {
            assertThat(viewModel.state.value.pendingMode).isEqualTo(ImportMode.MERGE)

            viewModel.onAction(BackupAction.OnModeChange(ImportMode.REPLACE))

            assertThat(viewModel.state.value.pendingMode).isEqualTo(ImportMode.REPLACE)
        }

    private fun expectedFileName(): String {
        val timestamp =
            Instant
                .ofEpochMilli(fixedClockMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        return "ezmakro-backup-$timestamp.json"
    }
}
