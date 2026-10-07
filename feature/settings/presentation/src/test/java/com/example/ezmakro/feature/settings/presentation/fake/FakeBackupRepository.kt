package com.example.ezmakro.feature.settings.presentation.fake

import com.example.ezmakro.core.domain.model.backup.BackupExportSummary
import com.example.ezmakro.core.domain.model.backup.BackupOptions
import com.example.ezmakro.core.domain.model.backup.BackupPreview
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.model.backup.ImportReport
import com.example.ezmakro.core.domain.model.backup.TableCounts
import com.example.ezmakro.core.domain.repository.BackupRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [BackupRepository] for the view model tests. Every call can be
 * forced to fail via [error], and [exportGate]/[importGate] park a running
 * call so a test can observe the busy state and prove that a second click
 * while exporting/importing never reaches the repository twice.
 */
class FakeBackupRepository : BackupRepository {
    var error: DataError.Local? = null

    /** When set, `exportTo` suspends on it instead of returning. */
    var exportGate: CompletableDeferred<Unit>? = null

    /** When set, `import` suspends on it instead of returning. */
    var importGate: CompletableDeferred<Unit>? = null

    var exportCallCount = 0
        private set
    var importCallCount = 0
        private set
    var countsCallCount = 0
        private set

    val exportUris = mutableListOf<String>()
    val exportOptions = mutableListOf<BackupOptions>()
    val importModes = mutableListOf<ImportMode>()

    var previewResult: BackupPreview = DEFAULT_PREVIEW
    var report: ImportReport = DEFAULT_REPORT
    var counts: BackupExportSummary = DEFAULT_COUNTS

    override suspend fun exportTo(
        uri: String,
        options: BackupOptions,
    ): Result<BackupExportSummary, DataError.Local> {
        exportCallCount++
        exportUris += uri
        exportOptions += options
        exportGate?.await()
        error?.let { return Result.Error(it) }
        return Result.Success(counts)
    }

    override suspend fun preview(uri: String): Result<BackupPreview, DataError.Local> {
        error?.let { return Result.Error(it) }
        return Result.Success(previewResult)
    }

    override suspend fun import(
        uri: String,
        mode: ImportMode,
    ): Result<ImportReport, DataError.Local> {
        importCallCount++
        importModes += mode
        importGate?.await()
        error?.let { return Result.Error(it) }
        return Result.Success(report)
    }

    override suspend fun counts(): Result<BackupExportSummary, DataError.Local> {
        countsCallCount++
        error?.let { return Result.Error(it) }
        return Result.Success(counts)
    }

    companion object {
        val DEFAULT_COUNTS =
            BackupExportSummary(
                foodItemCount = 10,
                recipeCount = 3,
                diaryEntryCount = 5,
                weightLogCount = 2,
                settingCount = 5,
            )

        val DEFAULT_PREVIEW =
            BackupPreview(
                formatVersion = 1,
                appVersionName = "1.0",
                exportedAtEpochMillis = 0L,
                foodItemCount = 10,
                recipeCount = 3,
                diaryEntryCount = 5,
                weightLogCount = 2,
                earliestDate = null,
                latestDate = null,
                warnings = emptyList(),
            )

        val DEFAULT_REPORT =
            ImportReport(
                mode = ImportMode.MERGE,
                inserted = TableCounts(1, 1, 2, 1, 0),
                skippedDuplicates = TableCounts(0, 0, 0, 0, 0),
                skippedRows = emptyList(),
                droppedReferences = 0,
                settingsRestored = 5,
                warnings = emptyList(),
            )
    }
}
