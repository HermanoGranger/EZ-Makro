package com.example.ezmakro.feature.food.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.ezmakro.feature.food.presentation.detail.FoodDetailRoot
import com.example.ezmakro.feature.food.presentation.edit.FoodEditRoot
import com.example.ezmakro.feature.food.presentation.scan.BarcodeScanRoot
import com.example.ezmakro.feature.food.presentation.search.FoodSearchRoot
import kotlinx.serialization.Serializable

@Serializable
data class FoodSearch(
    /** When set, selecting a food adds it to the diary for this date instead of
     *  opening the detail screen (cross-feature "add to diary" contract). */
    val addToDiaryDate: String? = null,
)

@Serializable
data class FoodDetail(
    val foodId: String,
)

@Serializable
data class FoodEdit(
    val foodId: String? = null,
    val prefillBarcode: String? = null,
)

@Serializable
object BarcodeScan

fun NavGraphBuilder.foodGraph(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onAddToDiary: (String, String, com.example.ezmakro.core.domain.model.MealSlot, Double) -> Unit = { _, _, _, _ -> },
) {
    navigation<FoodSearch>(startDestination = FoodSearch) {
        composable<FoodSearch> {
            FoodSearchRoot(
                onNavigateToDetail = { foodId -> navController.navigate(FoodDetail(foodId)) },
                onNavigateToScanner = { navController.navigate(BarcodeScan) },
                onNavigateToCreate = { barcode -> navController.navigate(FoodEdit(prefillBarcode = barcode)) },
                onAddToDiary = onAddToDiary,
            )
        }
        composable<FoodDetail> { backStackEntry ->
            val foodId = backStackEntry.toRoute<FoodDetail>().foodId
            FoodDetailRoot(
                foodId = foodId,
                onNavigateBack = onNavigateBack,
                onNavigateToEdit = { id -> navController.navigate(FoodEdit(foodId = id)) },
            )
        }
        composable<FoodEdit> { backStackEntry ->
            val route = backStackEntry.toRoute<FoodEdit>()
            FoodEditRoot(
                foodId = route.foodId,
                prefillBarcode = route.prefillBarcode,
                onNavigateBack = onNavigateBack,
                onSaved = { navController.popBackStack() },
            )
        }
        composable<BarcodeScan> {
            BarcodeScanRoot(
                onNavigateBack = onNavigateBack,
                onNavigateToDetail = { foodId -> navController.navigate(FoodDetail(foodId)) },
                onNavigateToCreate = { barcode -> navController.navigate(FoodEdit(prefillBarcode = barcode)) },
            )
        }
    }
}
