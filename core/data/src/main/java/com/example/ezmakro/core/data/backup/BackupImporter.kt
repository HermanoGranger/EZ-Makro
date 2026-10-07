package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.data.food.mapper.toFoodItem
import com.example.ezmakro.core.data.logging.AppLogger
import com.example.ezmakro.core.database.dao.BackupInsertReport
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.database.entity.WeightLogEntity
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.model.backup.BackupPreview
import com.example.ezmakro.core.domain.model.backup.BackupWarning
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.model.backup.ImportReport
import com.example.ezmakro.core.domain.model.backup.SkippedRow
import com.example.ezmakro.core.domain.model.backup.TableCounts
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.flatMap
import com.example.ezmakro.core.domain.result.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Reads a backup file, validates it and writes it into Room.
 *
 * Order of a run (§3.4): decode → stage 4 (row validation, no target-DB access)
 * → stage 5 (reference cleanup, only for diary entries) → **one** Room
 * transaction via `insertAll`/`replaceAll` → settings restore → report. The
 * settings are written only after a successful commit: DataStore is not part of
 * the Room transaction, and a rolled back import must not touch it (A11, A16).
 *
 * Nothing throws: every failure is a typed [Result] error, invalid rows are
 * skipped with a reason instead of aborting (A11).
 */
