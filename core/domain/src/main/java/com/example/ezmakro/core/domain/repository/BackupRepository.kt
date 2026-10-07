package com.example.ezmakro.core.domain.repository

import com.example.ezmakro.core.domain.model.backup.BackupExportSummary
import com.example.ezmakro.core.domain.model.backup.BackupOptions
import com.example.ezmakro.core.domain.model.backup.BackupPreview
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.model.backup.ImportReport
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result

/**
 * Full backup lifecycle: write an archive to a SAF document, read it back as a
 * dry-run preview and import it in one of the two [ImportMode]s.
 *
 * The contract works on a plain `uri: String` instead of an android.net.Uri so
 * `:core:domain` stays free of Android types. Every operation is a typed
 * `Result` — a failing backup never throws into the presentation layer.
 */
interface BackupRepository {
    /** Writes a backup to [uri] and reports what was actually written. */
    suspend fun exportTo(
        uri: String,
        options: BackupOptions = BackupOptions(),
    ): Result<BackupExportSummary, DataError.Local>

    /**
     * Reads [uri] without writing anything: counts, date span, warnings.
     *
     * [mode] decides which known-id set the row validation resolves against
     * (A19), so switching the mode in the preview dialog can re-run this dry
     * run and show mode-specific warnings.
     */
    suspend fun preview(
        uri: String,
        mode: ImportMode,
    ): Result<BackupPreview, DataError.Local>

    /** Validates and imports [uri] in the given [mode]; settings follow after the commit. */
    suspend fun import(
        uri: String,
        mode: ImportMode,
    ): Result<ImportReport, DataError.Local>

    /** Current stock counts for the settings screen (a one-shot read, not a Flow). */
    suspend fun counts(): Result<BackupExportSummary, DataError.Local>
}
