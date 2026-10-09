package com.example.ezmakro.feature.food.presentation.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.food.presentation.R
import com.example.ezmakro.feature.food.presentation.components.EditStatusBadge
import com.example.ezmakro.feature.food.presentation.model.toFoodEditStatusUi
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FoodEditRoot(
    foodId: String?,
    prefillBarcode: String?,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: FoodEditViewModel = koinViewModel { parametersOf(foodId, prefillBarcode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            FoodEditEvent.NavigateBack -> onNavigateBack()
            is FoodEditEvent.Saved -> onSaved(event.foodId)
        }
    }

    FoodEditScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditScreen(
    state: FoodEditState,
    onAction: (FoodEditAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditingExisting) {
                                R.string.food_edit_title_edit
                            } else {
                                R.string.food_edit_title_new
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(FoodEditAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val starTint: androidx.compose.ui.graphics.Color =
                        if (state.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    IconButton(onClick = { onAction(FoodEditAction.OnToggleFavorite) }) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            tint = starTint,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        FoodEditForm(state = state, onAction = onAction, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun FoodEditForm(
    state: FoodEditState,
    onAction: (FoodEditAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ADR-004: the edit status is server-owned and mirrored into the state, so it
        // stays visible while the user edits.
        EditStatusBadge(status = state.cachedStatus.toFoodEditStatusUi())
        OutlinedTextField(
            value = state.name,
            onValueChange = { onAction(FoodEditAction.OnNameChange(it)) },
            label = { Text(stringResource(R.string.food_edit_name)) },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it.asString()) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.barcode,
            onValueChange = { onAction(FoodEditAction.OnBarcodeChange(it)) },
            label = { Text(stringResource(R.string.food_edit_barcode)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.food_edit_section_per_100g),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        NumberField(state.kcal, R.string.food_edit_kcal) { onAction(FoodEditAction.OnKcalChange(it)) }
        NumberField(state.protein, R.string.food_edit_protein) { onAction(FoodEditAction.OnProteinChange(it)) }
        NumberField(state.fat, R.string.food_edit_fat) { onAction(FoodEditAction.OnFatChange(it)) }
        NumberField(state.carbs, R.string.food_edit_carbs) { onAction(FoodEditAction.OnCarbsChange(it)) }
        NumberField(state.fiber, R.string.food_edit_fiber) { onAction(FoodEditAction.OnFiberChange(it)) }

        state.numberError?.let { Text(it.asString()) }

        Text(
            stringResource(R.string.food_edit_section_serving),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        NumberField(state.portionGrams, R.string.food_edit_portion_grams) {
            onAction(FoodEditAction.OnPortionGramsChange(it))
        }

        Button(
            onClick = { onAction(FoodEditAction.OnSaveClick) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.food_edit_save)) }

        state.saveError?.let { Text(it.asString()) }
    }
}

@Composable
private fun NumberField(
    value: String,
    labelRes: Int,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
