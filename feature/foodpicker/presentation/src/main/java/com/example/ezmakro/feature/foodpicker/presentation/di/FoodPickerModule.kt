package com.example.ezmakro.feature.foodpicker.presentation.di

import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerTarget
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val foodPickerPresentationModule =
    module {
        viewModel { (target: FoodPickerTarget, targetDate: String?) ->
            FoodPickerViewModel(target, targetDate, get(), get(), get())
        }
    }
