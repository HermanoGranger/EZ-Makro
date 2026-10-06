package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.data.logging.AppLogger
import com.example.ezmakro.core.database.EZDatabase
import com.example.ezmakro.core.domain.model.backup.BackupExportSummary
import com.example.ezmakro.core.domain.model.backup.BackupOptions
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.flatMap
import com.example.ezmakro.core.domain.result.map
import com.example.ezmakro.core.domain.util.AppVersionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds and writes a backup document (§3.1) for the URI the user picked.
 *
 * Scope of one run (A5): own foods, favorites and foods referenced by recipes
 * or diary entries — plus the whole OFF cache when
 * [BackupOptions.includeCachedOpenFoodFactsItems] is on — and always all
 * recipes with their ingredients, all diary entries, all weight logs and all
 * settings. Settings are written sorted by key so two archives of the same data
 * hash identically (A14).
 *
 * Failures of the run surface as a typed [Result]; nothing throws into the
 * caller.
 */
class BackupExporter(
    private val local: BackupLocalDataSource,
    private val storage: BackupStorage,
    private val versionProvider: AppVersionProvider,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun export(
        uri: String,
        options: BackupOptions = BackupOptions(),
    ): Result<BackupExportSummary, DataError.Local> =
        local.readAll().flatMap { rows ->
            local.readSettings().flatMap { settings ->
                val document = buildDocument(rows, settings, options)
                write(uri, document).map { summarize(document.content) }
            }
        }

    private fun buildDocument(
        rows: BackupRows,
        settings: Map<String, String>,
        options: BackupOptions,
    ): BackupDocumentDto {
        val content = buildContent(rows, settings, options)
        return BackupDocumentDto(
            format = BackupCodec.FORMAT_ID,
            formatVersion = BackupCodec.FORMAT_VERSION,
            appVersionName = versionProvider.versionName,
            appVersionCode = versionProvider.versionCode,
            exportedAtEpochMillis = clock(),
            dbSchemaVersion = EZDatabase.SCHEMA_VERSION,
            checksum = BackupCodec.checksum(content),
            content = content,
        )
    }

    private fun buildContent(
        rows: BackupRows,
        settings: Map<String, String>,
        options: BackupOptions,
    ): BackupContentDto {
        val referencedFoodIds = rows.referencedFoodIds()
        val ingredientsByRecipe = rows.ingredients.groupBy { it.recipeId }
        return BackupContentDto(
            settings =
                settings
                    .map { (key, value) -> BackupSettingDto(key = key, value = value) }
                    .sortedBy { it.key },
            foodItems =
                rows.foods
                    .filter {
                        options.includeCachedOpenFoodFactsItems ||
                            it.isUserCreated ||
                            it.isFavorite ||
                            it.id in referencedFoodIds
                    }.map { it.toBackup() },
            recipes =
                rows.recipes.map { recipe ->
                    recipe
                        .toBackup()
                        .copy(
                            ingredients =
                                ingredientsByRecipe[recipe.id].orEmpty().map { it.toBackup() },
                        )
                },
            diaryEntries = rows.diaryEntries.map { it.toBackup() },
            weightLogs = rows.weightLogs.map { it.toBackup() },
        )
    }

    /** Food ids referenced by a recipe ingredient or a diary entry (A5). */
    private fun BackupRows.referencedFoodIds(): Set<String> =
        buildSet {
            ingredients.forEach { ingredient -> ingredient.foodItemId?.let { add(it) } }
            diaryEntries.forEach { entry -> entry.foodItemId?.let { add(it) } }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun write(
        uri: String,
        document: BackupDocumentDto,
    ): EmptyResult<DataError.Local> =
        withContext(Dispatchers.IO) {
            try {
                storage.openOutput(uri).use { output ->
                    output.write(BackupCodec.encode(document).toByteArray(Charsets.UTF_8))
                }
                Result.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("Backup export failed", e, tag = "BackupExporter")
                Result.Error(ioErrorToDataError(e))
            }
        }

    private fun summarize(content: BackupContentDto): BackupExportSummary =
        BackupExportSummary(
            foodItemCount = content.foodItems.size,
            recipeCount = content.recipes.size,
            diaryEntryCount = content.diaryEntries.size,
            weightLogCount = content.weightLogs.size,
            settingCount = content.settings.size,
        )
}
