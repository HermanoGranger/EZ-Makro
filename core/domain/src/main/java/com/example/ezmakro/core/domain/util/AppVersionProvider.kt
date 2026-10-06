package com.example.ezmakro.core.domain.util

/**
 * App version metadata written into every backup (`appVersionName`,
 * `appVersionCode`) so a preview can tell the user where the file came from.
 *
 * Backed by the platform package manager in `:core:data` — deliberately not by
 * `BuildConfig`, which would force `buildConfig = true` on the Gradle module.
 */
interface AppVersionProvider {
    val versionName: String

    val versionCode: Int
}
