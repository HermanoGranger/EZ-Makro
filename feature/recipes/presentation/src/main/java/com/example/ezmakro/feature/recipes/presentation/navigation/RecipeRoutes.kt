package com.example.ezmakro.feature.recipes.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.ezmakro.feature.recipes.presentation.detail.RecipeDetailRoot
import com.example.ezmakro.feature.recipes.presentation.editor.RecipeEditorRoot
import com.example.ezmakro.feature.recipes.presentation.list.RecipeListRoot
import kotlinx.serialization.Serializable

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

fun NavGraphBuilder.recipeGraph(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onAddToDiary: (String, String, com.example.ezmakro.core.domain.model.MealSlot, Double) -> Unit = { _, _, _, _ -> },
) {
    navigation<RecipeList>(startDestination = RecipeList) {
        composable<RecipeList> { backStackEntry ->
            val route = backStackEntry.toRoute<RecipeList>()
            RecipeListRoot(
                onNavigateToDetail = { recipeId -> navController.navigate(RecipeDetail(recipeId)) },
                onNavigateToEditor = { recipeId ->
                    navController.navigate(RecipeEditor(recipeId = recipeId))
                },
                onNavigateToQuickMeal = { navController.navigate(RecipeEditor(isQuickMeal = true)) },
                onAddToDiary = onAddToDiary,
            )
        }
        composable<RecipeDetail> { backStackEntry ->
            val recipeId = backStackEntry.toRoute<RecipeDetail>().recipeId
            RecipeDetailRoot(
                recipeId = recipeId,
                onNavigateBack = onNavigateBack,
                onNavigateToEdit = { id -> navController.navigate(RecipeEditor(recipeId = id)) },
            )
        }
        composable<RecipeEditor> { backStackEntry ->
            val route = backStackEntry.toRoute<RecipeEditor>()
            RecipeEditorRoot(
                recipeId = route.recipeId,
                isQuickMeal = route.isQuickMeal,
                onNavigateBack = onNavigateBack,
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
