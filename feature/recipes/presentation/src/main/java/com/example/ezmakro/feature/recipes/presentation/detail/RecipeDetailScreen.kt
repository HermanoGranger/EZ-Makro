package com.example.ezmakro.feature.recipes.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.recipes.presentation.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun RecipeDetailRoot(
    recipeId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: RecipeDetailViewModel =
        koinViewModel {
            org.koin.core.parameter
                .parametersOf(recipeId)
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            RecipeDetailEvent.NavigateBack -> onNavigateBack()
            is RecipeDetailEvent.NavigateToEditor -> onNavigateToEdit(event.recipeId)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    viewModel.onAction(RecipeDetailAction.OnDeleteClick)
                }) { Text(stringResource(R.string.recipe_detail_delete)) }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.recipe_detail_delete_confirm)) },
        )
    }

    RecipeDetailScreen(
        state = state,
        onAction = viewModel::onAction,
        onRequestDelete = { showDeleteConfirm = true },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailState,
    onAction: (RecipeDetailAction) -> Unit,
    onRequestDelete: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.recipe_detail_title)) }) },
    ) { padding ->
        val recipe = state.recipe
        if (recipe == null) {
            Text(
                text = stringResource(R.string.recipe_list_empty),
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = recipe.name)
            recipe.category?.let { Text(stringResource(R.string.recipe_detail_category, it)) }
            Text(stringResource(R.string.recipe_detail_portions, recipe.portions))
            if (recipe.isMealPrep) Text(stringResource(R.string.recipe_list_meal_prep))

            NutritionRow(label = stringResource(R.string.recipe_detail_total), value = "${recipe.totalKcal} kcal")
            NutritionRow(
                label = stringResource(R.string.recipe_detail_per_portion),
                value = "${recipe.perPortionKcal} kcal",
            )
            NutritionRow(
                label = stringResource(R.string.recipe_detail_per_100g),
                value =
                    recipe.per100gKcal?.let { "$it kcal" } ?: stringResource(
                        R.string.recipe_detail_no_final_weight,
                    ),
            )

            state.deleteError?.let { Text(text = it.asString()) }

            Button(onClick = { onAction(RecipeDetailAction.OnEditClick) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recipe_detail_edit))
            }
            Button(onClick = { onAction(RecipeDetailAction.OnDuplicateClick) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recipe_detail_duplicate))
            }
            Button(onClick = onRequestDelete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recipe_detail_delete))
            }
        }
    }
}

@Composable
private fun NutritionRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label)
        Text(text = value)
    }
    HorizontalDivider()
}
