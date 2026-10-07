package com.example.ezmakro.core.domain.model.backup

/**
 * Non-fatal problems found while reading or importing a backup (A14, A11).
 *
 * Warnings never abort a run: the user is shown what happened and decides.
 */
sealed interface BackupWarning {
    /** The document's `checksum` is missing or does not match the canonical `content` (A14). */
    data object ChecksumMismatch : BackupWarning

    /** A row failed validation and was left out; mirrored in `ImportReport.skippedRows`. */
    data class InvalidRow(
        val table: String,
        val id: String,
        val reason: String,
    ) : BackupWarning

    /**
     * The database commit succeeded but the settings restore afterwards failed
     * (DataStore lies outside Room, §3.4): the imported rows are valid and
     * committed, `ImportReport.settingsRestored` is 0.
     */
    data object SettingsRestoreFailed : BackupWarning
}
