package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.domain.model.backup.BackupExportSummary
import com.example.ezmakro.core.domain.model.backup.BackupOptions
import com.example.ezmakro.core.domain.model.backup.BackupPreview
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.model.backup.ImportReport
import com.example.ezmakro.core.domain.repository.BackupRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result

/**
 * Domain-facing implementation of [BackupRepository]: the only type the
 * presentation layer sees. It composes the two orchestration steps — [BackupExporter]
 * for writing an archive and [BackupImporter] for previewing/importing one —
 * and answers the stock-count query directly from the local data source.
 */
class RoomBackupRepository(
    private val local: BackupLocalDataSource,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
) : BackupRepository {
    override suspend fun exportTo(
        uri: String,
        options: BackupOptions,
    ): Result<BackupExportSummary, DataError.Local> = exporter.export(uri, options)

    override suspend fun preview(
        uri: String,
        mode: ImportMode,
    ): Result<BackupPreview, DataError.Local> = importer.preview(uri, mode)

    override suspend fun import(
        uri: String,
        mode: ImportMode,
    ): Result<ImportReport, DataError.Local> = importer.import(uri, mode)

    override suspend fun counts(): Result<BackupExportSummary, DataError.Local> = local.countAll()
}
