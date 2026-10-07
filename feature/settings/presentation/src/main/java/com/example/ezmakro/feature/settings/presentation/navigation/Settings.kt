package com.example.ezmakro.feature.settings.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.ezmakro.feature.settings.presentation.settings.SettingsRoot
import com.example.ezmakro.feature.settings.presentation.settings.backup.BackupRoot
import kotlinx.serialization.Serializable

// Route type — kept for serialization tests and potential future re-use.
// Navigation uses explicit string routes (below) to avoid the
// KClass.serializer() companion-resolution edge case in Navigation 2.9,
// the same pattern as DiaryRoutes.kt:17-19.

@Serializable
object Settings

// ---- Public string route constants ----

const val ROUTE_SETTINGS = "settings"
const val ROUTE_SETTINGS_BACKUP = "settings/backup"

/**
 * Settings feature nav graph: the settings hub plus the backup screen.
 * Cross-feature destinations (goals) are expressed as callbacks and wired in
 * `:app` — this module knows nothing about `:feature:diary`.
 */
fun NavGraphBuilder.settingsGraph(
    onNavigateBack: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToBackup: () -> Unit,
) {
    composable(ROUTE_SETTINGS) {
        SettingsRoot(
            onNavigateBack = onNavigateBack,
            onNavigateToGoals = onNavigateToGoals,
            onNavigateToBackup = onNavigateToBackup,
        )
    }
    composable(ROUTE_SETTINGS_BACKUP) {
        BackupRoot(onNavigateBack = onNavigateBack)
    }
}
