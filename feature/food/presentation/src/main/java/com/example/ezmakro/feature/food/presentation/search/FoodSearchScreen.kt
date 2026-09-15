package com.example.ezmakro.feature.food.presentation.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.food.presentation.R
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerAction
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerContent
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerEvent
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerState
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerTarget
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FoodSearchRoot(
    onNavigateToFoodDetail: (String) -> Unit,
    onNavigateToRecipeDetail: (String) -> Unit,
    onNavigateToCreate: (String?) -> Unit,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit = { _, _, _, _, _ -> },
    viewModel: FoodPickerViewModel =
        koinViewModel {
            parametersOf(FoodPickerTarget.BROWSE, null)
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is FoodPickerEvent.NavigateToFoodDetail -> onNavigateToFoodDetail(event.foodId)
            is FoodPickerEvent.NavigateToRecipeDetail -> onNavigateToRecipeDetail(event.recipeId)
            is FoodPickerEvent.NavigateToFoodCreate -> onNavigateToCreate(event.barcode)
            is FoodPickerEvent.AddToDiary ->
                onAddToDiary(event.foodId, event.date, event.slot, event.amount, event.isGrams)

            is FoodPickerEvent.AddAsIngredient -> Unit
            is FoodPickerEvent.NavigateToQuickAdd -> Unit
            FoodPickerEvent.Dismissed -> Unit
        }
    }

    FoodSearchScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    state: FoodPickerState,
    onAction: (FoodPickerAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.food_search_title)) }) },
    ) { padding ->
        FoodPickerContent(
            state = state,
            onAction = onAction,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
