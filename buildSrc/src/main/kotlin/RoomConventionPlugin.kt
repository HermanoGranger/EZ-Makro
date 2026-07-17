import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.android")
                apply("com.google.devtools.ksp")
                apply("androidx.room")
            }

            val extension = extensions.getByName("android") as LibraryExtension
            configureKotlinAndroid(extension)

            dependencies {
                add("implementation", libs.findLibrary("room-runtime").get())
                add("implementation", libs.findLibrary("room-ktx").get())
                add("ksp", libs.findLibrary("room-compiler").get())
                add("testImplementation", libs.findLibrary("room-testing").get())
            }
        }
    }
}
