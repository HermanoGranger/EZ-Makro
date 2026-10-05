package com.example.ezmakro.feature.diary.presentation.recipeentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.usecase.diary.DiaryRecipeEditScope
import com.example.ezmakro.core.presentation.recipe.RecipeIngredientList
import com.example.ezmakro.core.presentation.recipe.RecipeNutritionTotal
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.diary.presentation.R
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerSheet
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerTarget
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerViewModel
import com.example.ezmakro.core.presentation.R as CoreR

@Composable
fun DiaryRecipeEditRoot(
    entryId: String,
    onSaved: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: DiaryRecipeEditViewModel = koinViewModel { parametersOf(entryId) },
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DiaryRecipeEditEvent.Saved -> onSaved()
            DiaryRecipeEditEvent.NavigateBack -> onNavigateBack()
            is DiaryRecipeEditEvent.ShowMessage -> { }
        }
    }

    DiaryRecipeEditScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
fun DiaryRecipeEditScreen(
    state: DiaryRecipeEditState,
    onAction: (DiaryRecipeEditAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diary_recipe_edit_title)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(text = state.recipeName, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.diary_recipe_edit_logged_amount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text(
                            text = formatNutrition(state.originalConsumedNutrition),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(" → ", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = formatNutrition(state.consumedNutrition),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (state.isSourceRecipeOutdated) {
                item {
                    Text(
                        text = stringResource(R.string.diary_recipe_edit_info_recipe_outdated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!state.canUpdateRecipe && state.sourceRecipeId != null) {
                item {
                    Text(
                        text = stringResource(R.string.diary_recipe_edit_info_recipe_deleted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                RecipeNutritionTotal(
                    totalNutrition = state.totalNutrition,
                    label = stringResource(CoreR.string.recipe_detail_total),
                )
            }

            item {
                RecipeIngredientList(
                    ingredients = state.ingredients,
                    ingredientNutrition = state.ingredientNutrition,
                    expandedIngredientIndex = state.expandedIngredientIndex,
                    onAddIngredient = { onAction(DiaryRecipeEditAction.OnAddIngredientClick) },
                    onRawWeightChange = { index, value -> onAction(DiaryRecipeEditAction.OnIngredientRawWeightChange(index, value)) },
                    onServingCountChange = { index, value -> onAction(DiaryRecipeEditAction.OnIngredientServingCountChange(index, value)) },
                    onRemoveIngredient = { index -> onAction(DiaryRecipeEditAction.OnRemoveIngredient(index)) },
                    onIngredientClick = { index -> onAction(DiaryRecipeEditAction.OnIngredientClick(index)) },
                    addIngredientLabel = stringResource(CoreR.string.recipe_editor_add_ingredient),
                    ingredientError = state.ingredientError,
                )
                Text(
                    text = stringResource(R.string.diary_recipe_edit_replace_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                Column {
                    Text(stringResource(CoreR.string.recipe_editor_portions))
                    OutlinedTextField(
                        value = state.portions,
                        onValueChange = { onAction(DiaryRecipeEditAction.OnPortionsChange(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Column {
                    Text(stringResource(CoreR.string.recipe_editor_final_weight))
                    OutlinedTextField(
                        value = state.finalWeight,
                        onValueChange = { onAction(DiaryRecipeEditAction.OnFinalWeightChange(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(CoreR.string.recipe_editor_final_weight_hint)) },
                    )
                }
            }

            item {
                Column {
                    Text(stringResource(R.string.diary_recipe_edit_amount_mode_label))
                    Row {
                        Button(
                            onClick = { onAction(DiaryRecipeEditAction.OnAmountModeChange(DiaryAmountMode.PORTIONS)) },
                            enabled = state.amountMode != DiaryAmountMode.PORTIONS,
                        ) {
                            Text(stringResource(CoreR.string.recipe_editor_portions))
                        }
                        Spacer(modifier = Modifier.padding(4.dp))
                        Button(
                            onClick = { onAction(DiaryRecipeEditAction.OnAmountModeChange(DiaryAmountMode.GRAMS)) },
                            enabled = state.amountMode != DiaryAmountMode.GRAMS,
                        ) {
                            Text(stringResource(CoreR.string.recipe_editor_final_weight))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = { onAction(DiaryRecipeEditAction.OnAmountChange(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.diary_recipe_edit_logged_amount)) },
                    )
                }
            }

            item {
                Button(
                    onClick = { onAction(DiaryRecipeEditAction.OnSaveClick) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(CoreR.string.recipe_editor_save))
                }
                state.saveError?.let {
                    Text(it.asString(), color = MaterialTheme.colorScheme.error)
                }
                state.amountError?.let {
                    Text(it.asString(), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (state.isSaveScopeDialogOpen) {
        SaveScopeDialog(
            canUpdateRecipe = state.canUpdateRecipe,
            onConfirm = { scope -> onAction(DiaryRecipeEditAction.OnConfirmSave(scope)) },
            onDismiss = { onAction(DiaryRecipeEditAction.OnDismissSaveScopeDialog) },
        )
    }

    if (state.isAddPickerOpen) {
        val foodPickerViewModel: FoodPickerViewModel =
            koinViewModel {
                parametersOf(FoodPickerTarget.RECIPE_INGREDIENT, null)
            }
        FoodPickerSheet(
            isVisible = true,
            viewModel = foodPickerViewModel,
            onAddAsIngredient = { id, name, servingLabel, isRecipe ->
                if (isRecipe) {
                    onAction(DiaryRecipeEditAction.OnDismissPicker)
                    return@FoodPickerSheet
                }
                onAction(DiaryRecipeEditAction.OnDismissPicker)
            },
            onAddToDiary = { _, _, _, _, _, _ -> },
            onNavigateToFoodCreate = {},
            onDismiss = { onAction(DiaryRecipeEditAction.OnDismissPicker) },
        )
    }
}

@Composable
private fun SaveScopeDialog(
    canUpdateRecipe: Boolean,
    onConfirm: (DiaryRecipeEditScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diary_recipe_edit_save_scope_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                RadioRow(
                    text = stringResource(R.string.diary_recipe_edit_scope_entry_only),
                    hint = stringResource(R.string.diary_recipe_edit_scope_entry_only_hint),
                    selected = false,
                    enabled = true,
                    onClick = { onConfirm(DiaryRecipeEditScope.ENTRY_ONLY) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                RadioRow(
                    text = stringResource(R.string.diary_recipe_edit_scope_recipe),
                    hint = stringResource(R.string.diary_recipe_edit_scope_recipe_hint),
                    selected = false,
                    enabled = canUpdateRecipe,
                    onClick = { if (canUpdateRecipe) onConfirm(DiaryRecipeEditScope.RECIPE_AND_ENTRY) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}

@Composable
private fun RadioRow(
    text: String,
    hint: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                enabled = enabled,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(modifier = Modifier.padding(8.dp))
        Column {
            Text(text)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun formatNutrition(n: NutritionValues): String =
    "${n.kcal.formatValue()} kcal · P ${n.proteinG.formatValue()} · F ${n.fatG.formatValue()} · KH ${n.carbsG.formatValue()} · B ${n.fiberG.formatValue()}"
