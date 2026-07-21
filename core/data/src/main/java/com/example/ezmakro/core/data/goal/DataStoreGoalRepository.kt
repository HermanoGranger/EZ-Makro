package com.example.ezmakro.core.data.goal

import com.example.ezmakro.core.data.datastore.SettingsDataStore
import com.example.ezmakro.core.domain.model.DefaultNutritionGoal
import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Persisted [GoalRepository] backed by [SettingsDataStore] (DataStore
 * Preferences). Each goal field is stored as its own double preference so a
 * partial write can never leave the goal in an unreadable state and there is
 * no bespoke serialization to maintain.
 *
 * ADR-002 (offline-first): this is the single source of truth for goals; there
 * is no remote. Until the user saves a goal, [DefaultNutritionGoal] is emitted
 * (no preference present yet).
 */
class DataStoreGoalRepository(
    private val settings: SettingsDataStore,
) : GoalRepository {
    override fun observeGoal(): Flow<NutritionGoal> =
        combine(
            settings.getString(KEY_KCAL),
            settings.getString(KEY_PROTEIN),
            settings.getString(KEY_FAT),
            settings.getString(KEY_CARBS),
            settings.getString(KEY_FIBER),
        ) { kcal, protein, fat, carbs, fiber ->
            if (kcal == null) {
                DefaultNutritionGoal
            } else {
                NutritionGoal(
                    kcal = kcal?.toDoubleOrNull() ?: DefaultNutritionGoal.kcal,
                    proteinG = protein?.toDoubleOrNull() ?: DefaultNutritionGoal.proteinG,
                    fatG = fat?.toDoubleOrNull() ?: DefaultNutritionGoal.fatG,
                    carbsG = carbs?.toDoubleOrNull() ?: DefaultNutritionGoal.carbsG,
                    fiberG = fiber?.toDoubleOrNull() ?: DefaultNutritionGoal.fiberG,
                )
            }
        }

    override suspend fun saveGoal(goal: NutritionGoal) {
        settings.setString(KEY_KCAL, goal.kcal.toString())
        settings.setString(KEY_PROTEIN, goal.proteinG.toString())
        settings.setString(KEY_FAT, goal.fatG.toString())
        settings.setString(KEY_CARBS, goal.carbsG.toString())
        settings.setString(KEY_FIBER, goal.fiberG.toString())
    }

    private companion object {
        const val KEY_KCAL = "goal_kcal"
        const val KEY_PROTEIN = "goal_protein"
        const val KEY_FAT = "goal_fat"
        const val KEY_CARBS = "goal_carbs"
        const val KEY_FIBER = "goal_fiber"
    }
}
