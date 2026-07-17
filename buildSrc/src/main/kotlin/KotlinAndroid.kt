import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()

    commonExtension.defaultConfig.minSdk =
        libs.findVersion("minSdk").get().requiredVersion.toInt()

    commonExtension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    commonExtension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    tasks.withType(KotlinJvmCompile::class.java).configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}
