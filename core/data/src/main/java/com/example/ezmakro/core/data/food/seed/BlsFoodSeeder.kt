package com.example.ezmakro.core.data.food.seed

import com.example.ezmakro.core.data.datastore.SettingsDataStore
import com.example.ezmakro.core.data.logging.AppLogger
import com.example.ezmakro.core.database.dao.FoodItemDao
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * One-shot, idempotent pre-seeding of the bundled BLS vegan food list
 * (ADR-008). Runs at app startup; safe to call on every launch.
 *
 * Contract:
 * - **Versioned:** the applied `seedVersion` is stored under
 *   [SEED_FOOD_VERSION_KEY] in the settings store. A run is a no-op while the
 *   marker matches the bundled asset; a mismatch re-applies the full document.
 * - **Never overwrites user data:** `FoodItemDao.upsertSeed` only refreshes
 *   rows still in `ORIGINAL` state that the user did not create. Edited seeds
 *   (`USER_MODIFIED`), user-created foods, favorites' flag, barcodes and
 *   serving columns are untouched. A seed the user deleted reappears on the
 *   next version bump (accepted trade-off, ADR-008).
 * - **Never crashes the app:** asset and write failures are logged and
 *   returned as typed [DataError.Local]; the marker is only written after the
 *   whole document applied, so a partial run retries on the next launch.
 */
class BlsFoodSeeder(
    private val seedSource: SeedFoodDataSource,
    private val dao: FoodItemDao,
    private val settings: SettingsDataStore,
) {
    suspend fun seedIfNeeded(): EmptyResult<DataError.Local> {
        val document =
            when (val loaded = seedSource.load()) {
                is Result.Error -> return loaded
                is Result.Success -> loaded.data
            }

        val appliedVersion = settings.getString(SEED_FOOD_VERSION_KEY).first()
        if (appliedVersion == document.seedVersion.toString()) {
            return Result.Success(Unit)
        }

        val written =
            runCatchingLocal {
                withContext(Dispatchers.IO) {
                    document.foods.forEach { food ->
                        dao.upsertSeed(
                            id = food.id,
                            name = food.name,
                            nutriKcal = food.kcal,
                            nutriProteinG = food.proteinG,
                            nutriFatG = food.fatG,
                            nutriCarbsG = food.carbsG,
                            nutriFiberG = food.fiberG,
                        )
                    }
                }
            }

        return written
            .onSuccess {
                settings.setString(SEED_FOOD_VERSION_KEY, document.seedVersion.toString())
                AppLogger.i(
                    "Food seed v${document.seedVersion} applied (${document.foods.size} foods)",
                    tag = "BlsFoodSeeder",
                )
            }.onFailure { error ->
                AppLogger.w(
                    "Food seed v${document.seedVersion} not applied ($error); " +
                        "retries on next launch",
                    tag = "BlsFoodSeeder",
                )
            }
    }

    companion object {
        /**
         * Settings key holding the applied BLS seed version. Exported into
         * backups via the generic settings snapshot (A10) — the importer
         * clears it after a REPLACE so seeds re-apply into the restored DB.
         */
        const val SEED_FOOD_VERSION_KEY = "seed_food_version"
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun <T> runCatchingLocal(block: suspend () -> T): Result<T, DataError.Local> =
    try {
        Result.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("Food seed write failed", e, tag = "BlsFoodSeeder")
        Result.Error(DataError.Local.UNKNOWN)
    }
