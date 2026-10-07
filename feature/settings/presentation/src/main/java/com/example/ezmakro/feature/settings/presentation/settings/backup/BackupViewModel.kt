package com.example.ezmakro.feature.settings.presentation.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.backup.BackupOptions
import com.example.ezmakro.core.domain.repository.BackupRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.core.presentation.uitext.toUiText
import com.example.ezmakro.feature.settings.presentation.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Drives the backup screen: stock counts, SAF export/import and the
 * preview/report dialogs.
 *
 * The ViewModel never touches `android.net.Uri` or `ActivityResultContracts` —
 * URIs arrive as plain strings from the screen and the SAF dialogs are
 * requested via [BackupEvent]s, keeping the class unit-testable.
 */
class BackupViewModel(
    private val repository: BackupRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _state = MutableStateFlow(BackupState())
    val state = _state.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** URI of the backup currently shown in the preview dialog. */
    private var pendingUri: String? = null

    init {
        viewModelScope.launch { loadCounts() }
    }

    fun onAction(action: BackupAction) {
        when (action) {
            is BackupAction.OnIncludeCachedItemsChange ->
                _state.update { it.copy(includeCachedItems = action.include) }

            BackupAction.OnExportClick -> emit(BackupEvent.RequestCreateDocument(backupFileName()))

            is BackupAction.OnDocumentCreated ->
                action.uri?.let(::export)

            BackupAction.OnImportClick -> emit(BackupEvent.RequestOpenDocument)

            is BackupAction.OnBackupSelected ->
                action.uri?.let(::loadPreview)

            is BackupAction.OnModeChange ->
                _state.update { it.copy(pendingMode = action.mode) }

            BackupAction.OnConfirmImportClick -> confirmImport()

            BackupAction.OnDismissPreviewClick ->
                _state.update { it.copy(preview = null) }

            BackupAction.OnDismissReportClick ->
                _state.update { it.copy(importReport = null) }

            BackupAction.OnBackClick -> emit(BackupEvent.NavigateBack)
        }
    }

    private fun export(uri: String) {
        if (_state.value.isExporting) return
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            val options =
                BackupOptions(includeCachedOpenFoodFactsItems = _state.value.includeCachedItems)
            when (val result = repository.exportTo(uri, options)) {
                is Result.Success ->
                    _events.send(BackupEvent.ShowMessage(UiText.StringResource(R.string.backup_export_success)))

                is Result.Error ->
                    _events.send(BackupEvent.ShowMessage(result.error.toUiText()))
            }
            _state.update { it.copy(isExporting = false) }
        }
    }

    private fun loadPreview(uri: String) {
        viewModelScope.launch {
            when (val result = repository.preview(uri)) {
                is Result.Success -> {
                    pendingUri = uri
                    _state.update { it.copy(preview = result.data) }
                }

                is Result.Error ->
                    _events.send(BackupEvent.ShowMessage(result.error.toUiText()))
            }
        }
    }

    private fun confirmImport() {
        val uri = pendingUri ?: return
        if (_state.value.isImporting) return
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }
            when (val result = repository.import(uri, _state.value.pendingMode)) {
                is Result.Success -> {
                    pendingUri = null
                    _state.update { it.copy(preview = null, importReport = result.data) }
                    // Refresh the stock counts, otherwise the screen keeps
                    // showing the pre-import numbers (stale snapshot).
                    loadCounts()
                }

                is Result.Error ->
                    _events.send(BackupEvent.ShowMessage(result.error.toUiText()))
            }
            _state.update { it.copy(isImporting = false) }
        }
    }

    private suspend fun loadCounts() {
        when (val result = repository.counts()) {
            is Result.Success ->
                _state.update {
                    it.copy(
                        foodItemCount = result.data.foodItemCount,
                        recipeCount = result.data.recipeCount,
                        diaryEntryCount = result.data.diaryEntryCount,
                        weightLogCount = result.data.weightLogCount,
                    )
                }

            is Result.Error ->
                _events.send(BackupEvent.ShowMessage(result.error.toUiText()))
        }
    }

    private fun backupFileName(): String {
        val timestamp =
            Instant
                .ofEpochMilli(clock())
                .atZone(ZoneId.systemDefault())
                .format(FILE_NAME_TIMESTAMP_FORMAT)
        return "ezmakro-backup-$timestamp.json"
    }

    private fun emit(event: BackupEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        val FILE_NAME_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    }
}
