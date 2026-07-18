package com.example.ezmakro.core.data.di

import com.example.ezmakro.core.data.recipe.RoomRecipeLocalDataSource
import com.example.ezmakro.core.data.recipe.RoomRecipeRepository
import com.example.ezmakro.core.database.dao.RecipeDao
import com.example.ezmakro.core.domain.datasource.FoodLocalDataSource
import com.example.ezmakro.core.domain.datasource.RecipeLocalDataSource
import com.example.ezmakro.core.domain.repository.RecipeRepository
import org.koin.dsl.module

/**
 * Wires the Phase 6 recipe data layer: Room local source (with ingredient-food
 * resolution through the shared [FoodLocalDataSource]) and the offline-first
 * repository. Depends on [RecipeDao] (database module) and [FoodLocalDataSource]
 * (foodDataModule).
 */
val recipeDataModule =
    module {
        single<RecipeLocalDataSource> {
            RoomRecipeLocalDataSource(dao = get<RecipeDao>(), foodLocalDataSource = get())
        }
        single<RecipeRepository> {
            RoomRecipeRepository(local = get())
        }
    }
