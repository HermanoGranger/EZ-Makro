package com.example.ezmakro.core.data.di

import com.example.ezmakro.core.data.goal.InMemoryGoalRepository
import com.example.ezmakro.core.domain.repository.GoalRepository
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Wires the [GoalRepository]. Currently backed by [InMemoryGoalRepository]
 * (fixed default goal); Phase 9 will swap in a persisted implementation
 * behind the same interface without touching feature modules.
 */
val goalDataModule =
    module {
        single {
            InMemoryGoalRepository()
        } bind GoalRepository::class
    }
