package com.example.ezmakro

import android.app.Application
import com.example.ezmakro.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class EzMakroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@EzMakroApplication)
            modules(appModule)
        }
    }
}
