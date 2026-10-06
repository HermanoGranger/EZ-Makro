package com.example.ezmakro.core.data.backup

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.database.EZDatabase
import com.example.ezmakro.core.domain.model.backup.BackupOptions
import com.example.ezmakro.core.domain.result.Error
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.util.AppVersionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream

/**
 * [BackupExporter] against a hand-written [BackupLocalDataSource] and an
 * in-memory [BackupStorage] — no Room, no Robolectric (WP3 Vorgaben).
 */
class BackupExporterTest {
    private val storage = InMemoryBackupStorage()
    private val local =
        FakeBackupLocalDataSource(
            rows = BackupTestData.seedRows(),
            settings = mapOf("z_goal" to "2000.0", "a_theme" to "dark", "m_unit" to "g"),
        )
    private val exporter =
        BackupExporter(
            local = local,
            storage = storage,
            versionProvider = FixedVersionProvider,
            clock = { EXPORTED_AT },
        )

    @Test
    fun `default export contains only own favorite and referenced foods`() =
        runTest {
            val summary = exporter.export(DOC).expectSuccess()

            val content = readContent(DOC)
            assertThat(content.foodItems.map { it.id })
                .containsExactly(
                    BackupTestData.OWN_FOOD,
                    BackupTestData.FAVORITE_FOOD,
                    BackupTestData.REFERENCED_FOOD,
                )
            assertThat(summary.foodItemCount).isEqualTo(3)
            assertThat(summary.recipeCount).isEqualTo(1)
            assertThat(summary.diaryEntryCount).isEqualTo(2)
            assertThat(summary.weightLogCount).isEqualTo(1)
            assertThat(summary.settingCount).isEqualTo(3)
        }

    @Test
    fun `the off cache switch adds every cached food`() =
        runTest {
            exporter.export(DOC, BackupOptions(includeCachedOpenFoodFactsItems = true)).expectSuccess()

            val content = readContent(DOC)
            assertThat(content.foodItems).hasSize(4)
            assertThat(content.foodItems.map { it.id }).containsExactly(
                BackupTestData.OWN_FOOD,
                BackupTestData.FAVORITE_FOOD,
                BackupTestData.CACHED_FOOD,
                BackupTestData.REFERENCED_FOOD,
            )
        }

    @Test
    fun `recipes carry their ingredients, diary entries and weight logs are complete`() =
        runTest {
            exporter.export(DOC).expectSuccess()

            val content = readContent(DOC)
            val recipe = content.recipes.single()
            assertThat(recipe.id).isEqualTo(BackupTestData.RECIPE_ID)
            assertThat(recipe.ingredients.map { it.foodItemId }).containsExactly(BackupTestData.REFERENCED_FOOD)
            assertThat(content.diaryEntries.map { it.id }).containsExactly("diary-food", "diary-recipe")
            assertThat(content.weightLogs.single().date).isEqualTo(BackupTestData.DATE)
        }

    @Test
    fun `settings are written sorted by key`() =
        runTest {
            exporter.export(DOC).expectSuccess()

            assertThat(readContent(DOC).settings.map { it.key }).containsExactly("a_theme", "m_unit", "z_goal")
        }

    @Test
    fun `the document carries version schema and a valid checksum`() =
        runTest {
            exporter.export(DOC).expectSuccess()

            val decoded = BackupCodec.decode(storage.readText(DOC)) as BackupCodec.DecodeResult.Success
            val document = decoded.document
            assertThat(document.format).isEqualTo(BackupCodec.FORMAT_ID)
            assertThat(document.formatVersion).isEqualTo(BackupCodec.FORMAT_VERSION)
            assertThat(document.appVersionName).isEqualTo(FixedVersionProvider.versionName)
            assertThat(document.appVersionCode).isEqualTo(FixedVersionProvider.versionCode)
            assertThat(document.exportedAtEpochMillis).isEqualTo(EXPORTED_AT)
            assertThat(document.dbSchemaVersion).isEqualTo(EZDatabase.SCHEMA_VERSION)
            assertThat(decoded.checksumValid).isTrue()
            assertThat(storage.readText(DOC)).doesNotContain("rowId", "food_items_fts")
        }

    @Test
    fun `cancellation while writing is not turned into a DataError`() =
        runTest {
            val cancelling = CancellingStorage()
            val exporter =
                BackupExporter(
                    local = FakeBackupLocalDataSource(),
                    storage = cancelling,
                    versionProvider = FixedVersionProvider,
                )

            val failure = runCatching { exporter.export(DOC) }.exceptionOrNull()

            assertThat(failure).isNotNull()
            assertThat(failure is CancellationException).isTrue()
        }

    private suspend fun readContent(uri: String): BackupContentDto {
        val decoded = BackupCodec.decode(storage.readText(uri)) as BackupCodec.DecodeResult.Success
        return decoded.document.content
    }

    private fun <D, E : Error> Result<D, E>.expectSuccess(): D =
        when (this) {
            is Result.Success -> data
            is Result.Error -> throw AssertionError("expected Success, was $error")
        }

    private object FixedVersionProvider : AppVersionProvider {
        override val versionName: String = "2.3.1"

        override val versionCode: Int = 231
    }

    /** Produces nothing, cancels instead — proves the exporter rethrows instead of mapping. */
    private class CancellingStorage : BackupStorage {
        override suspend fun openOutput(uri: String): OutputStream = throw CancellationException("cancelled")

        override suspend fun openInput(uri: String): InputStream = throw CancellationException("cancelled")
    }

    private companion object {
        const val DOC = "doc://export"
        const val EXPORTED_AT = 1750000000000L
    }
}
