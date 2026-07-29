package com.example.ezmakro.feature.recipes.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
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
    val isQuickMeal: Boolean = false,
)

const val ROUTE_RECIPES = "recipes"
const val ROUTE_RECIPES_PARAM_DATE = "addToDiaryDate"
const val ROUTE_LIST = "recipe_list"

// Route patterns for composable registration (include {param} placeholders)
const val ROUTE_DETAIL_PATTERN = "recipe_detail/{recipeId}"
const val ROUTE_EDITOR_PATTERN = "recipe_editor/{recipeId}?isQuickMeal={isQuickMeal}"

// Route prefixes for navigation (no {param} placeholders; append values directly)
const val ROUTE_DETAIL = "recipe_detail"
const val ROUTE_EDITOR = "recipe_editor"
const val ROUTE_DETAIL_PARAM_ID = "recipeId"
const val ROUTE_EDITOR_PARAM_ID = "recipeId"
const val ROUTE_EDITOR_PARAM_QUICK = "isQuickMeal"

fun NavGraphBuilder.recipeGraph(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onAddToDiary: (
        String,
        String,
        com.example.ezmakro.core.domain.model.MealSlot,
        Double,
        Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
) {
    navigation(startDestination = ROUTE_LIST, route = ROUTE_RECIPES) {
        listComposable(navController, onAddToDiary)
        detailComposable(navController, onNavigateBack)
        editorComposable(navController, onNavigateBack)
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
            onNavigateToQuickMeal = { navController.navigate("$ROUTE_EDITOR/_none_?$ROUTE_EDITOR_PARAM_QUICK=true") },
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
        val recipeId = it.arguments?.getString(ROUTE_DETAIL_PARAM_ID) ?: return@composable
        RecipeDetailRoot(
            recipeId = recipeId,
            onNavigateBack = onNavigateBack,
            onNavigateToEdit = { id -> navController.navigate("$ROUTE_EDITOR/$id") },
        )
    }
}

private fun NavGraphBuilder.editorComposable(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
) {
    composable(
        route = ROUTE_EDITOR_PATTERN,
        arguments =
            listOf(
                navArgument(ROUTE_EDITOR_PARAM_ID) {
                    type = NavType.StringType
                    defaultValue = "_none_"
                },
                navArgument(ROUTE_EDITOR_PARAM_QUICK) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) {
        val recipeId =
            it.arguments?.getString(ROUTE_EDITOR_PARAM_ID)?.takeIf { it != "_none_" }
        val isQuickMeal =
            it.arguments?.getBoolean(ROUTE_EDITOR_PARAM_QUICK) ?: false
        RecipeEditorRoot(
            recipeId = recipeId,
            isQuickMeal = isQuickMeal,
            onNavigateBack = onNavigateBack,
            onSaved = { navController.popBackStack() },
        )
    }
}
