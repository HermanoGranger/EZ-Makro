package com.example.ezmakro.core.data.di

import com.example.ezmakro.core.data.datastore.createSettingsDataStore
import com.example.ezmakro.core.data.network.HttpClientFactory
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Provides shared data-layer singletons: the configured [HttpClient] (engine
 * injected via Ktor's platform default) and the [SettingsDataStore]. Feature
 * modules depend on these rather than constructing their own clients.
 */
val coreDataModule =
    module {
        single<HttpClient> {
            HttpClientFactory.create(
                io.ktor.client.engine.okhttp.OkHttp
                    .create(),
            )
        }

        single {
            createSettingsDataStore(androidContext())
        }
    }
