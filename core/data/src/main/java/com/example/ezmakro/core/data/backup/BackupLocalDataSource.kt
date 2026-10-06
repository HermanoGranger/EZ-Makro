package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.database.dao.BackupInsertReport
import com.example.ezmakro.core.domain.model.backup.BackupExportSummary
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result

/**
 * Narrow window the backup orchestration has on the local database and the
 * settings store.
 *
 * Deliberately an interface **in `:core:data`** (not in `:core:domain`): it is
 * expressed in Room entities and [BackupInsertReport], neither of which is
 * visible to the domain module. Keeping it here also lets
 * `BackupExporter` / `BackupImporter` be tested against a hand-written fake.
 *
 * Every call is a typed [Result]: an I/O failure surfaces as
 * `DataError.Local` instead of an exception reaching the ViewModel.
 */
interface BackupLocalDataSource {
    suspend fun readAll(): Result<BackupRows, DataError.Local>

    suspend fun insertAll(rows: BackupRows): Result<BackupInsertReport, DataError.Local>

    suspend fun replaceAll(rows: BackupRows): Result<BackupInsertReport, DataError.Local>

    suspend fun readFoodItemIds(): Result<Set<String>, DataError.Local>

    suspend fun readRecipeIds(): Result<Set<String>, DataError.Local>

    suspend fun countAll(): Result<BackupExportSummary, DataError.Local>

    suspend fun readSettings(): Result<Map<String, String>, DataError.Local>

    suspend fun restoreSettings(values: Map<String, String>): EmptyResult<DataError.Local>
}
