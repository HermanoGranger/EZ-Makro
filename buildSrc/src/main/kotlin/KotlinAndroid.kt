import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()

    commonExtension.defaultConfig.minSdk =
        libs.findVersion("minSdk").get().requiredVersion.toInt()

    commonExtension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    commonExtension.compileOptions.targetCompatibility = JavaVersion.VERSION_17
}
