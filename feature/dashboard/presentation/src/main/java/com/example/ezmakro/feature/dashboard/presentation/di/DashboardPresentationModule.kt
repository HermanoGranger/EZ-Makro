package com.example.ezmakro.feature.dashboard.presentation.di

import com.example.ezmakro.feature.dashboard.presentation.DashboardViewModel
import com.example.ezmakro.feature.dashboard.presentation.goals.GoalsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Wires the dashboard + goals presentation layer. ViewModels receive their
 * repositories (core-domain) via Koin.
 */
val dashboardPresentationModule =
    module {
        viewModel {
            DashboardViewModel(
                diaryRepository = get(),
                goalRepository = get(),
            )
        }
        viewModel { GoalsViewModel(repository = get()) }
    }
