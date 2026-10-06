package com.example.ezmakro.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsDataStoreTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsDataStore
    private lateinit var scope: CoroutineScope

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private fun setup(
        @TempDir tempDir: File,
    ) {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { tempDir.resolve("test_settings.preferences_pb") },
            )
        settings = SettingsDataStore(dataStore)
    }

    @Test
    fun `setString then getString returns value`(
        @TempDir tempDir: File,
    ) = runTest {
        setup(tempDir)
        settings.setString("theme", "dark")
        assertThat(settings.getString("theme").first()).isEqualTo("dark")
    }

    @Test
    fun `getString returns null for missing key`(
        @TempDir tempDir: File,
    ) = runTest {
        setup(tempDir)
        assertThat(settings.getString("missing").first()).isNull()
    }

    @Test
    fun `remove deletes key`(
        @TempDir tempDir: File,
    ) = runTest {
        setup(tempDir)
        settings.setString("k", "v")
        settings.remove("k")
        assertThat(settings.getString("k").first()).isNull()
    }

    @Test
    fun `clear removes all entries`(
        @TempDir tempDir: File,
    ) = runTest {
        setup(tempDir)
        settings.setString("a", "1")
        settings.setString("b", "2")
        settings.clear()
        assertThat(settings.getString("a").first()).isNull()
        assertThat(settings.getString("b").first()).isNull()
    }

    @Test
    fun `snapshot returns every stored string keyed by preference name`(
        @TempDir tempDir: File,
    ) = runTest {
        setup(tempDir)
        settings.setString("goal_kcal", "2000.0")
        settings.setString("theme", "dark")

        assertThat(settings.snapshot())
            .isEqualTo(mapOf("goal_kcal" to "2000.0", "theme" to "dark"))
    }

    @Test
    fun `restore writes the archive values and keeps local keys`(
        @TempDir tempDir: File,
    ) = runTest {
        setup(tempDir)
        settings.setString("local_only", "keep")

        settings.restore(mapOf("goal_kcal" to "1800.0"))

        assertThat(settings.getString("goal_kcal").first()).isEqualTo("1800.0")
        assertThat(settings.getString("local_only").first()).isEqualTo("keep")
    }

    @Test
    fun `snapshot and restore are a no-op on an empty store`(
        @TempDir tempDir: File,
    ) = runTest {
        setup(tempDir)

        val empty = settings.snapshot()
        assertThat(empty).isEqualTo(emptyMap<String, String>())

        settings.restore(empty)

        assertThat(settings.snapshot()).isEqualTo(emptyMap<String, String>())
    }
}
