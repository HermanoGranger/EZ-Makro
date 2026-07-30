package com.example.ezmakro.feature.foodpicker.presentation.picker.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ezmakro.feature.foodpicker.presentation.R
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerAction
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerState
import com.example.ezmakro.feature.foodpicker.presentation.picker.PickerFoodUi
import com.example.ezmakro.feature.foodpicker.presentation.picker.PickerRecipeUi
import java.util.Locale

@Composable
fun SearchTab(
    state: FoodPickerState,
    onAction: (FoodPickerAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onAction(FoodPickerAction.OnSearchQueryChange(it)) },
            label = { Text(stringResource(R.string.foodpicker_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onAction(FoodPickerAction.OnScanBarcodeClick) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.foodpicker_search_scan)) }
            OutlinedButton(
                onClick = { onAction(FoodPickerAction.OnCreateOwnClick) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.foodpicker_search_create)) }
        }

        if (state.searchQuery.isBlank()) {
            RecentSection(state = state, onAction = onAction)
        } else {
            SearchResultsSection(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun RecentSection(
    state: FoodPickerState,
    onAction: (FoodPickerAction) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(stringResource(R.string.foodpicker_section_recent))
        }
        if (state.recentFoods.isEmpty()) {
            item { EmptyRow(stringResource(R.string.foodpicker_empty)) }
        } else {
            items(state.recentFoods, key = { it.id }) { food ->
                FoodRow(food = food, onAction = onAction)
            }
        }

        if (state.recentRecipes.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.foodpicker_section_recent_recipes)) }
            items(state.recentRecipes, key = { it.id }) { recipe ->
                RecipeRow(recipe = recipe, onAction = onAction)
            }
        }
    }
}

@Composable
private fun SearchResultsSection(
    state: FoodPickerState,
    onAction: (FoodPickerAction) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(stringResource(R.string.foodpicker_section_local))
        }
        if (state.localSearchResults.isEmpty()) {
            item { EmptyRow(stringResource(R.string.foodpicker_empty_search)) }
        } else {
            items(state.localSearchResults, key = { it.id }) { food ->
                FoodRow(food = food, onAction = onAction)
            }
        }

        if (state.localRecipeResults.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.foodpicker_section_recipes)) }
            items(state.localRecipeResults, key = { it.id }) { recipe ->
                RecipeRow(recipe = recipe, onAction = onAction)
            }
        }

        item {
            Button(
                onClick = { onAction(FoodPickerAction.OnSearchOnlineClick) },
                enabled = !state.isSearchingOnline,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (state.isSearchingOnline) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(stringResource(R.string.foodpicker_search_online))
            }
        }

        state.onlineError?.let { error ->
            item { EmptyRow(error.asString()) }
        }

        if (state.onlineSearchResults.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.foodpicker_section_online))
            }
            items(state.onlineSearchResults, key = { it.id }) { food ->
                FoodRow(food = food, onAction = onAction)
            }
        }
    }
}

@Composable
private fun FoodRow(
    food: PickerFoodUi,
    onAction: (FoodPickerAction) -> Unit,
) {
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
            Text(text = "${formatKcal(food.kcalPer100g)} kcal / 100 g")
        }
        IconButton(onClick = { onAction(FoodPickerAction.OnToggleFavorite(food.id)) }) {
            Icon(
                imageVector = if (food.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun RecipeRow(
    recipe: PickerRecipeUi,
    onAction: (FoodPickerAction) -> Unit,
) {
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
            Text(text = "${formatKcal(recipe.kcalPer100g)} kcal / 100 g")
        }
        IconButton(onClick = { onAction(FoodPickerAction.OnToggleFavorite(recipe.id)) }) {
            Icon(
                imageVector = if (recipe.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

private fun formatKcal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
