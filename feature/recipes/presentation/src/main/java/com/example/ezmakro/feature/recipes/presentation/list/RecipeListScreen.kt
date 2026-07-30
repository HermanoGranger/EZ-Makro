package com.example.ezmakro.feature.recipes.presentation.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.diary.AddToDiarySheet
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.recipes.presentation.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun RecipeListRoot(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit = { _, _, _, _, _ -> },
    viewModel: RecipeListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RecipeListEvent.NavigateToDetail -> onNavigateToDetail(event.recipeId)
            is RecipeListEvent.NavigateToEditor -> onNavigateToEditor(event.recipeId)
            is RecipeListEvent.AddToDiary ->
                onAddToDiary(event.recipeId, event.date, event.slot, event.amount, event.isGrams)
        }
    }

    RecipeListScreen(
        state = state,
        onAction = viewModel::onAction,
    )

    if (state.pendingAddRecipeId != null) {
        AddToDiarySheet(
            isRecipe = true,
            onDismiss = { viewModel.onAction(RecipeListAction.OnDismissAddToDiary) },
            onConfirm = {
                slot,
                amount,
                isGrams,
                ->
                viewModel.onAction(RecipeListAction.OnConfirmAddToDiary(slot, amount, isGrams))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    state: RecipeListState,
    onAction: (RecipeListAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.recipe_list_title)) }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Button(
                onClick = { onAction(RecipeListAction.OnCreateClick) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.recipe_list_create)) }

            if (state.recipes.isEmpty()) {
                Text(
                    text = stringResource(R.string.recipe_list_empty),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.recipes, key = { it.id }) { recipe ->
                        RecipeRow(recipe = recipe, onClick = { onAction(RecipeListAction.OnRecipeClick(recipe.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeRow(
    recipe: com.example.ezmakro.feature.recipes.presentation.model.RecipeUi,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = recipe.name, maxLines = 1)
            val label = stringResource(R.string.recipe_list_per_portion)
            Text(
                text = "${recipe.totalKcal} kcal · ${recipe.perPortionKcal} / $label",
            )
        }
    }
    HorizontalDivider()
}
