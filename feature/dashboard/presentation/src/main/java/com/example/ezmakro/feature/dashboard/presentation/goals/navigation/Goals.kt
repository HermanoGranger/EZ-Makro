package com.example.ezmakro.feature.dashboard.presentation.goals.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.ezmakro.feature.dashboard.presentation.goals.GoalsRoot
import kotlinx.serialization.Serializable

@Serializable
object Goals

/**
 * Goals feature nav graph. Expresses no cross-feature destinations; it only
 * pops back to the dashboard via the host-supplied [onNavigateBack] callback
 * (features stay decoupled, see android-navigation skill).
 */
fun NavGraphBuilder.goalsGraph(onNavigateBack: () -> Unit) {
    composable<Goals> {
        GoalsRoot(onNavigateBack = onNavigateBack)
    }
}
