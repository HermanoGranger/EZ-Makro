@file:Suppress("TooManyFunctions")

package com.example.ezmakro.feature.settings.presentation.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.designsystem.component.LoadingIndicator
import com.example.ezmakro.core.domain.model.backup.BackupPreview
import com.example.ezmakro.core.domain.model.backup.BackupWarning
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.model.backup.ImportReport
import com.example.ezmakro.core.domain.model.backup.TableCounts
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.settings.presentation.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SUPPORTED_IMPORT_MIME_TYPES =
    arrayOf("application/json", "text/plain", "application/octet-stream")

private val EXPORTED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * Cards, dialogs and the SAF launchers of the backup screen.
 *
 * The launchers live here (and not in the ViewModel) because the screen
 * requests them via [BackupEvent] and reports the result per
 * [BackupAction] — the only Android interaction in the presentation layer.
 */
@Composable
fun BackupScreenContent(
    state: BackupState,
    onAction: (BackupAction) -> Unit,
    events: Flow<BackupEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarScope = rememberCoroutineScope()

    val createDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            onAction(BackupAction.OnDocumentCreated(uri?.toString()))
        }
    val openDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onAction(BackupAction.OnBackupSelected(uri?.toString()))
        }

    ObserveAsEvents(events) { event ->
        when (event) {
            is BackupEvent.RequestCreateDocument -> createDocument.launch(event.suggestedFileName)
            BackupEvent.RequestOpenDocument -> openDocument.launch(SUPPORTED_IMPORT_MIME_TYPES)
            is BackupEvent.ShowMessage -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarScope.launch { snackbarHostState.showSnackbar(event.message.asString(context)) }
            }
            BackupEvent.NavigateBack -> onNavigateBack()
        }
    }

    var showReplaceConfirm by remember { mutableStateOf(false) }

    BackupCardColumn(state = state, onAction = onAction, modifier = modifier)

    state.preview?.let { preview ->
        PreviewDialog(
            preview = preview,
            pendingMode = state.pendingMode,
            isImporting = state.isImporting,
            onAction = onAction,
            onConfirmImport = {
                if (state.pendingMode == ImportMode.REPLACE) {
                    showReplaceConfirm = true
                } else {
                    onAction(BackupAction.OnConfirmImportClick)
                }
            },
        )
    }

    if (showReplaceConfirm && state.preview != null) {
        ReplaceConfirmDialog(
            onConfirm = {
                showReplaceConfirm = false
                onAction(BackupAction.OnConfirmImportClick)
            },
            onDismiss = { showReplaceConfirm = false },
        )
    }

    state.importReport?.let { report ->
        ReportDialog(report = report, onAction = onAction)
    }
}

@Composable
private fun BackupCardColumn(
    state: BackupState,
    onAction: (BackupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExportCard(state = state, onAction = onAction)
        ImportCard(state = state, onAction = onAction)
        if (state.isExporting || state.isImporting) {
            LoadingIndicator(modifier = Modifier.height(72.dp))
            Text(
                stringResource(R.string.backup_working),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExportCard(
    state: BackupState,
    onAction: (BackupAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.backup_export_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                countsLine(
                    foods = state.foodItemCount,
                    recipes = state.recipeCount,
                    days = state.diaryEntryCount,
                    weightLogs = state.weightLogCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = state.includeCachedItems,
                    onCheckedChange = { onAction(BackupAction.OnIncludeCachedItemsChange(it)) },
                )
                Text(
                    stringResource(R.string.backup_include_cached),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = { onAction(BackupAction.OnExportClick) },
                enabled = !state.isExporting && !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_create))
            }
        }
    }
}

@Composable
private fun ImportCard(
    state: BackupState,
    onAction: (BackupAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.backup_import_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = { onAction(BackupAction.OnImportClick) },
                enabled = !state.isExporting && !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_open))
            }
        }
    }
}

@Composable
private fun PreviewDialog(
    preview: BackupPreview,
    pendingMode: ImportMode,
    isImporting: Boolean,
    onAction: (BackupAction) -> Unit,
    onConfirmImport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(BackupAction.OnDismissPreviewClick) },
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_exported_at, preview.formattedExportedAt()))
                Text(stringResource(R.string.backup_app_version, preview.appVersionName))
                Text(stringResource(R.string.backup_format_version, preview.formatVersion))
                Text(
                    countsLine(
                        foods = preview.foodItemCount,
                        recipes = preview.recipeCount,
                        days = preview.diaryEntryCount,
                        weightLogs = preview.weightLogCount,
                    ),
                )
                dateRangeText(preview = preview)?.let { Text(it) }
                WarningList(warnings = preview.warnings)
                ModeRow(mode = ImportMode.MERGE, selectedMode = pendingMode, onAction = onAction)
                ModeRow(mode = ImportMode.REPLACE, selectedMode = pendingMode, onAction = onAction)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmImport, enabled = !isImporting) {
                Text(stringResource(R.string.backup_confirm_import))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(BackupAction.OnDismissPreviewClick) }) {
                Text(stringResource(R.string.backup_cancel))
            }
        },
    )
}

