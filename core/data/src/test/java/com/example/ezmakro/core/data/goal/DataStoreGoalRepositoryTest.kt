package com.example.ezmakro.core.data.goal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.example.ezmakro.core.data.datastore.SettingsDataStore
import com.example.ezmakro.core.domain.model.DefaultNutritionGoal
import com.example.ezmakro.core.domain.model.NutritionGoal
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

class DataStoreGoalRepositoryTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private fun buildRepository(
        @TempDir tempDir: File,
    ): DataStoreGoalRepository {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { tempDir.resolve("goals.preferences_pb") },
            )
        return DataStoreGoalRepository(SettingsDataStore(dataStore))
    }

    @Test
    fun `observeGoal emits default when nothing persisted`(
        @TempDir tempDir: File,
    ) = runTest {
        val repo = buildRepository(tempDir)
        assertThat(repo.observeGoal().first()).isEqualTo(DefaultNutritionGoal)
    }

    @Test
    fun `saveGoal then observeGoal returns persisted values`(
        @TempDir tempDir: File,
    ) = runTest {
        val repo = buildRepository(tempDir)
        val goal = NutritionGoal(kcal = 1800.0, proteinG = 120.0, fatG = 60.0, carbsG = 200.0, fiberG = 25.0)
        repo.saveGoal(goal)
        assertThat(repo.observeGoal().first()).isEqualTo(goal)
    }

    @Test
    fun `saveGoal persists across a new repository instance`(
        @TempDir tempDir: File,
    ) = runTest {
        val first = buildRepository(tempDir)
        first.saveGoal(NutritionGoal(kcal = 1500.0, proteinG = 100.0, fatG = 50.0, carbsG = 180.0, fiberG = 20.0))
        scope.cancel()

        val second = buildRepository(tempDir)
        val observed = second.observeGoal().first()
        assertThat(observed.kcal).isEqualTo(1500.0)
        assertThat(observed.proteinG).isEqualTo(100.0)
    }
}
