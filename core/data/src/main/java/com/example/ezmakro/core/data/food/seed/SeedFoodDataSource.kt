package com.example.ezmakro.core.data.food.seed

import android.content.Context
import com.example.ezmakro.core.data.backup.ioErrorToDataError
import com.example.ezmakro.core.data.logging.AppLogger
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Loads the bundled BLS seed document. Split from [BlsFoodSeeder] so the
 * seeder can be tested against a hand-written document without touching
 * Android assets.
 */
interface SeedFoodDataSource {
    suspend fun load(): Result<SeedFoodDocumentDto, DataError.Local>
}

/**
 * Reads and decodes `assets/seed/food_seed_v1.json`. A missing or corrupted
 * asset is a build/packaging failure, not a user-facing error: it surfaces as
 * a typed [DataError.Local] which [BlsFoodSeeder] logs and swallows — the app
 * then starts without pre-seeded foods instead of crashing.
 */
class AssetSeedFoodDataSource(
    private val context: Context,
) : SeedFoodDataSource {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(): Result<SeedFoodDocumentDto, DataError.Local> =
        withContext(Dispatchers.IO) {
            try {
                val json =
                    context.assets
                        .open(SEED_ASSET_PATH)
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                Result.Success(SEED_JSON.decodeFromString(json))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SerializationException) {
                AppLogger.e("Seed asset is not valid JSON", e, tag = "AssetSeedFoodDataSource")
                Result.Error(DataError.Local.UNKNOWN)
            } catch (e: IllegalArgumentException) {
                AppLogger.e("Seed asset does not match the expected shape", e, tag = "AssetSeedFoodDataSource")
                Result.Error(DataError.Local.UNKNOWN)
            } catch (e: Exception) {
                AppLogger.e("Seed asset could not be read", e, tag = "AssetSeedFoodDataSource")
                Result.Error(ioErrorToDataError(e))
            }
        }

    companion object {
        const val SEED_ASSET_PATH = "seed/food_seed_v1.json"

        private val SEED_JSON =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
    }
}
