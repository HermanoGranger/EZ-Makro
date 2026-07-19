package com.example.ezmakro.feature.dashboard.presentation.di

import com.example.ezmakro.feature.dashboard.presentation.DashboardViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Wires the Phase 8 dashboard presentation layer. The ViewModel receives the
 * diary and goal repositories (core-domain) via Koin.
 */
val dashboardPresentationModule =
    module {
        viewModel {
            DashboardViewModel(
                diaryRepository = get(),
                goalRepository = get(),
            )
        }
    }
