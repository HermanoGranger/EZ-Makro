package com.example.ezmakro.feature.food.presentation.di

import androidx.lifecycle.SavedStateHandle
import com.example.ezmakro.feature.food.presentation.edit.FoodEditViewModel
import com.example.ezmakro.feature.food.presentation.scan.BarcodeScanViewModel
import com.example.ezmakro.feature.food.presentation.search.FoodSearchViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val foodPresentationModule =
    module {
        viewModelOf(::FoodSearchViewModel)
        viewModel { (foodId: String) ->
            com.example.ezmakro.feature.food.presentation.detail.FoodDetailViewModel(
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
        viewModel { BarcodeScanViewModel(repository = get()) }
    }
