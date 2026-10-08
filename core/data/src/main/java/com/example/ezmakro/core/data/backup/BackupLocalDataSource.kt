package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.database.dao.BackupInsertReport
import com.example.ezmakro.core.domain.model.ServingUnit
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

    /**
     * Every existing food id mapped to its serving unit (`null` = the food has
     * none). The stage-4 recipe validation resolves `servingCount` ingredients
     * against exactly these target-DB rows after a MERGE import (W3), so it
     * needs the serving unit, not just the id.
     */
    suspend fun readFoodServingUnits(): Result<Map<String, ServingUnit?>, DataError.Local>

    suspend fun readRecipeIds(): Result<Set<String>, DataError.Local>

    suspend fun countAll(): Result<BackupExportSummary, DataError.Local>

    suspend fun readSettings(): Result<Map<String, String>, DataError.Local>

    suspend fun restoreSettings(values: Map<String, String>): EmptyResult<DataError.Local>

    /**
     * Clears the BLS seed version marker so the next startup re-applies the
     * bundled seed document into the freshly restored database (ADR-008).
     * Called only after a REPLACE import: the archive's own marker value is
     * intentionally discarded, otherwise a same-version archive would leave
     * the restored DB without seeds it never contained.
     */
    suspend fun clearSeedVersionMarker(): EmptyResult<DataError.Local>
}
