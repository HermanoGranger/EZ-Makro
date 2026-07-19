package com.example.ezmakro.core.data.goal

import com.example.ezmakro.core.domain.model.DefaultNutritionGoal
import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Placeholder [GoalRepository] that emits the fixed [DefaultNutritionGoal].
 *
 * It intentionally exposes a [MutableStateFlow] so callers (and, later, Phase 9's
 * settings screen) can update the active goal without a remote or disk source.
 * Phase 9 replaces this with a persisted implementation behind the same
 * interface; the presentation layer stays unchanged.
 */
class InMemoryGoalRepository(
    initial: NutritionGoal = DefaultNutritionGoal,
) : GoalRepository {
    private val goal = MutableStateFlow(initial)

    override fun observeGoal(): Flow<NutritionGoal> = goal

    /** Replace the active goal (used once Phase 9 adds goal editing). */
    fun setGoal(goal: NutritionGoal) {
        this.goal.update { goal }
    }
}
