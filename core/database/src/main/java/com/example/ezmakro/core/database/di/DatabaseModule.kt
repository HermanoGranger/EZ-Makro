package com.example.ezmakro.core.database.di

import androidx.room.Room
import com.example.ezmakro.core.database.EZDatabase
import com.example.ezmakro.core.database.migration.ALL_MIGRATIONS
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module wiring the Room database and exposing its DAOs.
 *
 * The database is built once and reused (ADR-002: Room = Single Source of Truth).
 */
@Suppress("SpreadOperator")
val databaseModule =
    module {
        single<EZDatabase> {
            Room
                .databaseBuilder(
                    context = androidContext(),
                    klass = EZDatabase::class.java,
                    name = EZDatabase.DB_NAME,
                ).addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigration()
                .build()
        }

        single { get<EZDatabase>().foodItemDao() }
        single { get<EZDatabase>().recipeDao() }
        single { get<EZDatabase>().diaryEntryDao() }
        single { get<EZDatabase>().weightLogDao() }
    }
