package com.example.ezmakro.feature.food.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.feature.food.presentation.detail.FoodDetailRoot
import com.example.ezmakro.feature.food.presentation.edit.FoodEditRoot
import com.example.ezmakro.feature.food.presentation.search.FoodSearchRoot
import kotlinx.serialization.Serializable

// Route types — kept for serialization tests and potential future re-use.
// Navigation uses explicit string routes (below) to avoid
// KClass.serializer() companion-resolution edge case in Navigation 2.9.

@Serializable
object FoodSearch

@Serializable
data class FoodDetail(
    val foodId: String,
)

@Serializable
data class FoodEdit(
    val foodId: String? = null,
    val prefillBarcode: String? = null,
)

/**
 * Graph-level route for bottom navigation compatibility.
 */
@Serializable
object Food

// ---- Public string route constants ----

const val ROUTE_FOOD = "food"
const val ROUTE_SEARCH = "food_search"

// Route patterns for composable registration (include {param} placeholders)
internal const val ROUTE_DETAIL_PATTERN = "food_detail/{foodId}"
internal const val ROUTE_EDIT_PATTERN = "food_edit/{foodId}?prefillBarcode={prefillBarcode}"

// Route prefixes for navigation (no {param} placeholders; append values directly)
internal const val ROUTE_DETAIL = "food_detail"
internal const val ROUTE_EDIT = "food_edit"
internal const val ROUTE_DETAIL_PARAM_ID = "foodId"
internal const val ROUTE_EDIT_PARAM_ID = "foodId"
internal const val ROUTE_EDIT_PARAM_BARCODE = "prefillBarcode"

internal const val EDIT_ROUTE_MISSING_ID = "_none_"

/**
 * String-based food feature navigation graph.
 *
 * Uses explicit string routes for all destinations to avoid kotlinx.serialization
 * `KClass.serializer()` companion resolution edge case in Navigation 2.9.
 * The graph-level route [ROUTE_FOOD] matches [Food]'s serial name for bottom nav.
 */
fun NavGraphBuilder.foodGraph(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit = { _, _, _, _, _ -> },
) {
    navigation(startDestination = ROUTE_SEARCH, route = ROUTE_FOOD) {
        searchComposable(navController, onAddToDiary)
        detailComposable(navController, onNavigateBack)
        editComposable(navController, onNavigateBack)
    }
}

private fun NavGraphBuilder.searchComposable(
    navController: NavHostController,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit,
) {
    composable(route = ROUTE_SEARCH) {
        FoodSearchRoot(
            onNavigateToDetail = { foodId -> navController.navigate("$ROUTE_DETAIL/$foodId") },
            onNavigateToCreate = { barcode ->
                val route =
                    if (barcode != null) {
                        "$ROUTE_EDIT/$EDIT_ROUTE_MISSING_ID?$ROUTE_EDIT_PARAM_BARCODE=$barcode"
                    } else {
                        "$ROUTE_EDIT/$EDIT_ROUTE_MISSING_ID"
                    }
                navController.navigate(route)
            },
            onAddToDiary = onAddToDiary,
        )
    }
}

private fun NavGraphBuilder.detailComposable(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
) {
    composable(
        route = ROUTE_DETAIL_PATTERN,
        arguments = listOf(navArgument(ROUTE_DETAIL_PARAM_ID) { type = NavType.StringType }),
    ) {
        val foodId = it.arguments?.getString(ROUTE_DETAIL_PARAM_ID) ?: return@composable
        FoodDetailRoot(
            foodId = foodId,
            onNavigateBack = onNavigateBack,
            onNavigateToEdit = { id -> navController.navigate("$ROUTE_EDIT/$id") },
        )
    }
}

private fun NavGraphBuilder.editComposable(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
) {
    composable(
        route = ROUTE_EDIT_PATTERN,
        arguments =
            listOf(
                navArgument(ROUTE_EDIT_PARAM_ID) {
                    type = NavType.StringType
                    defaultValue = EDIT_ROUTE_MISSING_ID
                },
                navArgument(ROUTE_EDIT_PARAM_BARCODE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        val foodId =
            it.arguments?.getString(ROUTE_EDIT_PARAM_ID)?.takeIf { it != EDIT_ROUTE_MISSING_ID }
        val prefillBarcode =
            it.arguments?.getString(ROUTE_EDIT_PARAM_BARCODE)?.takeIf { it.isNotEmpty() }
        FoodEditRoot(
            foodId = foodId,
            prefillBarcode = prefillBarcode,
            onNavigateBack = onNavigateBack,
            onSaved = { navController.popBackStack() },
        )
    }
}
