package com.example.ezmakro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.example.ezmakro.core.designsystem.theme.EZMakroTheme
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.domain.usecase.diary.AddFoodToDiaryUseCase
import com.example.ezmakro.core.domain.usecase.diary.AddRecipeToDiaryUseCase
import com.example.ezmakro.feature.dashboard.presentation.goals.navigation.Goals
import com.example.ezmakro.feature.dashboard.presentation.goals.navigation.goalsGraph
import com.example.ezmakro.feature.dashboard.presentation.navigation.Dashboard
import com.example.ezmakro.feature.dashboard.presentation.navigation.dashboardGraph
import com.example.ezmakro.feature.diary.presentation.navigation.Diary
import com.example.ezmakro.feature.diary.presentation.navigation.diaryGraph
import com.example.ezmakro.feature.food.presentation.navigation.ROUTE_FOOD
import com.example.ezmakro.feature.food.presentation.navigation.ROUTE_SEARCH
import com.example.ezmakro.feature.food.presentation.navigation.ROUTE_SEARCH_PARAM_DATE
import com.example.ezmakro.feature.food.presentation.navigation.foodGraph
import com.example.ezmakro.feature.recipes.presentation.navigation.ROUTE_LIST
import com.example.ezmakro.feature.recipes.presentation.navigation.ROUTE_RECIPES
import com.example.ezmakro.feature.recipes.presentation.navigation.ROUTE_RECIPES_PARAM_DATE
import com.example.ezmakro.feature.recipes.presentation.navigation.recipeGraph
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EzMakroApp() }
    }

    @Composable
    private fun EzMakroApp() {
        EZMakroTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                val navController = rememberNavController()

                val addFoodToDiary: (String, String, MealSlot, Double) -> Unit =
                    makeAddFoodToDiary(navController)
                val addRecipeToDiary: (String, String, MealSlot, Double) -> Unit =
                    makeAddRecipeToDiary(navController)

                Scaffold(
                    bottomBar = { AppBottomBar(navController) },
                ) { padding ->
                    AppNavHost(
                        navController = navController,
                        padding = padding,
                        addFoodToDiary = addFoodToDiary,
                        addRecipeToDiary = addRecipeToDiary,
                    )
                }
            }
        }
    }

    @Composable
    private fun AppBottomBar(navController: NavHostController) {
        val items =
            listOf(
                BottomNavItem(Dashboard, R.string.dashboard_title, R.drawable.ic_dashboard),
                BottomNavItem(Diary, R.string.diary_title, R.drawable.ic_diary),
                BottomNavItem(ROUTE_FOOD, R.string.food_title, R.drawable.ic_food),
                BottomNavItem(ROUTE_RECIPES, R.string.recipe_title, R.drawable.ic_recipe),
            )
        NavigationBar {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            items.forEach { item ->
                val selected =
                    currentDestination
                        ?.hierarchy
                        ?.any { dest ->
                            when (val route = item.route) {
                                is String -> dest.route == route
                                else -> dest.hasRoute(route::class)
                            }
                        } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        val navOpts =
                            navOptions {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        when (val route = item.route) {
                            is String -> navController.navigate(route, navOpts, null)
                            else ->
                                @Suppress("UNCHECKED_CAST")
                                navController.navigate(route, navOpts, null)
                        }
                    },
                    icon = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = stringResource(item.labelRes),
                        )
                    },
                    label = { Text(stringResource(item.labelRes)) },
                )
            }
        }
    }

    @Composable
    private fun AppNavHost(
        navController: NavHostController,
        padding: androidx.compose.foundation.layout.PaddingValues,
        addFoodToDiary: (String, String, MealSlot, Double) -> Unit,
        addRecipeToDiary: (String, String, MealSlot, Double) -> Unit,
    ) {
        val onNavigateBack: () -> Unit = { navController.popBackStack() }
        NavHost(
            navController = navController,
            startDestination = Dashboard,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            dashboardGraph(
                onNavigateToDiary = {
                    navController.navigate(Diary) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToGoals = { navController.navigate(Goals) },
            )
            diaryGraph(
                navController = navController,
                onNavigateToAddFood = { date ->
                    navController.navigate("$ROUTE_SEARCH?$ROUTE_SEARCH_PARAM_DATE=$date")
                },
                onNavigateToAddRecipe = { date ->
                    navController.navigate("$ROUTE_LIST?$ROUTE_RECIPES_PARAM_DATE=$date")
                },
            )
            foodGraph(
                navController = navController,
                onNavigateBack = onNavigateBack,
                onAddToDiary = addFoodToDiary,
            )
            recipeGraph(
                navController = navController,
                onNavigateBack = onNavigateBack,
                onAddToDiary = addRecipeToDiary,
            )
            goalsGraph(onNavigateBack = onNavigateBack)
        }
    }

    private fun makeAddFoodToDiary(navController: NavHostController): (String, String, MealSlot, Double) -> Unit =
        { foodId, date, slot, amountGrams ->
            lifecycleScope.launch {
                val foodRepository = get<FoodRepository>()
                val addUseCase = get<AddFoodToDiaryUseCase>()
                foodRepository
                    .getById(FoodId(foodId))
                    .onSuccess { food: FoodItem? ->
                        food?.let { addUseCase(it, slot, LocalDate.parse(date), amountGrams) }
                    }
            }
            navController.popBackStack(Diary, inclusive = false)
        }

    private fun makeAddRecipeToDiary(navController: NavHostController): (String, String, MealSlot, Double) -> Unit =
        { recipeId, date, slot, portions ->
            lifecycleScope.launch {
                val recipeRepository = get<RecipeRepository>()
                val addUseCase = get<AddRecipeToDiaryUseCase>()
                recipeRepository
                    .getById(RecipeId(recipeId))
                    .onSuccess { recipe: Recipe? ->
                        recipe?.let { addUseCase(it, slot, LocalDate.parse(date), portionCount = portions) }
                    }
            }
            navController.popBackStack(Diary, inclusive = false)
        }
}

private data class BottomNavItem(
    val route: Any,
    val labelRes: Int,
    val iconRes: Int,
)
