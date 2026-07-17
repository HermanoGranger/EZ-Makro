plugins {
    alias(libs.plugins.ezmakro.android.library)
    alias(libs.plugins.ezmakro.compose)
}

android {
    namespace = "com.example.ezmakro.core.designsystem"
}

dependencies {
    implementation(project(":core:presentation"))

    implementation(libs.findLibrary("kotlinx-coroutines-android").get())
}
