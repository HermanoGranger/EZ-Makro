package com.example.ezmakro.feature.recipes.presentation.di

import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.feature.recipes.presentation.detail.RecipeDetailViewModel
import com.example.ezmakro.feature.recipes.presentation.editor.RecipeEditorViewModel
import com.example.ezmakro.feature.recipes.presentation.list.RecipeListViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Wires the Phase 6 recipe presentation layer. View models receive the
 * [RecipeRepository] (core-data) and, for the editor's ingredient picker, the
 * [FoodRepository] (core-data). The editor/detail view models take runtime
 * argument [recipeId] via Koin parameters.
 */
val recipePresentationModule =
    module {
        viewModelOf(::RecipeListViewModel)

        viewModel { (recipeId: String?) ->
            RecipeEditorViewModel(
                recipeId = recipeId,
                recipeRepository = get(),
                foodRepository = get(),
                savedStateHandle = get(),
            )
        }

        viewModel { (recipeId: String) ->
            RecipeDetailViewModel(
                recipeId = recipeId,
                repository = get(),
            )
        }
    }
