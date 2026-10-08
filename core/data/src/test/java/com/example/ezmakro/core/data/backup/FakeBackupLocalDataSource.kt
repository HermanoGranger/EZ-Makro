package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.database.dao.BackupInsertReport
import com.example.ezmakro.core.database.dao.TableInsertCounts
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.model.backup.BackupExportSummary
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result

/**
 * Hand-written [BackupLocalDataSource] so the exporter and the importer can be
 * tested without Room (WP3 Vorgaben: the interface exists exactly for this).
 *
 * The insert/replace counters record which path the importer chose; [insertError]
 * lets a test simulate a failing transaction to prove that settings are only
 * restored after a successful commit (A11), and [restoreSettingsError] simulates
 * a DataStore failure *after* the commit (§3.4 — tolerated, reported as warning).
 */
class FakeBackupLocalDataSource(
    var rows: BackupRows = BackupRows(),
    var settings: Map<String, String> = emptyMap(),
    var existingFoodServingUnits: Map<String, ServingUnit?> = emptyMap(),
    var existingRecipeIds: Set<String> = emptySet(),
) : BackupLocalDataSource {
    var insertAllCalls = 0
        private set
    var replaceAllCalls = 0
        private set
    var restoredSettings: Map<String, String>? = null
        private set
    var insertError: DataError.Local? = null
    var restoreSettingsError: DataError.Local? = null
    var clearSeedVersionMarkerCalls = 0
        private set
    var clearSeedVersionMarkerError: DataError.Local? = null

    override suspend fun readAll(): Result<BackupRows, DataError.Local> = Result.Success(rows)

    override suspend fun insertAll(rows: BackupRows): Result<BackupInsertReport, DataError.Local> {
        insertAllCalls++
        insertError?.let { return Result.Error(it) }
        this.rows =
            BackupRows(
                foods = this.rows.foods + rows.foods,
                recipes = this.rows.recipes + rows.recipes,
                ingredients = this.rows.ingredients + rows.ingredients,
                diaryEntries = this.rows.diaryEntries + rows.diaryEntries,
                weightLogs = this.rows.weightLogs + rows.weightLogs,
            )
        return Result.Success(insertReport(rows))
    }

    override suspend fun replaceAll(rows: BackupRows): Result<BackupInsertReport, DataError.Local> {
        replaceAllCalls++
        insertError?.let { return Result.Error(it) }
        this.rows = rows
        return Result.Success(insertReport(rows))
    }

    override suspend fun readFoodServingUnits(): Result<Map<String, ServingUnit?>, DataError.Local> =
        Result.Success(existingFoodServingUnits)

    override suspend fun readRecipeIds(): Result<Set<String>, DataError.Local> = Result.Success(existingRecipeIds)

    override suspend fun countAll(): Result<BackupExportSummary, DataError.Local> =
        Result.Success(
            BackupExportSummary(
                foodItemCount = rows.foods.size,
                recipeCount = rows.recipes.size,
                diaryEntryCount = rows.diaryEntries.size,
                weightLogCount = rows.weightLogs.size,
                settingCount = settings.size,
            ),
        )

    override suspend fun readSettings(): Result<Map<String, String>, DataError.Local> = Result.Success(settings)

    override suspend fun restoreSettings(values: Map<String, String>): Result<Unit, DataError.Local> {
        restoreSettingsError?.let { return Result.Error(it) }
        restoredSettings = values
        return Result.Success(Unit)
    }

    override suspend fun clearSeedVersionMarker(): Result<Unit, DataError.Local> {
        clearSeedVersionMarkerCalls++
        clearSeedVersionMarkerError?.let { return Result.Error(it) }
        return Result.Success(Unit)
    }

    private fun insertReport(rows: BackupRows): BackupInsertReport =
        BackupInsertReport(
            foodItems = TableInsertCounts.from(List(rows.foods.size) { 1L }),
            recipes = TableInsertCounts.from(List(rows.recipes.size) { 1L }),
            recipeIngredients = TableInsertCounts.from(List(rows.ingredients.size) { 1L }),
            diaryEntries = TableInsertCounts.from(List(rows.diaryEntries.size) { 1L }),
            weightLogs = TableInsertCounts.from(List(rows.weightLogs.size) { 1L }),
        )
}
