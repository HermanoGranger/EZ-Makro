plugins {
    alias(libs.plugins.ezmakro.android.library)
    alias(libs.plugins.ezmakro.ktor)
    alias(libs.plugins.ezmakro.koin)
    alias(libs.plugins.ezmakro.kotlinx.serialization)
}

android {
    namespace = "com.example.ezmakro.core.data"

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    testImplementation(libs.findLibrary("room-testing").get())
    testImplementation(libs.findLibrary("androidx-test-core").get())
    testImplementation(libs.findLibrary("androidx-test-runner").get())
    testImplementation(libs.findLibrary("robolectric").get())

    // Explicit engines (pattern core/database/build.gradle.kts): without them
    // the Gradle/AGP wiring is undefined — and the Robolectric @RunWith tests
    // only run once the vintage engine is on the runtime classpath.
    testRuntimeOnly(libs.findLibrary("junit5-jupiter-engine").get())
    testRuntimeOnly(libs.findLibrary("junit5-vintage-engine").get())
    testRuntimeOnly(libs.findLibrary("junit5-platform-launcher").get())
}
