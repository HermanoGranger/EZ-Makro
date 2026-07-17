plugins {
    alias(libs.plugins.ezmakro.android.library)
    alias(libs.plugins.ezmakro.koin)
}

android {
    namespace = "com.example.ezmakro.core.barcode"

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.findLibrary("koin-android").get())
    implementation(libs.findLibrary("kermit").get())
    implementation(libs.findLibrary("kotlinx-coroutines-android").get())

    implementation(libs.findBundle("camerax").get())
    implementation(libs.findLibrary("mlkit-barcode-scanning").get())

    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testImplementation(libs.findLibrary("assertk").get())
    testImplementation(libs.findLibrary("kotlin-testjunit5").get())
}
