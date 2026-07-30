package com.example.ezmakro.feature.food.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.food.presentation.R
import com.example.ezmakro.feature.food.presentation.model.FoodEditStatusUi
import com.example.ezmakro.feature.food.presentation.model.FoodUi
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FoodDetailRoot(
    foodId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: FoodDetailViewModel = koinViewModel { parametersOf(foodId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            FoodDetailEvent.NavigateBack -> onNavigateBack()
            is FoodDetailEvent.NavigateToEdit -> onNavigateToEdit(event.foodId)
            is FoodDetailEvent.ShowMessage -> Unit
        }
    }

    FoodDetailScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    state: FoodDetailState,
    onAction: (FoodDetailAction) -> Unit,
) {
    if (state.showRefetchConfirm) {
        RefetchConfirmDialog(onAction = onAction)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.food?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = { onAction(FoodDetailAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val food = state.food
                    if (food != null) {
                        val starTint: androidx.compose.ui.graphics.Color =
                            if (food.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        IconButton(onClick = { onAction(FoodDetailAction.OnToggleFavorite) }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                tint = starTint,
                                contentDescription =
                                    if (food.isFavorite) {
                                        stringResource(R.string.food_detail_favorite_remove)
                                    } else {
                                        stringResource(R.string.food_detail_favorite_add)
                                    },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.food == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.food_empty_search))
                }

            else -> FoodDetailContent(food = state.food, onAction = onAction, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun RefetchConfirmDialog(onAction: (FoodDetailAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(FoodDetailAction.OnRefetchDismiss) },
        title = { Text(stringResource(R.string.food_refetch_confirm_title)) },
        text = { Text(stringResource(R.string.food_refetch_confirm_message)) },
        confirmButton = {
            TextButton(onClick = { onAction(FoodDetailAction.OnRefetchConfirm) }) {
                Text(stringResource(R.string.food_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(FoodDetailAction.OnRefetchDismiss) }) {
                Text(stringResource(R.string.food_dialog_cancel))
            }
        },
    )
}

@Composable
private fun FoodDetailContent(
    food: FoodUi,
    onAction: (FoodDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditStatusBadge(status = food.editStatus)

        Text(stringResource(R.string.food_detail_per_100g))
        HorizontalDivider()
        NutritionRow(stringResource(R.string.food_detail_kcal), "${food.kcalPer100g} kcal")
        NutritionRow(stringResource(R.string.food_detail_protein), "${food.proteinPer100g} g")
        NutritionRow(stringResource(R.string.food_detail_fat), "${food.fatPer100g} g")
        NutritionRow(stringResource(R.string.food_detail_carbs), "${food.carbsPer100g} g")
        NutritionRow(stringResource(R.string.food_detail_fiber), "${food.fiberPer100g} g")

        OutlinedButton(
            onClick = { onAction(FoodDetailAction.OnEditClick) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.food_detail_edit)) }

        if (food.editStatus == FoodEditStatusUi.UserModified ||
            food.editStatus == FoodEditStatusUi.UpdateAvailable
        ) {
            OutlinedButton(
                onClick = { onAction(FoodDetailAction.OnResetToOriginalClick) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.food_action_reset_original)) }

            if (food.barcode != null) {
                OutlinedButton(
                    onClick = { onAction(FoodDetailAction.OnRefetchClick) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.food_action_refetch)) }
            }
        }
    }
}

@Composable
private fun EditStatusBadge(status: FoodEditStatusUi) {
    val label =
        when (status) {
            FoodEditStatusUi.UserModified -> stringResource(R.string.food_badge_user_modified)
            FoodEditStatusUi.UpdateAvailable -> stringResource(R.string.food_badge_update_available)
            else -> null
        }
    if (label != null) {
        AssistChip(onClick = {}, label = { Text(label) })
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
        Text(label)
        Text(value)
    }
}
