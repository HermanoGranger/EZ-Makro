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

    testImplementation(libs.findLibrary("junit5-jupiter-aggregator").get())
    testImplementation(libs.findLibrary("assertk").get())
    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testRuntimeOnly(libs.findLibrary("junit5-jupiter-engine").get())
    testRuntimeOnly(libs.findLibrary("junit5-platform-launcher").get())
}
