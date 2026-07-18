package com.example.ezmakro.di

import com.example.ezmakro.core.barcode.di.barcodeModule
import com.example.ezmakro.core.data.di.coreDataModule
import com.example.ezmakro.core.data.di.foodDataModule
import com.example.ezmakro.core.data.di.recipeDataModule
import com.example.ezmakro.core.database.di.databaseModule
import com.example.ezmakro.feature.food.presentation.di.foodPresentationModule
import com.example.ezmakro.feature.recipes.presentation.di.recipePresentationModule
import org.koin.dsl.module

val appModule =
    module {
        includes(
            databaseModule,
            coreDataModule,
            foodDataModule,
            recipeDataModule,
            barcodeModule,
            foodPresentationModule,
            recipePresentationModule,
        )
    }
