package com.example.ezmakro.feature.settings.presentation.settings.di

import com.example.ezmakro.feature.settings.presentation.settings.backup.BackupViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Wires the settings presentation layer. The settings hub itself is
 * stateless, so only the backup screen has a view model.
 */
val settingsPresentationModule =
    module {
        viewModel { BackupViewModel(get()) }
    }
