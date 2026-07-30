package com.example.ezmakro.feature.recipes.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontStyle
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
@Suppress("LongMethod")
fun RecipeDetailScreen(
    state: RecipeDetailState,
    onAction: (RecipeDetailAction) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val recipe = state.recipe
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_detail_title)) },
                actions = {
                    if (recipe != null) {
                        val starTint: androidx.compose.ui.graphics.Color =
                            if (recipe.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        IconButton(onClick = { onAction(RecipeDetailAction.OnToggleFavorite) }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                tint = starTint,
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (recipe == null) {
            Text(
                text = stringResource(R.string.recipe_list_empty),
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = recipe.name)
            recipe.category?.let { Text(stringResource(R.string.recipe_detail_category, it)) }
            Text(stringResource(R.string.recipe_detail_portions, recipe.portions))

            NutritionRow(label = stringResource(R.string.recipe_detail_total), value = "${recipe.totalKcal} kcal")
            NutritionRow(
                label = stringResource(R.string.recipe_detail_per_portion),
                value = "${recipe.perPortionKcal} kcal",
            )
            val per100gDisplay =
                if (recipe.isEstimated && recipe.per100gKcal != null) {
                    "~ ${recipe.per100gKcal} kcal"
                } else if (recipe.per100gKcal != null) {
                    "${recipe.per100gKcal} kcal"
                } else {
                    stringResource(R.string.recipe_detail_no_final_weight)
                }
            NutritionRow(
                label = stringResource(R.string.recipe_detail_per_100g),
                value = per100gDisplay,
            )

            Text(
                text = stringResource(R.string.recipe_detail_ingredients),
                modifier = Modifier.padding(top = 8.dp),
            )
            state.ingredients.forEachIndexed { index, ingredient ->
                IngredientDetailRow(
                    ingredient = ingredient,
                    isExpanded = state.expandedIngredientIndex == index,
                    onClick = { onAction(RecipeDetailAction.OnIngredientClick(index)) },
                )
            }

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
private fun IngredientDetailRow(
    ingredient: IngredientDetailUi,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = ingredient.name, modifier = Modifier.weight(1f))
            Text(text = "${ingredient.kcal} kcal")
        }
        if (isExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.recipe_detail_ingredient_amount, ingredient.amountDisplay),
                    fontStyle = FontStyle.Italic,
                )
                Text(text = "${stringResource(R.string.recipe_detail_ingredient_protein)} ${ingredient.proteinG}g")
                Text(text = "${stringResource(R.string.recipe_detail_ingredient_fat)} ${ingredient.fatG}g")
                Text(text = "${stringResource(R.string.recipe_detail_ingredient_carbs)} ${ingredient.carbsG}g")
                Text(text = "${stringResource(R.string.recipe_detail_ingredient_fiber)} ${ingredient.fiberG}g")
            }
        }
    }
    HorizontalDivider()
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
