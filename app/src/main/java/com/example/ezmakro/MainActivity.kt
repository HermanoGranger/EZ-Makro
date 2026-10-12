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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.example.ezmakro.core.designsystem.theme.EZMakroTheme
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.util.encodeRouteParam
import com.example.ezmakro.diary.DiaryEntryAdder
import com.example.ezmakro.feature.diary.presentation.goals.navigation.Goals
import com.example.ezmakro.feature.diary.presentation.goals.navigation.goalsGraph
import com.example.ezmakro.feature.diary.presentation.navigation.ROUTE_DIARY
import com.example.ezmakro.feature.diary.presentation.navigation.diaryGraph
import com.example.ezmakro.feature.food.presentation.navigation.ROUTE_FOOD
import com.example.ezmakro.feature.food.presentation.navigation.foodGraph
import com.example.ezmakro.feature.recipes.presentation.navigation.ROUTE_RECIPES
import com.example.ezmakro.feature.recipes.presentation.navigation.recipeGraph
import com.example.ezmakro.feature.settings.presentation.navigation.ROUTE_SETTINGS
import com.example.ezmakro.feature.settings.presentation.navigation.ROUTE_SETTINGS_BACKUP
import com.example.ezmakro.feature.settings.presentation.navigation.settingsGraph
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
                val diaryEntryAdder = remember { get<DiaryEntryAdder>() }
                val snackbarHostState = remember { SnackbarHostState() }

                val addFoodToDiary: (String, String, MealSlot, Double, Boolean) -> Unit =
                    makeAddFoodToDiary(navController, diaryEntryAdder, snackbarHostState)
                val addRecipeToDiary: (String, String, MealSlot, Double, Boolean) -> Unit =
                    makeAddRecipeToDiary(navController, diaryEntryAdder, snackbarHostState)
                val addToDiaryFromPicker: (String, Boolean, String, MealSlot, Double, Boolean) -> Unit =
                    makeAddToDiaryFromPicker(diaryEntryAdder, snackbarHostState)

                Scaffold(
                    bottomBar = { AppBottomBar(navController) },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        ?.any { dest -> dest.route == item.route } == true
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
                        navController.navigate(item.route, navOpts, null)
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
            startDestination = ROUTE_DIARY,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val onNavigateToFoodCreate: (barcode: String?) -> Unit = { barcode ->
                val route =
                    if (barcode != null) {
                        "food_edit/_none_?prefillBarcode=${encodeRouteParam(barcode)}"
                    } else {
                        "food_edit/_none_"
                    }
                navController.navigate(route)
            }
            diaryGraph(
                navController = navController,
                onAddToDiaryFromPicker = addToDiaryFromPicker,
                onNavigateToFoodCreate = onNavigateToFoodCreate,
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                onNavigateToFoodDetail = { foodId, slot, date ->
                    navController.navigate("food_detail/${encodeRouteParam(foodId)}?slot=${slot.name}&date=$date")
                },
                onNavigateToRecipeDetail = { recipeId, slot, date ->
                    navController.navigate("recipe_detail/$recipeId?slot=${slot.name}&date=$date")
                },
            )
            foodGraph(
                navController = navController,
                onNavigateBack = onNavigateBack,
                onAddToDiary = addFoodToDiary,
                onNavigateToFoodDetail = { foodId ->
                    navController.navigate("food_detail/${encodeRouteParam(foodId)}")
                },
                onNavigateToRecipeDetail = { recipeId -> navController.navigate("recipe_detail/$recipeId") },
            )
            recipeGraph(
                navController = navController,
                onNavigateBack = onNavigateBack,
                onAddToDiary = addRecipeToDiary,
                onNavigateToFoodCreate = onNavigateToFoodCreate,
                onNavigateToFoodDetail = { foodId ->
                    navController.navigate("food_detail/${encodeRouteParam(foodId)}")
                },
                onNavigateToRecipeDetail = { recipeId -> navController.navigate("recipe_detail/$recipeId") },
            )
            goalsGraph(onNavigateBack = onNavigateBack)
            settingsGraph(
                onNavigateBack = onNavigateBack,
                onNavigateToGoals = { navController.navigate(Goals) },
                onNavigateToBackup = { navController.navigate(ROUTE_SETTINGS_BACKUP) },
            )
        }
    }

    private fun makeAddToDiaryFromPicker(
        diaryEntryAdder: DiaryEntryAdder,
        snackbarHostState: SnackbarHostState,
    ): (String, Boolean, String, MealSlot, Double, Boolean) -> Unit =
        { itemId, isRecipe, date, slot, amount, isGrams ->
            lifecycleScope.launch {
                val outcome =
                    diaryEntryAdder.addFromPicker(
                        itemId = itemId,
                        isRecipe = isRecipe,
                        date = LocalDate.parse(date),
                        slot = slot,
                        amount = amount,
                        isGrams = isGrams,
                    )
                // The picker already reports "added"; a failed diary write on top
                // of that must not go unnoticed.
                if (outcome == DiaryEntryAdder.AddToDiaryOutcome.Failed) {
                    snackbarHostState.showSnackbar(getString(R.string.diary_add_failed))
                }
            }
        }

    private fun makeAddFoodToDiary(
        navController: NavHostController,
        diaryEntryAdder: DiaryEntryAdder,
        snackbarHostState: SnackbarHostState,
    ): (String, String, MealSlot, Double, Boolean) -> Unit =
        { foodId, date, slot, amount, isGrams ->
            lifecycleScope.launch {
                val outcome = diaryEntryAdder.addFood(foodId, LocalDate.parse(date), slot, amount, isGrams)
                when (outcome) {
                    DiaryEntryAdder.AddToDiaryOutcome.Added ->
                        navController.popBackStack(ROUTE_DIARY, inclusive = false)
                    // Unresolved id: stay on this screen instead of navigating to
                    // the diary as if the entry had been added.
                    DiaryEntryAdder.AddToDiaryOutcome.NotFound -> Unit
                    DiaryEntryAdder.AddToDiaryOutcome.Failed ->
                        snackbarHostState.showSnackbar(getString(R.string.diary_add_failed))
                }
            }
        }

    private fun makeAddRecipeToDiary(
        navController: NavHostController,
        diaryEntryAdder: DiaryEntryAdder,
        snackbarHostState: SnackbarHostState,
    ): (String, String, MealSlot, Double, Boolean) -> Unit =
        { recipeId, date, slot, amount, isGrams ->
            lifecycleScope.launch {
                val outcome = diaryEntryAdder.addRecipe(recipeId, LocalDate.parse(date), slot, amount, isGrams)
                when (outcome) {
                    DiaryEntryAdder.AddToDiaryOutcome.Added ->
                        navController.popBackStack(ROUTE_DIARY, inclusive = false)
                    // Unresolved id: stay on this screen instead of navigating to
                    // the diary as if the entry had been added.
                    DiaryEntryAdder.AddToDiaryOutcome.NotFound -> Unit
                    DiaryEntryAdder.AddToDiaryOutcome.Failed ->
                        snackbarHostState.showSnackbar(getString(R.string.diary_add_failed))
                }
            }
        }
}

private data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
)
