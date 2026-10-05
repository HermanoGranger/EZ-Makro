package com.example.ezmakro.feature.diary.presentation.di

import com.example.ezmakro.core.domain.usecase.diary.AddFoodToDiaryUseCase
import com.example.ezmakro.core.domain.usecase.diary.AddRecipeToDiaryUseCase
import com.example.ezmakro.core.domain.usecase.diary.CopyYesterdayUseCase
import com.example.ezmakro.core.domain.usecase.diary.DeleteDiaryEntryUseCase
import com.example.ezmakro.core.domain.usecase.diary.EditDiaryRecipeEntryUseCase
import com.example.ezmakro.core.domain.usecase.diary.GetHistoryUseCase
import com.example.ezmakro.core.domain.usecase.diary.QuickAddUseCase
import com.example.ezmakro.core.domain.usecase.diary.UpdateDiaryEntryUseCase
import com.example.ezmakro.core.domain.usecase.recipe.BuildRecipeFromDraftsUseCase
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayViewModel
import com.example.ezmakro.feature.diary.presentation.goals.GoalsViewModel
import com.example.ezmakro.feature.diary.presentation.history.HistoryViewModel
import com.example.ezmakro.feature.diary.presentation.quickadd.QuickAddViewModel
import com.example.ezmakro.feature.diary.presentation.recipeentry.DiaryRecipeEditViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Wires the Phase 7 diary presentation layer. View models receive the
 * [DiaryRepository] and the diary use cases (core-domain) via Koin.
 */
val diaryPresentationModule =
    module {
        viewModel {
            DiaryDayViewModel(
                repository = get(),
                goalRepository = get(),
                recipeRepository = get(),
                copyYesterdayUseCase = get(),
                deleteEntryUseCase = get(),
                updateEntryUseCase = get(),
                currentDateProvider = get(),
                savedStateHandle = get(),
            )
        }
        viewModel { HistoryViewModel(get()) }
        viewModel { QuickAddViewModel(get(), get()) }
        viewModel { GoalsViewModel(get()) }

        viewModel {
            DiaryRecipeEditViewModel(
                entryId = get(),
                diaryRepository = get(),
                editDiaryRecipeEntryUseCase = get(),
                recipeRepository = get(),
                foodRepository = get(),
                buildRecipeFromDraftsUseCase = get(),
                savedStateHandle = get(),
            )
        }

        factory { AddFoodToDiaryUseCase(get()) }
        factory { AddRecipeToDiaryUseCase(get()) }
        factory { CopyYesterdayUseCase(get()) }
        factory { DeleteDiaryEntryUseCase(get()) }
        factory { EditDiaryRecipeEntryUseCase(get(), get()) }
        factory { GetHistoryUseCase(get()) }
        factory { QuickAddUseCase(get()) }
        factory { UpdateDiaryEntryUseCase(get()) }
        factory { BuildRecipeFromDraftsUseCase() }
    }
