package com.example.ezmakro.feature.food.presentation.search

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.presentation.diary.AddToDiarySheet
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.food.presentation.R
import com.example.ezmakro.feature.food.presentation.model.FoodUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun FoodSearchRoot(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToCreate: (String?) -> Unit,
    onAddToDiary: (String, String, com.example.ezmakro.core.domain.model.MealSlot, Double) -> Unit = { _, _, _, _ -> },
    viewModel: FoodSearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is FoodSearchEvent.NavigateToDetail -> onNavigateToDetail(event.foodId)
            FoodSearchEvent.NavigateToScanner -> onNavigateToScanner()
            is FoodSearchEvent.NavigateToCreate -> onNavigateToCreate(event.barcode)
            is FoodSearchEvent.AddToDiary -> onAddToDiary(event.foodId, event.date, event.slot, event.amountGrams)
        }
    }

    FoodSearchScreen(state = state, onAction = viewModel::onAction)

    if (state.pendingAddFoodId != null) {
        AddToDiarySheet(
            isRecipe = false,
            onDismiss = { viewModel.onAction(FoodSearchAction.OnDismissAddToDiary) },
            onConfirm = { slot, amount, _ -> viewModel.onAction(FoodSearchAction.OnConfirmAddToDiary(slot, amount)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    state: FoodSearchState,
    onAction: (FoodSearchAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.food_search_title)) }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(FoodSearchAction.OnQueryChange(it)) },
                label = { Text(stringResource(R.string.food_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onAction(FoodSearchAction.OnScanBarcodeClick) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.food_scan_barcode)) }
                OutlinedButton(
                    onClick = { onAction(FoodSearchAction.OnCreateOwnClick) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.food_create_own)) }
            }

            if (state.isQuerying) {
                QueryResults(state = state, onAction = onAction)
            } else {
                Sections(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun Sections(
    state: FoodSearchState,
    onAction: (FoodSearchAction) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sectionHeader(R.string.food_section_favorites)
        if (state.favorites.isEmpty()) {
            item { EmptyRow(stringResource(R.string.food_empty_favorites)) }
        } else {
            items(state.favorites, key = { "fav-${it.id}" }) { food ->
                FoodRow(food = food, onAction = onAction)
            }
        }
        if (state.recent.isNotEmpty()) {
            sectionHeader(R.string.food_section_recent)
            items(state.recent, key = { "recent-${it.id}" }) { food ->
                FoodRow(food = food, onAction = onAction)
            }
        }
    }
}

@Composable
private fun QueryResults(
    state: FoodSearchState,
    onAction: (FoodSearchAction) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sectionHeader(R.string.food_section_local_results)
        if (state.localResults.isEmpty()) {
            item { EmptyRow(stringResource(R.string.food_empty_search)) }
        } else {
            items(state.localResults, key = { "local-${it.id}" }) { food ->
                FoodRow(food = food, onAction = onAction)
            }
        }

        item {
            Button(
                onClick = { onAction(FoodSearchAction.OnSearchOnlineClick) },
                enabled = !state.isSearchingOnline,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (state.isSearchingOnline) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(stringResource(R.string.food_search_online))
            }
        }

        state.onlineError?.let { error ->
            item { EmptyRow(error.asString()) }
        }

        if (state.onlineResults.isNotEmpty()) {
            sectionHeader(R.string.food_section_online_results)
            items(state.onlineResults, key = { "online-${it.id}" }) { food ->
                FoodRow(food = food, onAction = onAction)
            }
        }
    }
}

@Composable
private fun FoodRow(
    food: FoodUi,
    onAction: (FoodSearchAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onAction(FoodSearchAction.OnFoodClick(food.id)) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "${food.kcalPer100g} kcal / 100 g")
        }
        IconButton(onClick = { onAction(FoodSearchAction.OnToggleFavorite(food.id)) }) {
            Icon(
                imageVector = if (food.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription =
                    if (food.isFavorite) {
                        stringResource(R.string.food_detail_favorite_remove)
                    } else {
                        stringResource(R.string.food_detail_favorite_add)
                    },
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionHeader(
    @androidx.annotation.StringRes titleRes: Int,
) {
    item {
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}
