plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(buildLogicLibs.android.gradle.plugin)
    implementation(buildLogicLibs.kotlin.gradle.plugin)
    implementation(buildLogicLibs.kotlin.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "ezmakro.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "ezmakro.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("domainModule") {
            id = "ezmakro.domain.module"
            implementationClass = "DomainModuleConventionPlugin"
        }
        register("compose") {
            id = "ezmakro.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("koin") {
            id = "ezmakro.koin"
            implementationClass = "KoinConventionPlugin"
        }
    }
}
