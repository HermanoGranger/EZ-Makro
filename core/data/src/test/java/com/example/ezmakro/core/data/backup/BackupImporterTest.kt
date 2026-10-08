package com.example.ezmakro.core.data.backup

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.model.backup.BackupWarning
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Error
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream

/**
 * [BackupImporter] against the hand-written [FakeBackupLocalDataSource]: which
 * write path is taken, when settings are restored and how references are
 * resolved (A19).
 */
class BackupImporterTest {
    private val storage = InMemoryBackupStorage()
    private val local = FakeBackupLocalDataSource()
    private val importer = BackupImporter(local, storage)

    @Test
    fun `merge writes through insertAll and never replaceAll`() =
        runTest {
            put(seedContent())

            importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(local.insertAllCalls).isEqualTo(1)
            assertThat(local.replaceAllCalls).isEqualTo(0)
            assertThat(local.rows.foods).hasSize(4)
        }

    @Test
    fun `replace writes through replaceAll`() =
        runTest {
            put(seedContent())

            importer.import(DOC, ImportMode.REPLACE).expectSuccess()

            assertThat(local.replaceAllCalls).isEqualTo(1)
            assertThat(local.insertAllCalls).isEqualTo(0)
        }

    @Test
    fun `merge resolves a reference that exists only in the local database`() =
        runTest {
            local.existingFoodServingUnits = mapOf("db-only-food" to null)
            put(BackupContentDto(diaryEntries = listOf(entryTo("db-only-food"))))

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.droppedReferences).isEqualTo(0)
            assertThat(
                local.rows.diaryEntries
                    .single()
                    .foodItemId,
            ).isEqualTo("db-only-food")
        }

    @Test
    fun `replace resolves references only against the archive`() =
        runTest {
            local.existingFoodServingUnits = mapOf("db-only-food" to null)
            put(BackupContentDto(diaryEntries = listOf(entryTo("db-only-food"))))

            val report = importer.import(DOC, ImportMode.REPLACE).expectSuccess()

            assertThat(report.droppedReferences).isEqualTo(1)
            assertThat(
                local.rows.diaryEntries
                    .single()
                    .foodItemId,
            ).isNull()
            assertThat(
                local.rows.diaryEntries
                    .single()
                    .foodSnapshot,
            ).isNotNull()
        }

    @Test
    fun `settings are restored after a successful insert`() =
        runTest {
            put(BackupContentDto(settings = seedSettings))

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(local.restoredSettings).isEqualTo(seedSettings.associate { it.key to it.value })
            assertThat(report.settingsRestored).isEqualTo(2)
        }

    @Test
    fun `settings are not restored when the insert fails`() =
        runTest {
            put(BackupContentDto(settings = seedSettings))
            local.insertError = DataError.Local.DISK_FULL

            val failure = importer.import(DOC, ImportMode.MERGE).expectError()

            assertThat(failure).isEqualTo(DataError.Local.DISK_FULL)
            assertThat(local.restoredSettings).isNull()
        }

    @Test
    fun `settings restore failure keeps the committed import as a success with a warning`() =
        runTest {
            put(BackupContentDto(settings = seedSettings))
            local.restoreSettingsError = DataError.Local.DISK_FULL

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.settingsRestored).isEqualTo(0)
            assertThat(report.warnings).contains(BackupWarning.SettingsRestoreFailed)
            assertThat(local.restoredSettings).isNull()
            assertThat(local.insertAllCalls).isEqualTo(1)
        }

    @Test
    fun `replace clears the seed version marker so seeds re-apply on next launch`() =
        runTest {
            put(seedContent())

            importer.import(DOC, ImportMode.REPLACE).expectSuccess()

            assertThat(local.clearSeedVersionMarkerCalls).isEqualTo(1)
        }

    @Test
    fun `merge keeps the seed version marker untouched`() =
        runTest {
            put(seedContent())

            importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(local.clearSeedVersionMarkerCalls).isEqualTo(0)
        }

    @Test
    fun `seed marker reset failure after replace is a warning, not an error`() =
        runTest {
            put(seedContent())
            local.clearSeedVersionMarkerError = DataError.Local.DISK_FULL

            val report = importer.import(DOC, ImportMode.REPLACE).expectSuccess()

            assertThat(report.warnings).contains(BackupWarning.SettingsRestoreFailed)
            assertThat(local.replaceAllCalls).isEqualTo(1)
        }

    @Test
    fun `preview never writes`() =
        runTest {
            put(seedContent())

            val preview = importer.preview(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(preview.foodItemCount).isEqualTo(4)
            assertThat(preview.recipeCount).isEqualTo(1)
            assertThat(preview.diaryEntryCount).isEqualTo(2)
            assertThat(local.insertAllCalls).isEqualTo(0)
            assertThat(local.replaceAllCalls).isEqualTo(0)
            assertThat(local.restoredSettings).isNull()
        }

    @Test
    fun `preview resolves recipe ingredients against the known ids of its mode`() =
        runTest {
            local.existingFoodServingUnits = mapOf("db-only-food" to null)
            put(
                BackupContentDto(
                    recipes =
                        listOf(
                            BackupTestData
                                .recipe()
                                .toBackup()
                                .copy(
                                    ingredients =
                                        listOf(
                                            BackupTestData.ingredient(foodId = "db-only-food").toBackup(),
                                        ),
                                ),
                        ),
                ),
            )

            val merge = importer.preview(DOC, ImportMode.MERGE).expectSuccess()
            val replace = importer.preview(DOC, ImportMode.REPLACE).expectSuccess()

            assertThat(merge.warnings.filterIsInstance<BackupWarning.InvalidRow>()).hasSize(0)
            val invalidRow = replace.warnings.filterIsInstance<BackupWarning.InvalidRow>().single()
            assertThat(invalidRow.reason).isEqualTo("unbekannte Food-Referenz: db-only-food")
        }

    @Test
    fun `merge validates the serving unit against a food that exists only in the target database`() =
        runTest {
            local.existingFoodServingUnits = mapOf("db-only-food" to null)
            put(
                recipeContent(
                    BackupTestData
                        .ingredient(foodId = "db-only-food", grams = null, servingCount = 2.0)
                        .toBackup(),
                ),
            )

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.skippedRows.single().reason)
                .isEqualTo("servingCount braucht eine ServingUnit des Lebensmittels")
            assertThat(local.rows.recipes).isEmpty()
            assertThat(local.rows.ingredients).isEmpty()
        }

    @Test
    fun `merge accepts a target-db-only food that has a serving unit`() =
        runTest {
            local.existingFoodServingUnits =
                mapOf("db-only-food" to ServingUnit(label = "Portion", gramsEquivalent = 100.0))
            put(
                recipeContent(
                    BackupTestData
                        .ingredient(foodId = "db-only-food", grams = null, servingCount = 2.0)
                        .toBackup(),
                ),
            )

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.skippedRows).isEmpty()
            assertThat(local.rows.recipes).hasSize(1)
            assertThat(local.rows.ingredients).hasSize(1)
        }

    @Test
    fun `the target-db serving unit wins over the archive's on merge`() =
        runTest {
            local.existingFoodServingUnits = mapOf("shared-food" to null)
            val archiveFood =
                BackupTestData
                    .food("shared-food")
                    .copy(servingLabel = "Portion", servingGramsEquivalent = 100.0)
            put(
                recipeContent(
                    BackupTestData
                        .ingredient(foodId = "shared-food", grams = null, servingCount = 2.0)
                        .toBackup(),
                    foods = listOf(archiveFood.toBackup()),
                ),
            )

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.skippedRows.single().reason)
                .isEqualTo("servingCount braucht eine ServingUnit des Lebensmittels")
            assertThat(local.rows.recipes).isEmpty()
        }

    @Test
    fun `cancellation while reading is not turned into a DataError`() =
        runTest {
            val importer = BackupImporter(local, CancellingStorage())

            val failure = runCatching { importer.import(DOC, ImportMode.MERGE) }.exceptionOrNull()

            assertThat(failure).isNotNull()
            assertThat(failure is CancellationException).isTrue()
        }

    private suspend fun put(content: BackupContentDto) {
        storage.write(DOC, BackupCodec.encode(BackupTestData.document(content)).toByteArray(Charsets.UTF_8))
    }

    private fun entryTo(foodId: String) =
        BackupTestData
            .foodEntry(foodEntity = BackupTestData.food("snapshot-source"))
            .toBackup()
            .copy(foodItemId = foodId)

    /** A document with one recipe whose ingredients are the given archive rows. */
    private fun recipeContent(
        vararg ingredients: BackupIngredientDto,
        foods: List<BackupFoodItemDto> = emptyList(),
    ): BackupContentDto =
        BackupContentDto(
            foodItems = foods,
            recipes =
                listOf(
                    BackupTestData
                        .recipe()
                        .toBackup()
                        .copy(ingredients = ingredients.toList()),
                ),
        )

    private val seedSettings =
        listOf(
            BackupSettingDto("goal_kcal", "2000.0"),
            BackupSettingDto("theme", "dark"),
        )

    private fun seedContent(): BackupContentDto = BackupTestData.seedContent().copy(settings = seedSettings)

    private fun <D, E : Error> Result<D, E>.expectSuccess(): D =
        when (this) {
            is Result.Success -> data
            is Result.Error -> throw AssertionError("expected Success, was $error")
        }

    private fun <E : Error> Result<*, E>.expectError(): E =
        when (this) {
            is Result.Success -> throw AssertionError("expected Error, was $data")
            is Result.Error -> error
        }

    /** Produces nothing, cancels instead — proves the reader rethrows instead of mapping. */
    private class CancellingStorage : BackupStorage {
        override suspend fun openOutput(uri: String): OutputStream = throw CancellationException("cancelled")

        override suspend fun openInput(uri: String): InputStream = throw CancellationException("cancelled")
    }

    private companion object {
        const val DOC = "doc://import"
    }
}
