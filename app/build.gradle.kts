plugins {
    alias(libs.plugins.ezmakro.android.application)
    alias(libs.plugins.ezmakro.compose)
    alias(libs.plugins.ezmakro.koin)
    alias(libs.plugins.ezmakro.kotlinx.serialization)
}

android {
    namespace = "com.example.ezmakro"

    defaultConfig {
        applicationId = "com.example.ezmakro"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:barcode"))
    implementation(project(":core:design-system"))
    implementation(project(":feature:food:presentation"))
    implementation(project(":feature:recipes:presentation"))
    implementation(project(":feature:diary:presentation"))
    implementation(project(":feature:dashboard:presentation"))
    implementation(project(":feature:foodpicker:presentation"))

    implementation(libs.findLibrary("androidx-core-ktx").get())
    implementation(libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
    implementation(libs.findLibrary("androidx-activity-compose").get())
    implementation(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    implementation(libs.findLibrary("androidx-navigation-compose").get())

    implementation(libs.findLibrary("koin-android").get())
    implementation(libs.findLibrary("koin-androidx-compose").get())

    testImplementation(libs.findLibrary("junit5-jupiter-aggregator").get())
    testImplementation(libs.findLibrary("assertk").get())
    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testRuntimeOnly(libs.findLibrary("junit5-jupiter-engine").get())
    testRuntimeOnly(libs.findLibrary("junit5-platform-launcher").get())

    androidTestImplementation(libs.findLibrary("androidx-junit").get())
    androidTestImplementation(libs.findLibrary("androidx-espresso-core").get())
    androidTestImplementation(libs.findLibrary("androidx-compose-ui-test-junit4").get())
    debugImplementation(libs.findLibrary("androidx-compose-ui-test-manifest").get())
}
