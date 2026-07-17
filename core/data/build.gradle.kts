plugins {
    alias(libs.plugins.ezmakro.android.library)
    alias(libs.plugins.ezmakro.ktor)
    alias(libs.plugins.ezmakro.koin)
}

android {
    namespace = "com.example.ezmakro.core.data"

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))

    implementation(libs.findLibrary("koin-android").get())
    implementation(libs.findLibrary("datastore-preferences").get())
    implementation(libs.findLibrary("kermit").get())
    implementation(libs.findLibrary("kotlinx-coroutines-android").get())

    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testImplementation(libs.findLibrary("ktor-client-mock").get())
    testImplementation(libs.findLibrary("androidx-junit").get())
    testImplementation(libs.findLibrary("assertk").get())
    testImplementation(libs.findLibrary("kotlin-testjunit5").get())
}
