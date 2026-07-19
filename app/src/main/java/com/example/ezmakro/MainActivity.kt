package com.example.ezmakro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
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
import com.example.ezmakro.feature.diary.presentation.navigation.Diary
import com.example.ezmakro.feature.diary.presentation.navigation.diaryGraph
import com.example.ezmakro.feature.food.presentation.navigation.FoodSearch
import com.example.ezmakro.feature.food.presentation.navigation.foodGraph
import com.example.ezmakro.feature.recipes.presentation.navigation.RecipeList
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
                val onNavigateBack: () -> Unit = { navController.popBackStack() }
                val addFoodToDiary: (String, String, MealSlot, Double) -> Unit = { foodId, date, slot, amountGrams ->
                    lifecycleScope.launch {
                        val foodRepository = get<FoodRepository>()
                        val addUseCase = get<AddFoodToDiaryUseCase>()
                        foodRepository.getById(FoodId(foodId)).onSuccess { food: FoodItem? ->
                            food?.let { addUseCase(it, slot, LocalDate.parse(date), amountGrams) }
                        }
                    }
                    navController.popBackStack(Diary, inclusive = false)
                }
                val addRecipeToDiary: (String, String, MealSlot, Double) -> Unit = { recipeId, date, slot, portions ->
                    lifecycleScope.launch {
                        val recipeRepository = get<RecipeRepository>()
                        val addUseCase = get<AddRecipeToDiaryUseCase>()
                        recipeRepository.getById(RecipeId(recipeId)).onSuccess { recipe: Recipe? ->
                            recipe?.let { addUseCase(it, slot, LocalDate.parse(date), portionCount = portions) }
                        }
                    }
                    navController.popBackStack(Diary, inclusive = false)
                }

                androidx.navigation.compose.NavHost(
                    navController = navController,
                    startDestination = Diary,
                ) {
                    diaryGraph(
                        navController = navController,
                        onNavigateToAddFood = { date ->
                            navController.navigate(FoodSearch(addToDiaryDate = date))
                        },
                        onNavigateToAddRecipe = { date ->
                            navController.navigate(RecipeList(addToDiaryDate = date))
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
                }
            }
        }
    }
}
