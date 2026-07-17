dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("buildLogicLibs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
