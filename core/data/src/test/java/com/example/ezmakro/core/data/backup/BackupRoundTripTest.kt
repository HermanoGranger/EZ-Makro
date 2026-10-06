package com.example.ezmakro.core.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.data.datastore.SettingsDataStore
import com.example.ezmakro.core.database.EZDatabase
import com.example.ezmakro.core.domain.model.backup.BackupOptions
import com.example.ezmakro.core.domain.model.backup.BackupWarning
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.model.backup.TableCounts
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Error
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.util.AppVersionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * End-to-end proof of WP3: export into an in-memory document, wipe the real
 * (in-memory Room) database and import the document back — plus the failure
 * paths of §3.4 against real files.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: EZDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var scope: CoroutineScope
    private lateinit var local: RoomBackupLocalDataSource
    private lateinit var exporter: BackupExporter
    private lateinit var importer: BackupImporter
    private val storage = InMemoryBackupStorage()

    /** A5 switch on: the whole seed, including the plain cached OFF food, must round trip. */
    private val fullOptions = BackupOptions(includeCachedOpenFoodFactsItems = true)

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EZDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings =
            SettingsDataStore(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { File(tempFolder.root, "settings.preferences_pb") },
                ),
            )
        local = RoomBackupLocalDataSource(db.backupDao(), settings)
        exporter =
            BackupExporter(
                local = local,
                storage = storage,
                versionProvider = FixedVersionProvider,
                clock = { 1750000000000L },
            )
        importer = BackupImporter(local, storage)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `merge round trip restores every row byte for byte and the settings`() =
        runTest {
            val seed = BackupTestData.seedRows()
            local.insertAll(seed).expectSuccess()
            settings.setString("goal_kcal", "2000.0")
            settings.setString("theme", "dark")
            exporter.export(DOC, fullOptions).expectSuccess()

            wipeDatabase()
            settings.clear()

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.inserted).isEqualTo(counts(food = 4, recipes = 1, ingredients = 1, diary = 2, weight = 1))
            assertThat(report.skippedRows).hasSize(0)
            assertThat(report.droppedReferences).isEqualTo(0)
            assertThat(report.settingsRestored).isEqualTo(2)

            val restored = local.readAll().expectSuccess()
            assertThat(restored.foods.map { it.copy(rowId = 0) }).isEqualTo(seed.foods)
            assertThat(restored.recipes).isEqualTo(seed.recipes)
            assertThat(restored.ingredients.map { it.copy(rowId = 0L) }).isEqualTo(seed.ingredients)
            assertThat(restored.diaryEntries).isEqualTo(seed.diaryEntries)
            assertThat(restored.weightLogs).isEqualTo(seed.weightLogs)

            assertThat(settings.getString("goal_kcal").first()).isEqualTo("2000.0")
            assertThat(settings.getString("theme").first()).isEqualTo("dark")
        }

    @Test
    fun `merge keeps a locally changed favorite when the archive is imported again`() =
        runTest {
            val seed = BackupTestData.seedRows()
            local.insertAll(seed).expectSuccess()
            exporter.export(DOC, fullOptions).expectSuccess()

            val toggled =
                seed.copy(
                    foods =
                        seed.foods.map {
                            if (it.id == BackupTestData.CACHED_FOOD) it.copy(isFavorite = true) else it
                        },
                )
            local.replaceAll(toggled).expectSuccess()

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.inserted).isEqualTo(counts())
            val food =
                local
                    .readAll()
                    .expectSuccess()
                    .foods
                    .single { it.id == BackupTestData.CACHED_FOOD }
            assertThat(food.isFavorite).isTrue()
        }

    @Test
    fun `replace round trip overwrites local content with the archive`() =
        runTest {
            val seed = BackupTestData.seedRows()
            local.insertAll(seed).expectSuccess()
            exporter.export(DOC, fullOptions).expectSuccess()

            wipeDatabase()
            local
                .insertAll(
                    BackupRows(
                        foods = listOf(BackupTestData.food("local-only", name = "Nur lokal")),
                        weightLogs = listOf(BackupTestData.weightLog("2026-01-01", 90.0)),
                    ),
                ).expectSuccess()

            val report = importer.import(DOC, ImportMode.REPLACE).expectSuccess()

            assertThat(report.inserted).isEqualTo(counts(food = 4, recipes = 1, ingredients = 1, diary = 2, weight = 1))
            val restored = local.readAll().expectSuccess()
            assertThat(restored.foods.map { it.id }).containsExactly(
                BackupTestData.OWN_FOOD,
                BackupTestData.FAVORITE_FOOD,
                BackupTestData.CACHED_FOOD,
                BackupTestData.REFERENCED_FOOD,
            )
            assertThat(restored.foods.single { it.id == BackupTestData.OWN_FOOD }.copy(rowId = 0))
                .isEqualTo(seed.foods.first { it.id == BackupTestData.OWN_FOOD })
            assertThat(restored.weightLogs).isEqualTo(seed.weightLogs)
        }

    @Test
    fun `import drops an unknown food reference but keeps the entry renderable`() =
        runTest {
            val seed = BackupTestData.seedRows()
            val food = seed.foods.first { it.id == BackupTestData.OWN_FOOD }
            val entry =
                BackupTestData.foodEntry(foodEntity = food, id = "diary-ghost").copy(foodItemId = "ghost-food")
            writeDocument(
                BackupContentDto(
                    foodItems = seed.foods.map { it.toBackup() },
                    diaryEntries = listOf(entry.toBackup()),
                ),
            )

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.droppedReferences).isEqualTo(1)
            assertThat(report.skippedRows).hasSize(0)
            assertThat(report.inserted.diaryEntries).isEqualTo(1)
            val imported =
                local
                    .readAll()
                    .expectSuccess()
                    .diaryEntries
                    .single()
            assertThat(imported.foodItemId).isNull()
            assertThat(imported.foodSnapshot).isEqualTo(entry.foodSnapshot)
        }

    @Test
    fun `a recipe with an unknown food reference is skipped with a reason`() =
        runTest {
            val recipe = BackupTestData.recipe(id = "recipe-ghost")
            writeDocument(
                BackupContentDto(
                    recipes =
                        listOf(
                            recipe
                                .toBackup()
                                .copy(
                                    ingredients =
                                        listOf(
                                            BackupIngredientDto(foodItemId = "ghost-food", rawWeightGrams = 80.0),
                                        ),
                                ),
                        ),
                ),
            )

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.inserted).isEqualTo(counts())
            assertThat(report.skippedRows).hasSize(1)
            val skipped = report.skippedRows.single()
            assertThat(skipped.table).isEqualTo("recipes")
            assertThat(skipped.id).isEqualTo("recipe-ghost")
            assertThat(skipped.reason).isEqualTo("unbekannte Food-Referenz: ghost-food")
        }

    @Test
    fun `second merge import is idempotent`() =
        runTest {
            val seed = BackupTestData.seedRows()
            local.insertAll(seed).expectSuccess()
            exporter.export(DOC, fullOptions).expectSuccess()
            wipeDatabase()

            val first = importer.import(DOC, ImportMode.MERGE).expectSuccess()
            val second = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(second.inserted).isEqualTo(counts())
            assertThat(second.skippedDuplicates)
                .isEqualTo(first.inserted)
            assertThat(second.skippedRows).hasSize(0)
        }

    @Test
    fun `second replace import reports the same exact counts`() =
        runTest {
            val seed = BackupTestData.seedRows()
            local.insertAll(seed).expectSuccess()
            exporter.export(DOC, fullOptions).expectSuccess()

            val first = importer.import(DOC, ImportMode.REPLACE).expectSuccess()
            val second = importer.import(DOC, ImportMode.REPLACE).expectSuccess()

            assertThat(second.inserted).isEqualTo(first.inserted)
            assertThat(second.skippedDuplicates).isEqualTo(counts())
            assertThat(second.inserted).isEqualTo(counts(food = 4, recipes = 1, ingredients = 1, diary = 2, weight = 1))
        }

    @Test
    fun `broken rows are skipped with a reason and the rest is imported`() =
        runTest {
            val goodFood = BackupTestData.food("good-food", name = "Gutes Essen")
            val badFood = BackupTestData.food("bad-food", name = "Gammelig").copy(nutriKcal = -10.0)
            val badSlot = BackupTestData.foodEntry(foodEntity = goodFood, id = "diary-bad-slot").copy(slot = "SUPPER")
            writeDocument(
                BackupContentDto(
                    foodItems = listOf(badFood, goodFood).map { it.toBackup() },
                    diaryEntries = listOf(badSlot.toBackup()),
                    weightLogs = listOf(BackupTestData.weightLog().toBackup()),
                ),
            )

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.inserted).isEqualTo(counts(food = 1, weight = 1))
            assertThat(report.skippedRows).hasSize(2)
            assertThat(report.skippedRows.map { it.id }).containsExactly("bad-food", "diary-bad-slot")
            assertThat(report.skippedRows.first { it.id == "bad-food" }.table).isEqualTo("food_items")
            assertThat(report.skippedRows.first { it.id == "diary-bad-slot" }.table).isEqualTo("diary_entries")
            val rows = local.readAll().expectSuccess()
            assertThat(rows.foods.map { it.id }).containsExactly("good-food")
            assertThat(rows.diaryEntries).hasSize(0)
        }

    @Test
    fun `a row with an unreadable snapshot is skipped`() =
        runTest {
            val broken =
                BackupTestData.foodEntry(id = "diary-broken").copy(
                    foodItemId = null,
                    foodSnapshot = "{kein json",
                )
            writeDocument(BackupContentDto(diaryEntries = listOf(broken.toBackup())))

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.inserted).isEqualTo(counts())
            assertThat(report.skippedRows).hasSize(1)
            assertThat(report.skippedRows.single().id).isEqualTo("diary-broken")
            assertThat(local.readAll().expectSuccess().diaryEntries).hasSize(0)
        }

    @Test
    fun `the evolved fixture imports successfully`() =
        runTest {
            storage.write(DOC, fixture("backup-v1-evolved.json").toByteArray(Charsets.UTF_8))

            val report = importer.import(DOC, ImportMode.MERGE).expectSuccess()

            assertThat(report.inserted).isEqualTo(counts(food = 1, recipes = 1, ingredients = 1, weight = 1))
            assertThat(report.skippedRows).hasSize(1)
            assertThat(report.skippedRows.single().id).isEqualTo("diary-future")
            assertThat(report.warnings).containsExactly(BackupWarning.ChecksumMismatch)
        }

    @Test
    fun `preview writes nothing`() =
        runTest {
            val seed = BackupTestData.seedRows()
            local.insertAll(seed).expectSuccess()
            exporter.export(DOC, fullOptions).expectSuccess()
            wipeDatabase()
            settings.clear()

            val preview = importer.preview(DOC).expectSuccess()

            assertThat(preview.foodItemCount).isEqualTo(4)
            assertThat(preview.recipeCount).isEqualTo(1)
            assertThat(preview.diaryEntryCount).isEqualTo(2)
            assertThat(preview.earliestDate).isEqualTo(java.time.LocalDate.parse(BackupTestData.DATE))
            assertThat(local.readAll().expectSuccess().foods).hasSize(0)
            assertThat(settings.getString("goal_kcal").first()).isNull()
        }

    @Test
    fun `a foreign file is rejected as INVALID_BACKUP`() =
        runTest {
            storage.write(DOC, "das ist kein Backup".toByteArray(Charsets.UTF_8))

            val error = importer.import(DOC, ImportMode.MERGE).expectError()

            assertThat(error).isEqualTo(DataError.Local.INVALID_BACKUP)
        }

    @Test
    fun `a newer format version is rejected as UNSUPPORTED_BACKUP_VERSION`() =
        runTest {
            val document =
                BackupTestData.document(BackupContentDto()).copy(
                    formatVersion =
                        BackupCodec.FORMAT_VERSION + 1,
                )
            storage.write(DOC, BackupCodec.encode(document).toByteArray(Charsets.UTF_8))

            val error = importer.import(DOC, ImportMode.MERGE).expectError()

            assertThat(error).isEqualTo(DataError.Local.UNSUPPORTED_BACKUP_VERSION)
        }

    @Test
    fun `a truncated file is rejected as INVALID_BACKUP`() =
        runTest {
            val full = BackupCodec.encode(BackupTestData.document(BackupTestData.seedContent()))
            storage.write(DOC, full.take(full.length / 2).toByteArray(Charsets.UTF_8))

            val error = importer.import(DOC, ImportMode.MERGE).expectError()

            assertThat(error).isEqualTo(DataError.Local.INVALID_BACKUP)
        }

    private suspend fun wipeDatabase() {
        local.replaceAll(BackupRows.EMPTY).expectSuccess()
    }

    private suspend fun writeDocument(
        content: BackupContentDto,
        uri: String = DOC,
    ) {
        storage.write(uri, BackupCodec.encode(BackupTestData.document(content)).toByteArray(Charsets.UTF_8))
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/backup/$name")) { "missing fixture $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private fun counts(
        food: Int = 0,
        recipes: Int = 0,
        ingredients: Int = 0,
        diary: Int = 0,
        weight: Int = 0,
    ) = TableCounts(
        foodItems = food,
        recipes = recipes,
        recipeIngredients = ingredients,
        diaryEntries = diary,
        weightLogs = weight,
    )

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

    private object FixedVersionProvider : AppVersionProvider {
        override val versionName: String = "1.0"

        override val versionCode: Int = 1
    }

    private companion object {
        const val DOC = "doc://backup"
    }
}
