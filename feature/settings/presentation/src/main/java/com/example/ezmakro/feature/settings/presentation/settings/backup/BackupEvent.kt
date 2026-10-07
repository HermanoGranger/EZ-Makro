package com.example.ezmakro.feature.settings.presentation.settings.backup

import com.example.ezmakro.core.presentation.uitext.UiText

sealed interface BackupEvent {
    /** The screen launches the SAF create-document dialog with this file name. */
    data class RequestCreateDocument(
        val suggestedFileName: String,
    ) : BackupEvent

    data object RequestOpenDocument : BackupEvent

    data class ShowMessage(
        val message: UiText,
    ) : BackupEvent

    data object NavigateBack : BackupEvent
}
