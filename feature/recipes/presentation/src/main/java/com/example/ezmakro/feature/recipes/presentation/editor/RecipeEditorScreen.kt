package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.recipes.presentation.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun RecipeEditorRoot(
    recipeId: String?,
    isQuickMeal: Boolean,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RecipeEditorViewModel =
        koinViewModel {
            org.koin.core.parameter
                .parametersOf(recipeId, isQuickMeal)
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            RecipeEditorEvent.Saved -> onSaved()
            RecipeEditorEvent.NavigateBack -> onNavigateBack()
            is RecipeEditorEvent.ShowIngredientPicker -> showPicker = event.show
        }
    }

    if (showPicker) {
        IngredientPickerDialog(
            items = state.availableFoods,
            onDismiss = { showPicker = false },
            onPick = { viewModel.onAction(RecipeEditorAction.OnAddIngredientClick(it)) },
        )
    }

    RecipeEditorScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenPicker = { showPicker = true },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    state: RecipeEditorState,
    onAction: (RecipeEditorAction) -> Unit,
    onOpenPicker: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                state.isEditingExisting -> R.string.recipe_editor_title_edit
                                state.isQuickMeal -> R.string.recipe_editor_title_quick
                                else -> R.string.recipe_editor_title_new
                            },
                        ),
                    )
                },
            )
        },
    ) { padding ->
        EditorForm(
            state = state,
            onAction = onAction,
            onOpenPicker = onOpenPicker,
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        )
    }
}

@Composable
private fun EditorForm(
    state: RecipeEditorState,
    onAction: (RecipeEditorAction) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NameField(state = state, onAction = onAction) }
        item { CategoryField(state = state, onAction = onAction) }
        item {
            CalculationModeSelector(
                mode = state.calculationMode,
                onModeChange = { onAction(RecipeEditorAction.OnCalculationModeChange(it)) },
            )
        }
        item { PortionsField(state = state, onAction = onAction) }
        if (state.calculationMode == RecipeCalculationMode.Weight) {
            item { FinalWeightField(state = state, onAction = onAction) }
        }

        if (!state.isQuickMeal) {
            item { ScaleField(state = state, onAction = onAction) }
            item { MealPrepRow(state = state, onAction = onAction) }
        }

        item {
            Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recipe_editor_add_ingredient))
            }
            state.ingredientError?.let { Text(text = it.asString()) }
        }

        itemsIndexed(state.ingredients, key = { index, _ -> index }) { index, ingredient ->
            IngredientRow(
                ingredient = ingredient,
                onRawWeightChange = { onAction(RecipeEditorAction.OnIngredientRawWeightChange(index, it)) },
                onServingCountChange = { onAction(RecipeEditorAction.OnIngredientServingCountChange(index, it)) },
                onRemove = { onAction(RecipeEditorAction.OnRemoveIngredientClick(index)) },
            )
        }

        item {
            Button(
                onClick = { onAction(RecipeEditorAction.OnSaveClick) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.recipe_editor_save)) }
            state.saveError?.let { Text(text = it.asString()) }
        }
    }
}

@Composable
private fun NameField(
    state: RecipeEditorState,
    onAction: (RecipeEditorAction) -> Unit,
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { onAction(RecipeEditorAction.OnNameChange(it)) },
        label = { Text(stringResource(R.string.recipe_editor_name)) },
        isError = state.nameError != null,
        supportingText = state.nameError?.let { { Text(it.asString()) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryField(
    state: RecipeEditorState,
    onAction: (RecipeEditorAction) -> Unit,
) {
    OutlinedTextField(
        value = state.category,
        onValueChange = { onAction(RecipeEditorAction.OnCategoryChange(it)) },
        label = { Text(stringResource(R.string.recipe_editor_category)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PortionsField(
    state: RecipeEditorState,
    onAction: (RecipeEditorAction) -> Unit,
) {
    OutlinedTextField(
        value = state.portions,
        onValueChange = { onAction(RecipeEditorAction.OnPortionsChange(it)) },
        label = { Text(stringResource(R.string.recipe_editor_portions)) },
        isError = state.numberError != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FinalWeightField(
    state: RecipeEditorState,
    onAction: (RecipeEditorAction) -> Unit,
) {
    OutlinedTextField(
        value = state.finalWeight,
        onValueChange = { onAction(RecipeEditorAction.OnFinalWeightChange(it)) },
        label = { Text(stringResource(R.string.recipe_editor_final_weight)) },
        isError = state.numberError != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ScaleField(
    state: RecipeEditorState,
    onAction: (RecipeEditorAction) -> Unit,
) {
    OutlinedTextField(
        value = state.scale,
        onValueChange = { onAction(RecipeEditorAction.OnScaleChange(it)) },
        label = { Text(stringResource(R.string.recipe_editor_scale)) },
        isError = state.numberError != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MealPrepRow(
    state: RecipeEditorState,
    onAction: (RecipeEditorAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.recipe_editor_meal_prep))
        Switch(
            checked = state.isMealPrep,
            onCheckedChange = { onAction(RecipeEditorAction.OnMealPrepToggle(it)) },
        )
    }
}
