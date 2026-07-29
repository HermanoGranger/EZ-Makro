plugins {
    alias(libs.plugins.ezmakro.android.library)
    alias(libs.plugins.ezmakro.compose)
    alias(libs.plugins.ezmakro.koin)
}

android {
    namespace = "com.example.ezmakro.core.presentation"
    defaultConfig {
        minSdk =
            libs
                .findVersion("minSdk")
                .get()
                .requiredVersion
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.findLibrary("kotlinx-coroutines-android").get())

    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testImplementation(libs.findLibrary("androidx-junit").get())
    testImplementation(libs.findLibrary("assertk").get())
}
