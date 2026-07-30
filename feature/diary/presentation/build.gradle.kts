plugins {
    alias(libs.plugins.ezmakro.android.library)
    alias(libs.plugins.ezmakro.compose)
    alias(libs.plugins.ezmakro.koin)
    alias(libs.plugins.ezmakro.kotlinx.serialization)
}

android {
    namespace = "com.example.ezmakro.feature.diary.presentation"

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
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))
    implementation(project(":feature:foodpicker:presentation"))

    implementation(libs.findLibrary("koin-android").get())
    implementation(libs.findLibrary("koin-androidx-compose").get())
    implementation(libs.findLibrary("kotlinx-coroutines-android").get())

    implementation(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    implementation(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    implementation(libs.findLibrary("androidx-navigation-compose").get())

    testImplementation(libs.findLibrary("junit5-jupiter-aggregator").get())
    testImplementation(libs.findLibrary("assertk").get())
    testImplementation(libs.findLibrary("turbine").get())
    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testRuntimeOnly(libs.findLibrary("junit5-jupiter-engine").get())
    testRuntimeOnly(libs.findLibrary("junit5-platform-launcher").get())

    androidTestImplementation(libs.findLibrary("androidx-junit").get())
    androidTestImplementation(libs.findLibrary("androidx-espresso-core").get())
    androidTestImplementation(libs.findLibrary("androidx-compose-ui-test-junit4").get())
    debugImplementation(libs.findLibrary("androidx-compose-ui-test-manifest").get())
}
