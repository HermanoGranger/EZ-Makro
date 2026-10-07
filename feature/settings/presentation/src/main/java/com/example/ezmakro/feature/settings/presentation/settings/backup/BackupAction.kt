package com.example.ezmakro.feature.settings.presentation.settings.backup

import com.example.ezmakro.core.domain.model.backup.ImportMode

sealed interface BackupAction {
    data class OnIncludeCachedItemsChange(
        val include: Boolean,
    ) : BackupAction

    data object OnExportClick : BackupAction

    /** SAF result of the create-document dialog; `null` means the user cancelled. */
    data class OnDocumentCreated(
        val uri: String?,
    ) : BackupAction

    data object OnImportClick : BackupAction

    /** SAF result of the open-document dialog; `null` means the user cancelled. */
    data class OnBackupSelected(
        val uri: String?,
    ) : BackupAction

    data class OnModeChange(
        val mode: ImportMode,
    ) : BackupAction

    data object OnConfirmImportClick : BackupAction

    data object OnDismissPreviewClick : BackupAction

    data object OnDismissReportClick : BackupAction

    data object OnBackClick : BackupAction
}
