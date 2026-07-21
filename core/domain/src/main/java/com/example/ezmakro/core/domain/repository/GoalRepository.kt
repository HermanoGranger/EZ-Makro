package com.example.ezmakro.core.domain.repository

import com.example.ezmakro.core.domain.model.NutritionGoal
import kotlinx.coroutines.flow.Flow

/**
 * Source for the user's daily nutrition targets shown on the dashboard.
 *
 * Offline-first (ADR-002): there is no remote source — goals are a local
 * preference. The dashboard always has a goal to compute remaining values
 * against (the [DefaultNutritionGoal] until the user configures their own).
 */
interface GoalRepository {
    /** Observe the active daily goal. Emits at least once immediately. */
    fun observeGoal(): Flow<NutritionGoal>

    /**
     * Persist the active daily goal. Throws [IllegalArgumentException] when the
     * [NutritionGoal] violates its invariants (e.g. negative values); callers
     * should validate before calling.
     */
    suspend fun saveGoal(goal: NutritionGoal)
}
