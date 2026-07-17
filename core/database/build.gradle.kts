plugins {
    alias(libs.plugins.ezmakro.android.library)
    alias(libs.plugins.ezmakro.room)
    alias(libs.plugins.ezmakro.koin)
}

android {
    namespace = "com.example.ezmakro.core.database"

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.findLibrary("koin-android").get())
    implementation(libs.findLibrary("kotlinx-coroutines-android").get())

    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testImplementation(libs.findLibrary("junit5-jupiter-api").get())
    testImplementation(libs.findLibrary("junit5-jupiter-engine").get())
    testImplementation(libs.findLibrary("junit5-vintage-engine").get())
    testImplementation(libs.findLibrary("junit5-platform-launcher").get())
    testImplementation(libs.findLibrary("assertk").get())
    testImplementation(libs.findLibrary("turbine").get())
    testImplementation(libs.findLibrary("room-testing").get())
    testImplementation(libs.findLibrary("androidx-test-core").get())
    testImplementation(libs.findLibrary("androidx-test-runner").get())
    testImplementation(libs.findLibrary("robolectric").get())
}
