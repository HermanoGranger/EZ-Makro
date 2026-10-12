package com.example.ezmakro.feature.food.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.diary.AddToDiarySheet
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.food.presentation.R
import com.example.ezmakro.feature.food.presentation.components.EditStatusBadge
import com.example.ezmakro.feature.food.presentation.model.FoodEditStatusUi
import com.example.ezmakro.feature.food.presentation.model.FoodUi
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

@Composable
fun FoodDetailRoot(
    foodId: String,
    initialSlot: MealSlot?,
    date: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit = { _, _, _, _, _ -> },
    viewModel: FoodDetailViewModel = koinViewModel { parametersOf(foodId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            FoodDetailEvent.NavigateBack -> onNavigateBack()
            is FoodDetailEvent.NavigateToEdit -> onNavigateToEdit(event.foodId)
            is FoodDetailEvent.ShowMessage ->
                scope.launch { snackbarHostState.showSnackbar(event.message.asString(context)) }
        }
    }

    FoodDetailScreen(
        state = state,
        onAction = viewModel::onAction,
        onAddToDiary = onAddToDiary,
        initialSlot = initialSlot,
        date = date,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun FoodDetailScreen(
    state: FoodDetailState,
    onAction: (FoodDetailAction) -> Unit,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit = { _, _, _, _, _ -> },
    initialSlot: MealSlot? = null,
    date: String = "",
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    if (state.showRefetchConfirm) {
        RefetchConfirmDialog(onAction = onAction)
    }

    if (showAddSheet) {
        val food = state.food
        if (food != null) {
            AddToDiarySheet(
                isRecipe = false,
                hasServingUnit = food.servingLabel != null,
                gramsPerPortion = food.servingGrams,
                initialSlot = initialSlot ?: MealSlot.BREAKFAST,
                fixedSlot = initialSlot,
                onDismiss = { showAddSheet = false },
                onConfirm = { slot, amount, isGrams ->
                    onAddToDiary(
                        food.id,
                        date.ifEmpty { LocalDate.now().toString() },
                        slot,
                        amount,
                        isGrams,
                    )
                    showAddSheet = false
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        IconButton(onClick = { showAddSheet = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.food_detail_add),
                            )
                        }
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

        NutritionSection(title = stringResource(R.string.food_detail_per_100g)) {
            NutritionRow(stringResource(R.string.food_detail_kcal), "${food.kcalPer100g} kcal")
            NutritionRow(stringResource(R.string.food_detail_protein), "${food.proteinPer100g} g")
            NutritionRow(stringResource(R.string.food_detail_fat), "${food.fatPer100g} g")
            NutritionRow(stringResource(R.string.food_detail_carbs), "${food.carbsPer100g} g")
            NutritionRow(stringResource(R.string.food_detail_fiber), "${food.fiberPer100g} g")
        }

        food.servingAmount?.let { amount ->
            NutritionSection(title = stringResource(R.string.food_detail_per_serving, amount)) {
                NutritionRow(stringResource(R.string.food_detail_kcal), "${food.kcalPerServing} kcal")
                NutritionRow(stringResource(R.string.food_detail_protein), "${food.proteinPerServing} g")
                NutritionRow(stringResource(R.string.food_detail_fat), "${food.fatPerServing} g")
                NutritionRow(stringResource(R.string.food_detail_carbs), "${food.carbsPerServing} g")
                NutritionRow(stringResource(R.string.food_detail_fiber), "${food.fiberPerServing} g")
            }
        }

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
private fun NutritionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
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
        Text(label)
        Text(value)
    }
}
