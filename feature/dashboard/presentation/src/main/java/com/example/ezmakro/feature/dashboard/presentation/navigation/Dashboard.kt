package com.example.ezmakro.feature.dashboard.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.ezmakro.feature.dashboard.presentation.DashboardRoot
import kotlinx.serialization.Serializable

@Serializable
object Dashboard

/**
 * Dashboard feature nav graph. Cross-feature destinations (diary, goals) are
 * expressed as lambda callbacks to keep features decoupled (android-navigation
 * skill): the [NavHost] in :app supplies them.
 */
fun NavGraphBuilder.dashboardGraph(
    onNavigateToDiary: (String) -> Unit,
    onNavigateToGoals: () -> Unit,
) {
    composable<Dashboard> {
        DashboardRoot(
            onNavigateToDiary = onNavigateToDiary,
            onNavigateToGoals = onNavigateToGoals,
        )
    }
}
