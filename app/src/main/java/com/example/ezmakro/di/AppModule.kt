package com.example.ezmakro.di

import com.example.ezmakro.core.barcode.di.barcodeModule
import com.example.ezmakro.core.data.di.backupDataModule
import com.example.ezmakro.core.data.di.coreDataModule
import com.example.ezmakro.core.data.di.diaryDataModule
import com.example.ezmakro.core.data.di.foodDataModule
import com.example.ezmakro.core.data.di.goalDataModule
import com.example.ezmakro.core.data.di.recipeDataModule
import com.example.ezmakro.core.database.di.databaseModule
import com.example.ezmakro.core.presentation.di.corePresentationModule
import com.example.ezmakro.diary.DiaryEntryAdder
import com.example.ezmakro.feature.diary.presentation.di.diaryPresentationModule
import com.example.ezmakro.feature.food.presentation.di.foodPresentationModule
import com.example.ezmakro.feature.foodpicker.presentation.di.foodPickerPresentationModule
import com.example.ezmakro.feature.recipes.presentation.di.recipePresentationModule
import com.example.ezmakro.feature.settings.presentation.settings.di.settingsPresentationModule
import org.koin.dsl.module

val appModule =
    module {
        includes(
            backupDataModule,
            barcodeModule,
            coreDataModule,
            corePresentationModule,
            databaseModule,
            diaryDataModule,
            foodDataModule,
            goalDataModule,
            recipeDataModule,
            foodPresentationModule,
            recipePresentationModule,
            diaryPresentationModule,
            foodPickerPresentationModule,
            settingsPresentationModule,
        )

        factory {
            DiaryEntryAdder(
                foodRepository = get(),
                recipeRepository = get(),
                addFoodToDiary = get(),
                addRecipeToDiary = get(),
            )
        }
    }
