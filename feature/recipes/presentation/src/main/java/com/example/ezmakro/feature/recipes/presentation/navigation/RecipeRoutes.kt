package com.example.ezmakro.feature.recipes.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.feature.recipes.presentation.detail.RecipeDetailRoot
import com.example.ezmakro.feature.recipes.presentation.editor.RecipeEditorRoot
import com.example.ezmakro.feature.recipes.presentation.list.RecipeListRoot
import kotlinx.serialization.Serializable

// Route types — kept for serialization tests and potential future re-use.
// Navigation uses explicit string routes (below) to avoid
// KClass.serializer() companion-resolution edge case in Navigation 2.9.

@Serializable
object Recipe

@Serializable
data class RecipeList(
    val addToDiaryDate: String? = null,
)

@Serializable
data class RecipeDetail(
    val recipeId: String,
)

@Serializable
data class RecipeEditor(
    val recipeId: String? = null,
)

const val ROUTE_RECIPES = "recipes"
const val ROUTE_RECIPES_PARAM_DATE = "addToDiaryDate"
const val ROUTE_LIST = "recipe_list"

// Route patterns for composable registration (include {param} placeholders)
const val ROUTE_DETAIL_PATTERN = "recipe_detail/{recipeId}?slot={slot}&date={date}"
const val ROUTE_EDITOR_PATTERN = "recipe_editor/{recipeId}"

// Route prefixes for navigation (no {param} placeholders; append values directly)
const val ROUTE_DETAIL = "recipe_detail"
const val ROUTE_EDITOR = "recipe_editor"
const val ROUTE_DETAIL_PARAM_ID = "recipeId"
const val ROUTE_EDITOR_PARAM_ID = "recipeId"
internal const val ROUTE_DETAIL_PARAM_SLOT = "slot"
internal const val ROUTE_DETAIL_PARAM_DATE = "date"

fun NavGraphBuilder.recipeGraph(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onAddToDiary: (
        String,
        String,
        MealSlot,
        Double,
        Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
    onNavigateToFoodCreate: (barcode: String?) -> Unit = {},
    onNavigateToFoodDetail: (String) -> Unit = {},
    onNavigateToRecipeDetail: (String) -> Unit = {},
) {
    navigation(startDestination = ROUTE_LIST, route = ROUTE_RECIPES) {
        listComposable(navController, onAddToDiary)
        detailComposable(navController, onNavigateBack, onAddToDiary)
        editorComposable(
            navController,
            onNavigateBack,
            onNavigateToFoodCreate,
            onNavigateToFoodDetail,
            onNavigateToRecipeDetail,
        )
    }
}

private fun NavGraphBuilder.listComposable(
    navController: NavHostController,
    onAddToDiary: (String, String, com.example.ezmakro.core.domain.model.MealSlot, Double, Boolean) -> Unit,
) {
    composable(
        route = "$ROUTE_LIST?$ROUTE_RECIPES_PARAM_DATE={$ROUTE_RECIPES_PARAM_DATE}",
        arguments =
            listOf(
                navArgument(ROUTE_RECIPES_PARAM_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        RecipeListRoot(
            onNavigateToDetail = { recipeId -> navController.navigate("$ROUTE_DETAIL/$recipeId") },
            onNavigateToEditor = { recipeId ->
                navController.navigate("$ROUTE_EDITOR/${recipeId ?: "_none_"}")
            },
            onAddToDiary = onAddToDiary,
        )
    }
}

private fun NavGraphBuilder.detailComposable(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit,
) {
    composable(
        route = ROUTE_DETAIL_PATTERN,
        arguments =
            listOf(
                navArgument(ROUTE_DETAIL_PARAM_ID) { type = NavType.StringType },
                navArgument(ROUTE_DETAIL_PARAM_SLOT) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(ROUTE_DETAIL_PARAM_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        val recipeId = it.arguments?.getString(ROUTE_DETAIL_PARAM_ID) ?: return@composable
        val slotArg = it.arguments?.getString(ROUTE_DETAIL_PARAM_SLOT).orEmpty()
        val date = it.arguments?.getString(ROUTE_DETAIL_PARAM_DATE).orEmpty()
        RecipeDetailRoot(
            recipeId = recipeId,
            initialSlot = MealSlot.entries.firstOrNull { slot -> slot.name == slotArg },
            date = date,
            onNavigateBack = onNavigateBack,
            onNavigateToEdit = { id -> navController.navigate("$ROUTE_EDITOR/$id") },
            onAddToDiary = onAddToDiary,
        )
    }
}

private fun NavGraphBuilder.editorComposable(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onNavigateToFoodCreate: (barcode: String?) -> Unit,
    onNavigateToFoodDetail: (String) -> Unit,
    onNavigateToRecipeDetail: (String) -> Unit,
) {
    composable(
        route = ROUTE_EDITOR_PATTERN,
        arguments =
            listOf(
                navArgument(ROUTE_EDITOR_PARAM_ID) {
                    type = NavType.StringType
                    defaultValue = "_none_"
                },
            ),
    ) {
        val recipeId =
            it.arguments?.getString(ROUTE_EDITOR_PARAM_ID)?.takeIf { it != "_none_" }
        RecipeEditorRoot(
            recipeId = recipeId,
            onNavigateBack = onNavigateBack,
            onSaved = { navController.popBackStack() },
            onNavigateToFoodCreate = onNavigateToFoodCreate,
            onNavigateToFoodDetail = onNavigateToFoodDetail,
            onNavigateToRecipeDetail = onNavigateToRecipeDetail,
        )
    }
}