@Suppress("TooManyFunctions")
class BackupImporter(
    private val local: BackupLocalDataSource,
    private val storage: BackupStorage,
) {
    /** Dry run: stages 1–4 only, never writes (A11 — stage 5 is write preparation). */
    suspend fun preview(
        uri: String,
        mode: ImportMode,
    ): Result<BackupPreview, DataError.Local> =
        readDocument(uri).flatMap { decoded ->
            existingIds(mode).map { existing ->
                buildPreview(decoded, validateRows(decoded.document.content, existing).skippedRows)
            }
        }

    suspend fun import(
        uri: String,
        mode: ImportMode,
    ): Result<ImportReport, DataError.Local> =
        readDocument(uri).flatMap { decoded ->
            existingIds(mode).flatMap { existing ->
                val stage = cleanReferences(validateRows(decoded.document.content, existing))
                writeRows(stage.rows, mode).map { insertReport ->
                    val settings =
                        decoded.document.content.settings
                            .associate { it.key to it.value }
                    val (settingsRestored, settingsWarnings) = restoreSettings(settings)
                    buildReport(mode, insertReport, stage, settingsRestored, decoded.checksumValid, settingsWarnings)
                }
            }
        }

    /** Stages 1–3: read UTF-8, decode, map codec failures to typed errors. */
    private suspend fun readDocument(uri: String): Result<BackupCodec.DecodeResult.Success, DataError.Local> =
        readText(uri).flatMap { text ->
            when (val decoded = BackupCodec.decode(text)) {
                is BackupCodec.DecodeResult.Failure -> Result.Error(decoded.reason.toDataError())
                is BackupCodec.DecodeResult.Success -> Result.Success(decoded)
            }
        }

    private fun BackupCodec.DecodeError.toDataError(): DataError.Local =
        when (this) {
            BackupCodec.DecodeError.NotAZBackup -> DataError.Local.INVALID_BACKUP
            is BackupCodec.DecodeError.UnsupportedVersion -> DataError.Local.UNSUPPORTED_BACKUP_VERSION
            BackupCodec.DecodeError.Corrupted -> DataError.Local.INVALID_BACKUP
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun readText(uri: String): Result<String, DataError.Local> =
        withContext(Dispatchers.IO) {
            try {
                val text =
                    storage
                        .openInput(uri)
                        .use { input -> input.readBytes().toString(Charsets.UTF_8) }
                Result.Success(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("Backup read failed", e, tag = "BackupImporter")
                Result.Error(ioErrorToDataError(e))
            }
        }

    /**
     * Context the archive may resolve against (A19): MERGE = target-DB foods
     * (id → serving unit) ∪ valid backup ids, REPLACE = none — so REPLACE
     * never resolves against the current database content. The union with the
     * backup ids happens in [validateRows].
     */
    private suspend fun existingIds(mode: ImportMode): Result<ExistingIds, DataError.Local> =
        if (mode == ImportMode.REPLACE) {
            Result.Success(ExistingIds.EMPTY)
        } else {
            local.readFoodServingUnits().flatMap { foodServingUnits ->
                local.readRecipeIds().map { recipeIds -> ExistingIds(foodServingUnits, recipeIds) }
            }
        }

    /** Stage 4: validate every row in a fixed order, without touching the target DB. */
    private fun validateRows(
        content: BackupContentDto,
        existing: ExistingIds,
    ): StageFour {
        val skippedRows = mutableListOf<SkippedRow>()
        val validFoods = mutableMapOf<String, FoodItem>()
        val foodEntities = validateFoods(content.foodItems, validFoods, skippedRows)
        val knownFoodIds = existing.foodServingUnits.keys + validFoods.keys
        val (recipeEntities, ingredientEntities) =
            validateRecipes(content.recipes, validFoods, existing.foodServingUnits, skippedRows)
        val knownRecipeIds = existing.recipeIds + recipeEntities.map { it.id }
        return StageFour(
            rows =
                BackupRows(
                    foods = foodEntities,
                    recipes = recipeEntities,
                    ingredients = ingredientEntities,
                    diaryEntries = validateDiaryEntries(content.diaryEntries, skippedRows),
                    weightLogs = validateWeightLogs(content.weightLogs, skippedRows),
                ),
            skippedRows = skippedRows,
            knownFoodIds = knownFoodIds,
            knownRecipeIds = knownRecipeIds,
        )
    }

    private fun validateFoods(
        items: List<BackupFoodItemDto>,
        validFoods: MutableMap<String, FoodItem>,
        skippedRows: MutableList<SkippedRow>,
    ): List<FoodItemEntity> {
        val entities = mutableListOf<FoodItemEntity>()
        for (item in items) {
            val reason = BackupValidator.validateFood(item)
            if (reason == null) {
                val entity = item.toEntity()
                validFoods[item.id] = entity.toFoodItem()
                entities += entity
            } else {
                skippedRows += SkippedRow(TABLE_FOODS, item.id, reason)
            }
        }
        return entities
    }

    private fun validateRecipes(
        recipes: List<BackupRecipeDto>,
        validFoods: Map<String, FoodItem>,
        existingFoodServingUnits: Map<String, ServingUnit?>,
        skippedRows: MutableList<SkippedRow>,
    ): Pair<List<RecipeEntity>, List<RecipeIngredientEntity>> {
        val entities = mutableListOf<RecipeEntity>()
        val ingredients = mutableListOf<RecipeIngredientEntity>()
        for (recipe in recipes) {
            val reason = BackupValidator.validateRecipe(recipe, validFoods, existingFoodServingUnits)
            if (reason == null) {
                val (recipeEntity, ingredientEntityList) = recipe.toEntities()
                entities += recipeEntity
                ingredients += ingredientEntityList
            } else {
                skippedRows += SkippedRow(TABLE_RECIPES, recipe.id, reason)
            }
        }
        return entities to ingredients
    }

    private fun validateDiaryEntries(
        entries: List<BackupDiaryEntryDto>,
        skippedRows: MutableList<SkippedRow>,
    ): List<DiaryEntryEntity> {
        val entities = mutableListOf<DiaryEntryEntity>()
        for (entry in entries) {
            val reason = BackupValidator.validateDiaryEntry(entry)
            if (reason == null) {
                entities += entry.toEntity()
            } else {
                skippedRows += SkippedRow(TABLE_DIARY, entry.id, reason)
            }
        }
        return entities
    }

    private fun validateWeightLogs(
        logs: List<BackupWeightLogDto>,
        skippedRows: MutableList<SkippedRow>,
    ): List<WeightLogEntity> {
        val entities = mutableListOf<WeightLogEntity>()
        for (log in logs) {
            val reason = BackupValidator.validateWeightLog(log)
            if (reason == null) {
                entities += log.toEntity()
            } else {
                skippedRows += SkippedRow(TABLE_WEIGHT_LOGS, log.date, reason)
            }
        }
        return entities
    }

    /**
     * Stage 5 (diary entries only): a `foodItemId`/`recipeId` outside the known
     * set is dropped and counted when its snapshot is still decodable — the
     * entry keeps rendering from the snapshot. Without a snapshot the row would
     * lose its content, so it is skipped instead.
     */
    private fun cleanReferences(stage: StageFour): StageFive {
        val skippedRows = stage.skippedRows.toMutableList()
        var droppedReferences = 0
        val diaryEntities =
            stage.rows.diaryEntries.mapNotNull { entity ->
                val foodUnknown = entity.foodItemId != null && entity.foodItemId !in stage.knownFoodIds
                val recipeUnknown = entity.recipeId != null && entity.recipeId !in stage.knownRecipeIds
                val foodRenderable = !foodUnknown || entity.foodSnapshot != null
                val recipeRenderable = !recipeUnknown || entity.recipeSnapshot != null
                if (!foodRenderable || !recipeRenderable) {
                    skippedRows +=
                        SkippedRow(
                            table = TABLE_DIARY,
                            id = entity.id,
                            reason = "Referenz unbekannt und kein Snapshot vorhanden",
                        )
                    return@mapNotNull null
                }
                droppedReferences += (if (foodUnknown) 1 else 0) + (if (recipeUnknown) 1 else 0)
                entity.copy(
                    foodItemId = if (foodUnknown) null else entity.foodItemId,
                    recipeId = if (recipeUnknown) null else entity.recipeId,
                )
            }
        return StageFive(
            rows = stage.rows.copy(diaryEntries = diaryEntities),
            skippedRows = skippedRows,
            droppedReferences = droppedReferences,
        )
    }

    private suspend fun writeRows(
        rows: BackupRows,
        mode: ImportMode,
    ): Result<BackupInsertReport, DataError.Local> =
        when (mode) {
            ImportMode.MERGE -> local.insertAll(rows)
            ImportMode.REPLACE -> local.replaceAll(rows)
        }

    /**
     * Settings follow the successful commit (§3.4). A DataStore failure at
     * this point must not turn the run into `Result.Error`: the five tables
     * are already committed, so the report carries `settingsRestored = 0`
     * plus [BackupWarning.SettingsRestoreFailed] instead. Cancellation is
     * unaffected — the local data source rethrows it.
     */
    private suspend fun restoreSettings(settings: Map<String, String>): Pair<Int, List<BackupWarning>> =
        when (val result = local.restoreSettings(settings)) {
            is Result.Success -> settings.size to emptyList()
            is Result.Error -> {
                AppLogger.w("Settings restore failed after commit: ${result.error}", tag = "BackupImporter")
                0 to listOf(BackupWarning.SettingsRestoreFailed)
            }
        }

    private fun buildReport(
        mode: ImportMode,
        insertReport: BackupInsertReport,
        stage: StageFive,
        settingsRestored: Int,
        checksumValid: Boolean,
        extraWarnings: List<BackupWarning>,
    ): ImportReport =
        ImportReport(
            mode = mode,
            inserted =
                TableCounts(
                    foodItems = insertReport.foodItems.inserted,
                    recipes = insertReport.recipes.inserted,
                    recipeIngredients = insertReport.recipeIngredients.inserted,
                    diaryEntries = insertReport.diaryEntries.inserted,
                    weightLogs = insertReport.weightLogs.inserted,
                ),
            skippedDuplicates =
                TableCounts(
                    foodItems = insertReport.foodItems.skipped,
                    recipes = insertReport.recipes.skipped,
                    recipeIngredients = insertReport.recipeIngredients.skipped,
                    diaryEntries = insertReport.diaryEntries.skipped,
                    weightLogs = insertReport.weightLogs.skipped,
                ),
            skippedRows = stage.skippedRows,
            droppedReferences = stage.droppedReferences,
            settingsRestored = settingsRestored,
            warnings = warningsOf(checksumValid) + extraWarnings,
        )

    private fun buildPreview(
        decoded: BackupCodec.DecodeResult.Success,
        skippedRows: List<SkippedRow>,
    ): BackupPreview {
        val content = decoded.document.content
        val dates =
            content.diaryEntries.mapNotNull { parseDate(it.date) } +
                content.weightLogs.mapNotNull { parseDate(it.date) }
        return BackupPreview(
            formatVersion = decoded.document.formatVersion,
            appVersionName = decoded.document.appVersionName,
            exportedAtEpochMillis = decoded.document.exportedAtEpochMillis,
            foodItemCount = content.foodItems.size,
            recipeCount = content.recipes.size,
            diaryEntryCount = content.diaryEntries.size,
            weightLogCount = content.weightLogs.size,
            earliestDate = dates.minOrNull(),
            latestDate = dates.maxOrNull(),
            warnings =
                warningsOf(decoded.checksumValid) +
                    skippedRows.map { BackupWarning.InvalidRow(it.table, it.id, it.reason) },
        )
    }

    private fun warningsOf(checksumValid: Boolean): List<BackupWarning> =
        if (checksumValid) {
            emptyList()
        } else {
            listOf(BackupWarning.ChecksumMismatch)
        }

    private fun parseDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (ignore: DateTimeParseException) {
            AppLogger.w("Unparsable date in backup: $value", tag = "BackupImporter")
            null
        }

    private data class ExistingIds(
        val foodServingUnits: Map<String, ServingUnit?>,
        val recipeIds: Set<String>,
    ) {
        companion object {
            val EMPTY = ExistingIds(emptyMap(), emptySet())
        }
    }

    private data class StageFour(
        val rows: BackupRows,
        val skippedRows: List<SkippedRow>,
        val knownFoodIds: Set<String>,
        val knownRecipeIds: Set<String>,
    )

    private data class StageFive(
        val rows: BackupRows,
        val skippedRows: List<SkippedRow>,
        val droppedReferences: Int,
    )

    private companion object {
        const val TABLE_FOODS = "food_items"
        const val TABLE_RECIPES = "recipes"
        const val TABLE_DIARY = "diary_entries"
        const val TABLE_WEIGHT_LOGS = "weight_logs"
    }
}
