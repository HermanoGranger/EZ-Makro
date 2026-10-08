package com.example.ezmakro

import android.app.Application
import com.example.ezmakro.core.data.food.seed.BlsFoodSeeder
import com.example.ezmakro.core.data.logging.AppLogger
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class EzMakroApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@EzMakroApplication)
            modules(appModule)
        }
        applicationScope.launch {
            get<BlsFoodSeeder>().seedIfNeeded().onFailure {
                AppLogger.w("Startup food seed skipped: $it", tag = "EzMakroApplication")
            }
        }
    }
}
