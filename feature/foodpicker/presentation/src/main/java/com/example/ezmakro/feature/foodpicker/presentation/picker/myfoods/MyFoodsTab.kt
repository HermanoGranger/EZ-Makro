package com.example.ezmakro.feature.foodpicker.presentation.picker.myfoods

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.foodpicker.presentation.R
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerAction
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerState
import com.example.ezmakro.feature.foodpicker.presentation.picker.PickerFoodUi
import com.example.ezmakro.feature.foodpicker.presentation.picker.PickerRecipeUi

@Composable
fun MyFoodsTab(
    state: FoodPickerState,
    onAction: (FoodPickerAction) -> Unit,
    onAddClick: (String) -> Unit,
    onAddRecipeClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SegmentedButton(
                selected = state.showFoods,
                onClick = { if (!state.showFoods) onAction(FoodPickerAction.OnMyFoodsToggle) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.foodpicker_toggle_foods)) }

            SegmentedButton(
                selected = !state.showFoods,
                onClick = { if (state.showFoods) onAction(FoodPickerAction.OnMyFoodsToggle) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.foodpicker_toggle_recipes)) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.showFoods) {
            FoodList(foods = state.favoriteFoods, onAction = onAction, onAddClick = onAddClick)
        } else {
            RecipeList(recipes = state.favoriteRecipes, onAction = onAction, onAddRecipeClick = onAddRecipeClick)
        }
    }
}

@Composable
private fun FoodList(
    foods: List<PickerFoodUi>,
    onAction: (FoodPickerAction) -> Unit,
    onAddClick: (String) -> Unit,
) {
    if (foods.isEmpty()) {
        Text(
            text = stringResource(R.string.foodpicker_favorites_empty),
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(foods, key = { it.id }) { food ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onAction(FoodPickerAction.OnFoodClick(food.id)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = food.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = "${food.kcalPer100g.formatValue()} kcal / 100 g")
                }
                IconButton(onClick = { onAddClick(food.id) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.foodpicker_add),
                    )
                }
                IconButton(onClick = { onAction(FoodPickerAction.OnToggleFavorite(food.id)) }) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun RecipeList(
    recipes: List<PickerRecipeUi>,
    onAction: (FoodPickerAction) -> Unit,
    onAddRecipeClick: (String) -> Unit,
) {
    if (recipes.isEmpty()) {
        Text(
            text = stringResource(R.string.foodpicker_favorites_empty),
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(recipes, key = { it.id }) { recipe ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onAction(FoodPickerAction.OnRecipeClick(recipe.id)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = "${recipe.kcalPer100g.formatValue()} kcal / 100 g")
                }
                IconButton(onClick = { onAddRecipeClick(recipe.id) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.foodpicker_add),
                    )
                }
                IconButton(onClick = { onAction(FoodPickerAction.OnToggleFavorite(recipe.id)) }) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
