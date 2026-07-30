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
import com.example.ezmakro.feature.diary.presentation.navigation.ROUTE_DIARY
import com.example.ezmakro.feature.diary.presentation.navigation.ROUTE_DIARY_DAY
import com.example.ezmakro.feature.diary.presentation.navigation.diaryGraph
import com.example.ezmakro.feature.food.presentation.navigation.ROUTE_FOOD
import com.example.ezmakro.feature.food.presentation.navigation.foodGraph
import com.example.ezmakro.feature.recipes.presentation.navigation.ROUTE_RECIPES
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

                val addFoodToDiary: (String, String, MealSlot, Double, Boolean) -> Unit =
                    makeAddFoodToDiary(navController)
                val addRecipeToDiary: (String, String, MealSlot, Double, Boolean) -> Unit =
                    makeAddRecipeToDiary(navController)
                val addToDiaryFromPicker: (String, Boolean, String, MealSlot, Double, Boolean) -> Unit =
                    makeAddToDiaryFromPicker()

                Scaffold(
                    bottomBar = { AppBottomBar(navController) },
                ) { padding ->
                    AppNavHost(
                        navController = navController,
                        padding = padding,
                        addFoodToDiary = addFoodToDiary,
                        addRecipeToDiary = addRecipeToDiary,
                        addToDiaryFromPicker = addToDiaryFromPicker,
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
                BottomNavItem(ROUTE_DIARY, R.string.diary_title, R.drawable.ic_diary),
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
        addFoodToDiary: (String, String, MealSlot, Double, Boolean) -> Unit,
        addRecipeToDiary: (String, String, MealSlot, Double, Boolean) -> Unit,
        addToDiaryFromPicker: (String, Boolean, String, MealSlot, Double, Boolean) -> Unit,
    ) {
        val onNavigateBack: () -> Unit = { navController.popBackStack() }
        NavHost(
            navController = navController,
            startDestination = Dashboard,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            dashboardGraph(
                onNavigateToDiary = { date ->
                    navController.navigate("$ROUTE_DIARY_DAY?date=$date") {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToGoals = { navController.navigate(Goals) },
            )
            val onNavigateToFoodCreate: (barcode: String?) -> Unit = { barcode ->
                val route =
                    if (barcode != null) {
                        "food_edit/_none_?prefillBarcode=$barcode"
                    } else {
                        "food_edit/_none_"
                    }
                navController.navigate(route)
            }
            diaryGraph(
                navController = navController,
                onAddToDiaryFromPicker = addToDiaryFromPicker,
                onNavigateToFoodCreate = onNavigateToFoodCreate,
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
                onNavigateToFoodCreate = onNavigateToFoodCreate,
            )
            goalsGraph(onNavigateBack = onNavigateBack)
        }
    }

    private fun makeAddToDiaryFromPicker(): (String, Boolean, String, MealSlot, Double, Boolean) -> Unit =
        { foodId, isRecipe, date, slot, amount, isGrams ->
            lifecycleScope.launch {
                if (isRecipe) {
                    val recipeRepository = get<RecipeRepository>()
                    val addUseCase = get<AddRecipeToDiaryUseCase>()
                    recipeRepository
                        .getById(RecipeId(foodId))
                        .onSuccess { recipe ->
                            recipe?.let {
                                if (isGrams) {
                                    addUseCase(it, slot, LocalDate.parse(date), amountGrams = amount)
                                } else {
                                    addUseCase(it, slot, LocalDate.parse(date), portionCount = amount)
                                }
                            }
                        }
                } else {
                    val foodRepository = get<FoodRepository>()
                    val addUseCase = get<AddFoodToDiaryUseCase>()
                    foodRepository.getById(FoodId(foodId)).onSuccess { food ->
                        food?.let {
                            if (isGrams) {
                                addUseCase(it, slot, LocalDate.parse(date), amountGrams = amount)
                            } else {
                                addUseCase(it, slot, LocalDate.parse(date), portionCount = amount)
                            }
                        }
                    }
                }
            }
        }

    private fun makeAddFoodToDiary(
        navController: NavHostController,
    ): (String, String, MealSlot, Double, Boolean) -> Unit =
        { foodId, date, slot, amount, isGrams ->
            lifecycleScope.launch {
                val foodRepository = get<FoodRepository>()
                val addUseCase = get<AddFoodToDiaryUseCase>()
                foodRepository.getById(FoodId(foodId)).onSuccess { food: FoodItem? ->
                    food?.let {
                        if (isGrams) {
                            addUseCase(it, slot, LocalDate.parse(date), amountGrams = amount)
                        } else {
                            addUseCase(it, slot, LocalDate.parse(date), portionCount = amount)
                        }
                    }
                }
            }
            navController.popBackStack(ROUTE_DIARY, inclusive = false)
        }

    private fun makeAddRecipeToDiary(
        navController: NavHostController,
    ): (String, String, MealSlot, Double, Boolean) -> Unit =
        { recipeId, date, slot, amount, isGrams ->
            lifecycleScope.launch {
                val recipeRepository = get<RecipeRepository>()
                val addUseCase = get<AddRecipeToDiaryUseCase>()
                recipeRepository
                    .getById(RecipeId(recipeId))
                    .onSuccess { recipe: Recipe? ->
                        recipe?.let {
                            if (isGrams) {
                                addUseCase(it, slot, LocalDate.parse(date), amountGrams = amount)
                            } else {
                                addUseCase(it, slot, LocalDate.parse(date), portionCount = amount)
                            }
                        }
                    }
            }
            navController.popBackStack(ROUTE_DIARY, inclusive = false)
        }
}

private data class BottomNavItem(
    val route: Any,
    val labelRes: Int,
    val iconRes: Int,
)
