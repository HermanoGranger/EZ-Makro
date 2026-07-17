package com.example.ezmakro.buildsrc

import org.gradle.api.tasks.TaskContainer

/**
 * AGP 9.x / Kotlin 2.2 UAST bug crashes lintAnalyze on library and app modules
 * (NPE in KotlinUastLanguagePlugin). Lint is not part of the local gate
 * (build + detekt + ktlint), so the tasks are disabled until the toolchain is
 * patched.
 */
fun disableLint(tasks: TaskContainer) {
    tasks.configureEach {
        if (name.startsWith("lint") || name.contains("Lint")) {
            enabled = false
        }
    }
}
