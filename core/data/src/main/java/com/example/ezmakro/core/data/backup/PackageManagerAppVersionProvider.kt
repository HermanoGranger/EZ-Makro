package com.example.ezmakro.core.data.backup

import android.content.Context
import android.content.pm.PackageInfo
import com.example.ezmakro.core.domain.util.AppVersionProvider

/**
 * Reads the version metadata of the installed APK for the backup frame
 * (`appVersionName` / `appVersionCode`).
 *
 * Deliberately package-manager based instead of `BuildConfig`, which would
 * force `buildConfig = true` on this Gradle module (WP3 Vorgaben).
 */
class PackageManagerAppVersionProvider(
    private val context: Context,
) : AppVersionProvider {
    private val packageInfo: PackageInfo by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    override val versionName: String
        get() = packageInfo.versionName.orEmpty()

    @Suppress("DEPRECATION")
    override val versionCode: Int
        get() = packageInfo.versionCode
}
