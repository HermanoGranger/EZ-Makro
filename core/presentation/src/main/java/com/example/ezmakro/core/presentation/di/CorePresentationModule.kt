package com.example.ezmakro.core.presentation.di

import com.example.ezmakro.core.presentation.date.CurrentDateProvider
import org.koin.dsl.module

val corePresentationModule =
    module {
        single { CurrentDateProvider() }
    }
