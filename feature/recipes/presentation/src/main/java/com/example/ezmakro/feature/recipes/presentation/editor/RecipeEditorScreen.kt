package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.util.parseNonNegativeDouble
import com.example.ezmakro.core.domain.util.parsePositiveInt
import com.example.ezmakro.core.presentation.recipe.RecipeIngredientList
import com.example.ezmakro.core.presentation.recipe.RecipeNutritionTotal
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerSheet
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerTarget
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerViewModel
import com.example.ezmakro.feature.recipes.presentation.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.example.ezmakro.core.presentation.R as CoreR

@Composable
fun RecipeEditorRoot(
    recipeId: String?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToFoodCreate: (barcode: String?) -> Unit = {},
    onNavigateToFoodDetail: (String) -> Unit = {},
    onNavigateToRecipeDetail: (String) -> Unit = {},
    viewModel: RecipeEditorViewModel =
        koinViewModel {
            org.koin.core.parameter
                .parametersOf(recipeId)
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFoodPicker by rememberSaveable { mutableStateOf(false) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            RecipeEditorEvent.Saved -> onSaved()
            RecipeEditorEvent.NavigateBack -> onNavigateBack()
        }
    }

    if (showFoodPicker) {
        val foodPickerViewModel: FoodPickerViewModel =
            koinViewModel {
                parametersOf(FoodPickerTarget.RECIPE_INGREDIENT, null)
            }
        FoodPickerSheet(
            isVisible = showFoodPicker,
            onAddToDiary = { _, _, _, _, _, _ -> },
            onAddAsIngredient = { itemId, itemName, servingLabel, isRecipe, rawWeight, servingCount ->
                if (isRecipe) {
                    viewModel.onAction(
                        RecipeEditorAction.OnAddRecipeIngredientClick(
                            recipeId = itemId,
                            rawWeight = rawWeight,
                            servingCount = servingCount,
                        ),
                    )
                } else {
                    viewModel.onAction(
                        RecipeEditorAction.OnAddIngredientClick(
                            foodId = itemId,
                            foodName = itemName,
                            servingLabel = servingLabel,
                            rawWeight = rawWeight,
                            servingCount = servingCount,
                        ),
                    )
                }
            },
            onNavigateToFoodCreate = onNavigateToFoodCreate,
            onNavigateToFoodDetail = onNavigateToFoodDetail,
            onNavigateToRecipeDetail = onNavigateToRecipeDetail,
            onDismiss = { showFoodPicker = false },
            viewModel = foodPickerViewModel,
        )
    }

    RecipeEditorScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenPicker = { showFoodPicker = true },
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
                                else -> R.string.recipe_editor_title_new
                            },
                        ),
                    )
                },
                actions = {
                    val starTint: androidx.compose.ui.graphics.Color =
                        if (state.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    IconButton(onClick = { onAction(RecipeEditorAction.OnToggleFavorite) }) {
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
    val finalWeightGrams = state.finalWeight.parseNonNegativeDouble()
    val portionsValue = state.portions.parsePositiveInt()
    val gramsPerPortion =
        if (finalWeightGrams != null && portionsValue != null) {
            finalWeightGrams / portionsValue
        } else {
            null
        }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NameField(state = state, onAction = onAction) }
        item { CategoryField(state = state, onAction = onAction) }
        item { PortionsField(state = state, onAction = onAction) }
        item { FinalWeightField(state = state, onAction = onAction) }
        if (gramsPerPortion != null) {
            item {
                val label = stringResource(R.string.recipe_editor_grams_per_portion)
                Text(
                    text = "~ ${gramsPerPortion.formatValue()} g / $label",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        }

        item {
            RecipeNutritionTotal(state.totalNutrition, stringResource(CoreR.string.recipe_detail_total))
        }

        item {
            RecipeIngredientList(
                ingredients = state.ingredients,
                ingredientNutrition = state.ingredientNutrition,
                expandedIngredientIndex = state.expandedIngredientIndex,
                onAddIngredient = onOpenPicker,
                onRawWeightChange = { index, value ->
                    onAction(RecipeEditorAction.OnIngredientRawWeightChange(index, value))
                },
                onServingCountChange = { index, value ->
                    onAction(RecipeEditorAction.OnIngredientServingCountChange(index, value))
                },
                onRemoveIngredient = { onAction(RecipeEditorAction.OnRemoveIngredientClick(it)) },
                onIngredientClick = { onAction(RecipeEditorAction.OnIngredientClick(it)) },
                addIngredientLabel = stringResource(CoreR.string.recipe_editor_add_ingredient),
                ingredientError = state.ingredientError,
            )
        }

        item {
            Button(
                onClick = { onAction(RecipeEditorAction.OnSaveClick) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(CoreR.string.recipe_editor_save)) }
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
        label = { Text(stringResource(CoreR.string.recipe_editor_portions)) },
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
        label = { Text(stringResource(CoreR.string.recipe_editor_final_weight)) },
        supportingText = { Text(stringResource(CoreR.string.recipe_editor_final_weight_hint)) },
        isError = state.numberError != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
