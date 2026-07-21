package com.example.ezmakro.core.data.di

import com.example.ezmakro.core.data.goal.DataStoreGoalRepository
import com.example.ezmakro.core.domain.repository.GoalRepository
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Wires the [GoalRepository]. Backed by [DataStoreGoalRepository] so the
 * user's configured daily targets survive restarts (offline-first, ADR-002).
 */
val goalDataModule =
    module {
        single {
            DataStoreGoalRepository(settings = get())
        } bind GoalRepository::class
    }