/**
 * The REPLACE choice is a pure UI derivation of `pendingMode` — no second
 * confirm action in the ViewModel; the extra confirmation dialog lives in the
 * screen (A6).
 */
@Composable
private fun ReplaceConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_mode_replace)) },
        text = { Text(stringResource(R.string.backup_mode_replace_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_confirm_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_cancel))
            }
        },
    )
}

@Composable
private fun ReportDialog(
    report: ImportReport,
    onAction: (BackupAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(BackupAction.OnDismissReportClick) },
        title = { Text(stringResource(R.string.backup_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.backup_report_inserted, report.inserted.total()))
                Text(stringResource(R.string.backup_report_duplicates, report.skippedDuplicates.total()))
                Text(stringResource(R.string.backup_report_skipped, report.skippedRows.size))
                Text(stringResource(R.string.backup_report_dropped_references, report.droppedReferences))
                if (report.settingsRestored > 0) {
                    Text(stringResource(R.string.backup_report_settings_restored, report.settingsRestored))
                }
                WarningList(warnings = report.warnings)
                report.skippedRows.forEach { row ->
                    Text(stringResource(R.string.backup_report_skipped_row, row.table, row.id, row.reason))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(BackupAction.OnDismissReportClick) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun ModeRow(
    mode: ImportMode,
    selectedMode: ImportMode,
    onAction: (BackupAction) -> Unit,
) {
    val title: String
    val hint: String
    when (mode) {
        ImportMode.MERGE -> {
            title = stringResource(R.string.backup_mode_merge)
            hint = stringResource(R.string.backup_mode_merge_hint)
        }
        ImportMode.REPLACE -> {
            title = stringResource(R.string.backup_mode_replace)
            hint = stringResource(R.string.backup_mode_replace_warning)
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = selectedMode == mode,
                    role = Role.RadioButton,
                    onValueChange = { onAction(BackupAction.OnModeChange(mode)) },
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selectedMode == mode, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WarningList(warnings: List<BackupWarning>) {
    warnings.forEach { warning ->
        when (warning) {
            BackupWarning.ChecksumMismatch -> Text(stringResource(R.string.backup_warning_checksum))
            is BackupWarning.InvalidRow ->
                Text(
                    stringResource(
                        R.string.backup_warning_invalid_row,
                        warning.table,
                        warning.id,
                        warning.reason,
                    ),
                )
            BackupWarning.SettingsRestoreFailed -> Text(stringResource(R.string.backup_warning_settings))
        }
    }
}

@Composable
private fun countsLine(
    foods: Int,
    recipes: Int,
    days: Int,
    weightLogs: Int,
): String =
    listOf(
        stringResource(R.string.backup_count_foods, foods),
        stringResource(R.string.backup_count_recipes, recipes),
        stringResource(R.string.backup_count_days, days),
        stringResource(R.string.backup_count_weight_logs, weightLogs),
    ).joinToString(" · ")

@Composable
private fun dateRangeText(preview: BackupPreview): String? {
    val earliest = preview.earliestDate
    val latest = preview.latestDate
    return if (earliest != null && latest != null) {
        stringResource(
            R.string.backup_date_range,
            earliest.format(DATE_FORMAT),
            latest.format(DATE_FORMAT),
        )
    } else {
        null
    }
}

private fun BackupPreview.formattedExportedAt(): String =
    Instant
        .ofEpochMilli(exportedAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .format(EXPORTED_AT_FORMAT)

private fun TableCounts.total(): Int = foodItems + recipes + recipeIngredients + diaryEntries + weightLogs

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun BackupScreenContentPreview() {
    MaterialTheme {
        BackupScreenContent(
            state =
                BackupState(
                    foodItemCount = 1234,
                    recipeCount = 12,
                    diaryEntryCount = 480,
                    weightLogCount = 30,
                ),
            onAction = {},
            events = emptyFlow(),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
        )
    }
}
