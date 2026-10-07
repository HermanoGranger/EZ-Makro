package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.data.datastore.SettingsDataStore
import com.example.ezmakro.core.data.food.mapper.toServingUnit
import com.example.ezmakro.core.data.logging.AppLogger
import com.example.ezmakro.core.database.dao.BackupDao
import com.example.ezmakro.core.database.dao.BackupInsertReport
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.model.backup.BackupExportSummary
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room/DataStore implementation of the narrow [BackupLocalDataSource] window:
 * the five backup tables plus the generic settings snapshot (A10).
 *
 * Every call runs on [Dispatchers.IO], forwards cancellation untouched and
 * maps unexpected failures to `DataError.Local.UNKNOWN` — the same pattern as
 * `RoomDiaryLocalDataSource.runCatchingLocal`, copied here so no existing file
 * outside the plan's file list has to change.
 */
class RoomBackupLocalDataSource(
    private val dao: BackupDao,
    private val settings: SettingsDataStore,
) : BackupLocalDataSource {
    override suspend fun readAll(): Result<BackupRows, DataError.Local> =
        runCatchingLocal {
            BackupRows(
                foods = dao.readFoodItems(),
                recipes = dao.readRecipes(),
                ingredients = dao.readRecipeIngredients(),
                diaryEntries = dao.readDiaryEntries(),
                weightLogs = dao.readWeightLogs(),
            )
        }

    override suspend fun insertAll(rows: BackupRows): Result<BackupInsertReport, DataError.Local> =
        runCatchingLocal {
            dao.insertAll(rows.foods, rows.recipes, rows.ingredients, rows.diaryEntries, rows.weightLogs)
        }

    override suspend fun replaceAll(rows: BackupRows): Result<BackupInsertReport, DataError.Local> =
        runCatchingLocal {
            dao.replaceAll(rows.foods, rows.recipes, rows.ingredients, rows.diaryEntries, rows.weightLogs)
        }

    override suspend fun readFoodServingUnits(): Result<Map<String, ServingUnit?>, DataError.Local> =
        runCatchingLocal {
            dao
                .readFoodServingUnits()
                .associate { row -> row.id to toServingUnit(row.servingLabel, row.servingGramsEquivalent) }
        }

    override suspend fun readRecipeIds(): Result<Set<String>, DataError.Local> =
        runCatchingLocal { dao.readRecipeIds().toSet() }

    override suspend fun countAll(): Result<BackupExportSummary, DataError.Local> =
        runCatchingLocal {
            BackupExportSummary(
                foodItemCount = dao.countFoodItems(),
                recipeCount = dao.countRecipes(),
                diaryEntryCount = dao.countDiaryEntries(),
                weightLogCount = dao.countWeightLogs(),
                settingCount = settings.snapshot().size,
            )
        }

    override suspend fun readSettings(): Result<Map<String, String>, DataError.Local> =
        runCatchingLocal {
            settings.snapshot()
        }

    override suspend fun restoreSettings(values: Map<String, String>): EmptyResult<DataError.Local> =
        runCatchingLocal { settings.restore(values) }
}

/**
 * Maps failures to a typed [DataError.Local] and preserves cancellation —
 * the `runCatchingLocal` pattern of the other local data sources, with one
 * difference: the block is a plain `suspend () -> T` instead of a `crossinline`
 * inline lambda, because every [BackupDao] method is itself `suspend` (the
 * older DAOs are synchronous, which is why their copy of this helper can stay
 * inline).
 */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> runCatchingLocal(block: suspend () -> T): Result<T, DataError.Local> =
    withContext(Dispatchers.IO) {
        try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("Backup local data error", e, tag = "RoomBackupLocalDataSource")
            Result.Error(ioErrorToDataError(e))
        }
    }
