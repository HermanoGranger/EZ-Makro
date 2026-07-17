plugins {
    alias(libs.plugins.ezmakro.domain.module)
}

dependencies {
    api(libs.findLibrary("kotlinx-coroutines-core").get())
}
