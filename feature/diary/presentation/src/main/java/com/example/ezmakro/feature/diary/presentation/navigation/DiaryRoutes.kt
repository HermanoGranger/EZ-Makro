package com.example.ezmakro.feature.diary.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.util.encodeRouteParam
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayRoot
import com.example.ezmakro.feature.diary.presentation.history.HistoryRoot
import com.example.ezmakro.feature.diary.presentation.quickadd.QuickAddRoot
import com.example.ezmakro.feature.diary.presentation.recipeentry.DiaryRecipeEditRoot
import kotlinx.serialization.Serializable

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
    val slot: String? = null,
)

// ---- Public string route constants ----

const val ROUTE_DIARY = "diary"
const val ROUTE_DIARY_DAY_PARAM_DATE = "date"

const val ROUTE_DIARY_DAY = "diary_day"
internal const val ROUTE_QUICK_ADD = "quick_add"
internal const val ROUTE_QUICK_ADD_PARAM_SLOT = "slot"
internal const val ROUTE_HISTORY = "history"
internal const val ROUTE_DIARY_RECIPE_EDIT = "diary_recipe_edit"
internal const val ROUTE_DIARY_RECIPE_EDIT_PARAM_ENTRY_ID = "entryId"

/**
 * String-based diary feature navigation graph.
 *
 * Uses explicit string routes for all destinations to avoid kotlinx.serialization
 * `KClass.serializer()` companion resolution edge case in Navigation 2.9.
 * The graph-level route [ROUTE_DIARY] matches the bottom nav route.
 */
fun NavGraphBuilder.diaryGraph(
    navController: NavHostController,
    onAddToDiaryFromPicker: (
        foodId: String,
        isRecipe: Boolean,
        date: String,
        slot: MealSlot,
        amount: Double,
        isGrams: Boolean,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToFoodCreate: (barcode: String?) -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToFoodDetail: (String, MealSlot, String) -> Unit = { _, _, _ -> },
    onNavigateToRecipeDetail: (String, MealSlot, String) -> Unit = { _, _, _ -> },
) {
    navigation(startDestination = ROUTE_DIARY_DAY, route = ROUTE_DIARY) {
        dayComposable(
            navController,
            onAddToDiaryFromPicker,
            onNavigateToFoodCreate,
            onNavigateToGoals,
            onNavigateToFoodDetail,
            onNavigateToRecipeDetail,
        )
        quickAddComposable(navController)
        historyComposable(navController)
        recipeEditComposable(navController)
    }
}

private fun NavGraphBuilder.dayComposable(
    navController: NavHostController,
    onAddToDiaryFromPicker: (
        foodId: String,
        isRecipe: Boolean,
        date: String,
        slot: MealSlot,
        amount: Double,
        isGrams: Boolean,
    ) -> Unit,
    onNavigateToFoodCreate: (barcode: String?) -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToFoodDetail: (String, MealSlot, String) -> Unit,
    onNavigateToRecipeDetail: (String, MealSlot, String) -> Unit,
) {
    composable(
        route = "$ROUTE_DIARY_DAY?$ROUTE_DIARY_DAY_PARAM_DATE={$ROUTE_DIARY_DAY_PARAM_DATE}",
        arguments =
            listOf(
                navArgument(ROUTE_DIARY_DAY_PARAM_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        DiaryDayRoot(
            onAddToDiaryFromPicker = onAddToDiaryFromPicker,
            onNavigateToQuickAdd = { dateParam, slot ->
                navController.navigate("$ROUTE_QUICK_ADD?date=$dateParam&${ROUTE_QUICK_ADD_PARAM_SLOT}=${slot.name}")
            },
            onNavigateToHistory = { navController.navigate(ROUTE_HISTORY) },
            onNavigateToGoals = onNavigateToGoals,
            onNavigateToFoodCreate = onNavigateToFoodCreate,
            onNavigateToFoodDetail = onNavigateToFoodDetail,
            onNavigateToRecipeDetail = onNavigateToRecipeDetail,
            onNavigateToRecipeEdit = { entryId ->
                navController.navigate("$ROUTE_DIARY_RECIPE_EDIT/${encodeRouteParam(entryId)}")
            },
        )
    }
}

private fun NavGraphBuilder.quickAddComposable(navController: NavHostController) {
    composable(
        route =
            "$ROUTE_QUICK_ADD?$ROUTE_DIARY_DAY_PARAM_DATE={$ROUTE_DIARY_DAY_PARAM_DATE}" +
                "&$ROUTE_QUICK_ADD_PARAM_SLOT={$ROUTE_QUICK_ADD_PARAM_SLOT}",
        arguments =
            listOf(
                navArgument(ROUTE_DIARY_DAY_PARAM_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(ROUTE_QUICK_ADD_PARAM_SLOT) {
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

private fun NavGraphBuilder.recipeEditComposable(navController: NavHostController) {
    composable(
        route = "$ROUTE_DIARY_RECIPE_EDIT/{$ROUTE_DIARY_RECIPE_EDIT_PARAM_ENTRY_ID}",
        arguments =
            listOf(
                navArgument(ROUTE_DIARY_RECIPE_EDIT_PARAM_ENTRY_ID) {
                    type = NavType.StringType
                },
            ),
    ) { backStackEntry ->
        val entryId = backStackEntry.arguments?.getString(ROUTE_DIARY_RECIPE_EDIT_PARAM_ENTRY_ID) ?: ""
        DiaryRecipeEditRoot(
            entryId = entryId,
            onSaved = { navController.popBackStack() },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
