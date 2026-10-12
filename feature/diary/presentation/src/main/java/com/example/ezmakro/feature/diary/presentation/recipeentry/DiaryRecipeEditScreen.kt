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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.domain.usecase.diary.DiaryRecipeEditScope
import com.example.ezmakro.core.presentation.diary.GramsPerPortionHint
import com.example.ezmakro.core.presentation.recipe.RecipeIngredientList
import com.example.ezmakro.core.presentation.recipe.RecipeNutritionTotal
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.diary.presentation.R
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerSheet
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerTarget
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.example.ezmakro.core.presentation.R as CoreR

@Composable
fun DiaryRecipeEditRoot(
    entryId: String,
    onSaved: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToFoodCreate: (barcode: String?) -> Unit = {},
    viewModel: DiaryRecipeEditViewModel = koinViewModel { parametersOf(entryId) },
) {
    val scope = rememberCoroutineScope()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DiaryRecipeEditEvent.Saved -> onSaved()
            DiaryRecipeEditEvent.NavigateBack -> onNavigateBack()
            is DiaryRecipeEditEvent.ShowMessage -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                scope.launch { snackbarHostState.showSnackbar(event.message.asString(context)) }
            }
        }
    }

    DiaryRecipeEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onNavigateToFoodCreate = onNavigateToFoodCreate,
    )
}

