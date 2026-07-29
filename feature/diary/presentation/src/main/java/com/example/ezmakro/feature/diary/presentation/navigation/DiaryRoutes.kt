package com.example.ezmakro.feature.diary.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayRoot
import com.example.ezmakro.feature.diary.presentation.history.HistoryRoot
import com.example.ezmakro.feature.diary.presentation.quickadd.QuickAddRoot
import kotlinx.serialization.Serializable
import java.time.LocalDate

// Route types — kept for serialization tests and potential future re-use.
// Navigation uses explicit string routes (below) to avoid
// KClass.serializer() companion-resolution edge case in Navigation 2.9.

@Serializable
object Diary

@Serializable
data class DiaryDay(
    val date: String? = null,
)

@Serializable
object History

@Serializable
data class QuickAdd(
    val date: String? = null,
)

// ---- Public string route constants ----

const val ROUTE_DIARY = "diary"
const val ROUTE_DIARY_DAY_PARAM_DATE = "date"

const val ROUTE_DIARY_DAY = "diary_day"
internal const val ROUTE_QUICK_ADD = "quick_add"
internal const val ROUTE_HISTORY = "history"

/**
 * String-based diary feature navigation graph.
 *
 * Uses explicit string routes for all destinations to avoid kotlinx.serialization
 * `KClass.serializer()` companion resolution edge case in Navigation 2.9.
 * The graph-level route [ROUTE_DIARY] matches the bottom nav route.
 */
fun NavGraphBuilder.diaryGraph(
    navController: NavHostController,
    onNavigateToAddFood: (String) -> Unit,
    onNavigateToAddRecipe: (String) -> Unit,
) {
    navigation(startDestination = ROUTE_DIARY_DAY, route = ROUTE_DIARY) {
        dayComposable(navController, onNavigateToAddFood, onNavigateToAddRecipe)
        quickAddComposable(navController)
        historyComposable(navController)
    }
}

private fun NavGraphBuilder.dayComposable(
    navController: NavHostController,
    onNavigateToAddFood: (String) -> Unit,
    onNavigateToAddRecipe: (String) -> Unit,
) {
    composable(
        route = ROUTE_DIARY_DAY,
        arguments =
            listOf(
                navArgument(ROUTE_DIARY_DAY_PARAM_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        val date =
            it.arguments?.getString(ROUTE_DIARY_DAY_PARAM_DATE)?.takeIf { d -> d.isNotEmpty() }
        DiaryDayRoot(
            onNavigateToAddFood = onNavigateToAddFood,
            onNavigateToAddRecipe = onNavigateToAddRecipe,
            onNavigateToQuickAdd = {
                val dateParam = date ?: LocalDate.now().toString()
                navController.navigate("$ROUTE_QUICK_ADD?date=$dateParam")
            },
            onNavigateToHistory = { navController.navigate(ROUTE_HISTORY) },
        )
    }
}

private fun NavGraphBuilder.quickAddComposable(navController: NavHostController) {
    composable(
        route = ROUTE_QUICK_ADD,
        arguments =
            listOf(
                navArgument(ROUTE_DIARY_DAY_PARAM_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        QuickAddRoot(
            onSaved = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.historyComposable(navController: NavHostController) {
    composable(route = ROUTE_HISTORY) {
        HistoryRoot(
            onDayClick = { date -> navController.navigate("$ROUTE_DIARY_DAY?date=$date") },
        )
    }
}
