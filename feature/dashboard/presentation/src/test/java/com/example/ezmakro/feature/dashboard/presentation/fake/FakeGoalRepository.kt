package com.example.ezmakro.feature.dashboard.presentation.fake

import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGoalRepository(
    initial: NutritionGoal,
) : GoalRepository {
    private val goal = MutableStateFlow(initial)
    val savedGoals = mutableListOf<NutritionGoal>()

    override fun observeGoal(): Flow<NutritionGoal> = goal

    override suspend fun saveGoal(goal: NutritionGoal) {
        savedGoals += goal
        this.goal.value = goal
    }

    fun setGoal(goal: NutritionGoal) {
        this.goal.value = goal
    }
}