@Composable
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
fun DiaryRecipeEditScreen(
    state: DiaryRecipeEditState,
    onAction: (DiaryRecipeEditAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToFoodCreate: (barcode: String?) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diary_recipe_edit_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
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
                    Text(
                        text = stringResource(R.string.diary_recipe_edit_logged_on, state.date.formattedDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onRawWeightChange = {
                        index,
                        value,
                        ->
                        onAction(DiaryRecipeEditAction.OnIngredientRawWeightChange(index, value))
                    },
                    onServingCountChange = {
                        index,
                        value,
                        ->
                        onAction(DiaryRecipeEditAction.OnIngredientServingCountChange(index, value))
                    },
                    onRemoveIngredient = { index -> onAction(DiaryRecipeEditAction.OnRemoveIngredient(index)) },
                    onIngredientClick = { index -> onAction(DiaryRecipeEditAction.OnIngredientClick(index)) },
                    addIngredientLabel = stringResource(CoreR.string.recipe_editor_add_ingredient),
                    ingredientError = state.ingredientError,
                )
            }

            item {
                Column {
                    Text(stringResource(CoreR.string.recipe_editor_portions))
                    OutlinedTextField(
                        value = state.portions,
                        onValueChange = { onAction(DiaryRecipeEditAction.OnPortionsChange(it)) },
                        isError = state.numberError != null,
                        singleLine = true,
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
                        isError = state.numberError != null,
                        singleLine = true,
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
                            Text(stringResource(R.string.diary_recipe_edit_amount_mode_portions))
                        }
                        Spacer(modifier = Modifier.padding(4.dp))
                        Button(
                            onClick = { onAction(DiaryRecipeEditAction.OnAmountModeChange(DiaryAmountMode.GRAMS)) },
                            enabled = state.amountMode != DiaryAmountMode.GRAMS,
                        ) {
                            Text(stringResource(R.string.diary_recipe_edit_amount_mode_grams))
                        }
                    }
                    if (state.amountMode == DiaryAmountMode.PORTIONS) {
                        GramsPerPortionHint(state.gramsPerPortion, state.gramsPerPortionEstimated)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = { onAction(DiaryRecipeEditAction.OnAmountChange(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.amountError != null,
                        singleLine = true,
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
                state.numberError?.let {
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
            onAddAsIngredient = { id, name, servingLabel, isRecipe, rawWeight, servingCount ->
                if (isRecipe) {
                    onAction(DiaryRecipeEditAction.OnPickerRecipeNotAllowed)
                    onAction(DiaryRecipeEditAction.OnDismissPicker)
                    return@FoodPickerSheet
                }
                onAction(
                    DiaryRecipeEditAction.OnAddIngredientWithData(id, name, servingLabel, rawWeight, servingCount),
                )
            },
            onAddToDiary = { _, _, _, _, _, _ -> },
            onNavigateToFoodCreate = onNavigateToFoodCreate,
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
    var selectedScope by remember { mutableStateOf(DiaryRecipeEditScope.ENTRY_ONLY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diary_recipe_edit_save_scope_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                RadioRow(
                    text = stringResource(R.string.diary_recipe_edit_scope_entry_only),
                    hint = stringResource(R.string.diary_recipe_edit_scope_entry_only_hint),
                    selected = selectedScope == DiaryRecipeEditScope.ENTRY_ONLY,
                    enabled = true,
                    onClick = { selectedScope = DiaryRecipeEditScope.ENTRY_ONLY },
                )
                Spacer(modifier = Modifier.height(8.dp))
                RadioRow(
                    text = stringResource(R.string.diary_recipe_edit_scope_recipe),
                    hint = stringResource(R.string.diary_recipe_edit_scope_recipe_hint),
                    selected = selectedScope == DiaryRecipeEditScope.RECIPE_AND_ENTRY,
                    enabled = canUpdateRecipe,
                    onClick = { if (canUpdateRecipe) selectedScope = DiaryRecipeEditScope.RECIPE_AND_ENTRY },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedScope) }) {
                Text(stringResource(R.string.diary_recipe_edit_scope_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.diary_recipe_edit_scope_cancel))
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
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                    enabled = enabled,
                ).padding(8.dp),
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
    buildString {
        append("${n.kcal.formatValue()} kcal")
        append(" · P ${n.proteinG.formatValue()}")
        append(" · F ${n.fatG.formatValue()}")
        append(" · KH ${n.carbsG.formatValue()}")
        append(" · B ${n.fiberG.formatValue()}")
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun DiaryRecipeEditScreenPreview() {
    MaterialTheme {
        DiaryRecipeEditScreen(
            state = PREVIEW_STATE,
            onAction = {},
        )
    }
}

private val PREVIEW_BULGUR = previewNutrition(kcal = 100.0, protein = 10.0, fat = 5.0, carbs = 2.0, fiber = 1.0)
private val PREVIEW_TOMATENMARK = previewNutrition(kcal = 60.0, protein = 6.0, fat = 3.0, carbs = 12.0, fiber = 1.5)
private val PREVIEW_TOTAL = PREVIEW_BULGUR + PREVIEW_TOMATENMARK

private val PREVIEW_STATE =
    DiaryRecipeEditState(
        isLoading = false,
        entryId = "entry-1",
        recipeName = "Bulgur Test",
        date = "2026-07-19",
        sourceRecipeId = "recipe-1",
        canUpdateRecipe = true,
        amountMode = DiaryAmountMode.PORTIONS,
        amountText = "1",
        portions = "2",
        finalWeight = "200",
        ingredients =
            listOf(
                previewDraft("f1", "Bulgur", "100"),
                previewDraft("f2", "Tomatenmark", "75"),
            ),
        ingredientNutrition = listOf(PREVIEW_BULGUR, PREVIEW_TOMATENMARK),
        totalNutrition = PREVIEW_TOTAL,
        originalConsumedNutrition = PREVIEW_TOTAL * 0.5,
        consumedNutrition = PREVIEW_TOTAL * 0.55,
    )

private fun previewNutrition(
    kcal: Double,
    protein: Double,
    fat: Double,
    carbs: Double,
    fiber: Double,
) = NutritionValues(
    kcal = kcal,
    proteinG = protein,
    fatG = fat,
    carbsG = carbs,
    fiberG = fiber,
)

/** Renders the ISO date of the entry as `dd.MM.yyyy` for the header line. */
private fun String.formattedDate(): String =
    runCatching {
        java.time.LocalDate
            .parse(this)
            .format(DATE_HEADER_FORMATTER)
    }.getOrDefault(this)

private val DATE_HEADER_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter
        .ofPattern("dd.MM.yyyy")

private fun previewDraft(
    foodId: String,
    foodName: String,
    rawWeight: String,
) = RecipeIngredientDraft(
    foodId = foodId,
    foodName = foodName,
    servingLabel = null,
    rawWeight = rawWeight,
)
