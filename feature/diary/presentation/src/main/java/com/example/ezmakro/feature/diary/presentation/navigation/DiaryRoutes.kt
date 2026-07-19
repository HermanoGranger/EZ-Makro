package com.example.ezmakro.feature.diary.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.example.ezmakro.feature.diary.presentation.day.DiaryDayRoot
import com.example.ezmakro.feature.diary.presentation.history.HistoryRoot
import com.example.ezmakro.feature.diary.presentation.quickadd.QuickAddRoot
import kotlinx.serialization.Serializable
import java.time.LocalDate

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
)

fun NavGraphBuilder.diaryGraph(
    navController: NavHostController,
    onNavigateToAddFood: (String) -> Unit,
    onNavigateToAddRecipe: (String) -> Unit,
) {
    navigation<Diary>(startDestination = DiaryDay::class) {
        composable<DiaryDay> { backStackEntry ->
            val route = backStackEntry.toRoute<DiaryDay>()
            DiaryDayRoot(
                onNavigateToAddFood = onNavigateToAddFood,
                onNavigateToAddRecipe = onNavigateToAddRecipe,
                onNavigateToQuickAdd = {
                    val date = route.date ?: LocalDate.now().toString()
                    navController.navigate(QuickAdd(date = date))
                },
                onNavigateToHistory = { navController.navigate(History) },
            )
        }
        composable<QuickAdd> { backStackEntry ->
            val route = backStackEntry.toRoute<QuickAdd>()
            QuickAddRoot(
                onSaved = { navController.popBackStack() },
            )
        }
        composable<History> {
            HistoryRoot(
                onDayClick = { date -> navController.navigate(DiaryDay(date = date)) },
            )
        }
    }
}
