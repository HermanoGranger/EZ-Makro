package com.example.ezmakro.feature.food.presentation.di

import androidx.lifecycle.SavedStateHandle
import com.example.ezmakro.feature.food.presentation.detail.FoodDetailViewModel
import com.example.ezmakro.feature.food.presentation.edit.FoodEditViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val foodPresentationModule =
    module {
        viewModel { (foodId: String) ->
            FoodDetailViewModel(
                foodId = foodId,
                repository = get(),
            )
        }
        viewModel { (foodId: String?, prefillBarcode: String?) ->
            FoodEditViewModel(
                foodId = foodId,
                prefillBarcode = prefillBarcode,
                repository = get(),
                savedStateHandle = get<SavedStateHandle>(),
            )
        }
    }
