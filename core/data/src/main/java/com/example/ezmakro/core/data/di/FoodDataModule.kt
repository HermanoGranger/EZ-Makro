package com.example.ezmakro.core.data.di

import com.example.ezmakro.core.data.food.KtorFoodRemoteDataSource
import com.example.ezmakro.core.data.food.OfflineFirstFoodRepository
import com.example.ezmakro.core.data.food.RoomFoodLocalDataSource
import com.example.ezmakro.core.data.food.seed.AssetSeedFoodDataSource
import com.example.ezmakro.core.data.food.seed.BlsFoodSeeder
import com.example.ezmakro.core.data.food.seed.SeedFoodDataSource
import com.example.ezmakro.core.domain.datasource.FoodLocalDataSource
import com.example.ezmakro.core.domain.datasource.FoodRemoteDataSource
import com.example.ezmakro.core.domain.repository.FoodRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Wires the Phase 4 food data layer: OFF remote source, Room local source and
 * the offline-first repository. Depends on the shared `HttpClient` from
 * [coreDataModule] and the `FoodItemDao` from the database module.
 */
val foodDataModule =
    module {
        single<FoodRemoteDataSource> { KtorFoodRemoteDataSource(httpClient = get()) }
        single<FoodLocalDataSource> { RoomFoodLocalDataSource(dao = get()) }
        single<FoodRepository> {
            OfflineFirstFoodRepository(local = get(), remote = get())
        }
        single<SeedFoodDataSource> { AssetSeedFoodDataSource(context = androidContext()) }
        single { BlsFoodSeeder(seedSource = get(), dao = get(), settings = get()) }
    }
