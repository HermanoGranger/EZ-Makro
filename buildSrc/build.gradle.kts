plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(buildLogicLibs.android.gradle.plugin)
    implementation(buildLogicLibs.kotlin.gradle.plugin)
    implementation(buildLogicLibs.kotlin.compose.gradle.plugin)
    implementation(buildLogicLibs.kotlin.serialization.gradle.plugin)
    implementation(buildLogicLibs.ksp.gradle.plugin)
    implementation(buildLogicLibs.room.gradle.plugin)
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
        register("ktor") {
            id = "ezmakro.ktor"
            implementationClass = "KtorConventionPlugin"
        }
        register("room") {
            id = "ezmakro.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("kotlinxSerialization") {
            id = "ezmakro.kotlinx.serialization"
            implementationClass = "KotlinxSerializationConventionPlugin"
        }
    }
}
