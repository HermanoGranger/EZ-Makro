package com.example.ezmakro.core.domain.repository

import com.example.ezmakro.core.domain.model.NutritionGoal
import kotlinx.coroutines.flow.Flow

/**
 * Source for the user's daily nutrition targets shown on the dashboard.
 *
 * In this phase the goal is delivered as a fixed default (see
 * [DefaultNutritionGoal]); Phase 9 introduces configurable, persisted goals and
 * will replace the source behind this same interface without touching the
 * presentation layer. Offline-first (ADR-002): no remote source, the dashboard
 * always has a goal to compute remaining values against.
 */
interface GoalRepository {
    /** Observe the active daily goal. Emits at least once immediately. */
    fun observeGoal(): Flow<NutritionGoal>
}
