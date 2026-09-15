@file:Suppress("TooManyFunctions")

package com.example.ezmakro.feature.diary.presentation.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.diary.CollapsibleMealSlot
import com.example.ezmakro.core.presentation.diary.DiaryEntryDetailData
import com.example.ezmakro.core.presentation.diary.EditPortionDialog
import com.example.ezmakro.feature.diary.presentation.R
import com.example.ezmakro.feature.diary.presentation.model.DiaryEntryUi
import com.example.ezmakro.feature.diary.presentation.model.formatMacro
import com.example.ezmakro.feature.diary.presentation.model.sumNutrition
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerSheet
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerTarget
import com.example.ezmakro.feature.foodpicker.presentation.picker.FoodPickerViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@Suppress("LongMethod")
fun DiaryDayRoot(
    onNavigateToQuickAdd: (date: String, slot: MealSlot) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToGoals: () -> Unit = {},
    onAddToDiaryFromPicker: (
        foodId: String,
        isRecipe: Boolean,
        date: String,
        slot: MealSlot,
        amount: Double,
        isGrams: Boolean,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToFoodCreate: (barcode: String?) -> Unit = {},
    onNavigateToFoodDetail: (String, MealSlot, String) -> Unit = { _, _, _ -> },
    onNavigateToRecipeDetail: (String, MealSlot, String) -> Unit = { _, _, _ -> },
    viewModel: DiaryDayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Pair<String, MealSlot>?>(null) }
    var editEntryId by remember { mutableStateOf<String?>(null) }
    var pickerSlot by remember { mutableStateOf<MealSlot?>(null) }

    LaunchedEffect(state.copyYesterdayMessage) {
        state.copyYesterdayMessage?.let {
            snackbarHostState.showSnackbar(it.asString(context))
            viewModel.onAction(DiaryDayAction.OnMessageShown)
        }
    }

    DiaryDayScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        callbacks =
            DiaryDayCallbacks(
                onAction = viewModel::onAction,
                onRequestDelete = { id, slot -> pendingDelete = id to slot },
                onRequestEdit = { id -> editEntryId = id },
                onRequestAdd = { slot -> pickerSlot = slot },
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToGoals = onNavigateToGoals,
            ),
    )

    FoodPickerDialog(
        pickerSlot = pickerSlot,
        date = state.date.toString(),
        onAddToDiaryFromPicker = onAddToDiaryFromPicker,
        onNavigateToFoodCreate = onNavigateToFoodCreate,
        onNavigateToQuickAdd = onNavigateToQuickAdd,
        onNavigateToFoodDetail = onNavigateToFoodDetail,
        onNavigateToRecipeDetail = onNavigateToRecipeDetail,
        onDismiss = { pickerSlot = null },
    )

    if (pendingDelete != null) {
        DeleteConfirmDialog(
            onConfirm = {
                pendingDelete?.let { viewModel.onAction(DiaryDayAction.OnConfirmDelete(it.first, it.second)) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    val editingEntry = findEntryById(state, editEntryId)
    if (editingEntry != null) {
        EditPortionDialog(
            name = editingEntry.name,
            isRecipe = editingEntry.isRecipe,
            hasServingUnit = editingEntry.hasServingUnit,
            initialAmountGrams = editingEntry.amountGrams,
            initialPortionCount = editingEntry.portionCount,
            onDismiss = { editEntryId = null },
            onConfirm = { amountGrams, portionCount ->
                viewModel.onAction(DiaryDayAction.OnSaveEdit(editingEntry.id, amountGrams, portionCount))
                editEntryId = null
            },
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun FoodPickerDialog(
    pickerSlot: MealSlot?,
    date: String,
    onAddToDiaryFromPicker: (String, Boolean, String, MealSlot, Double, Boolean) -> Unit,
    onNavigateToFoodCreate: (barcode: String?) -> Unit,
    onNavigateToQuickAdd: (date: String, slot: MealSlot) -> Unit,
    onNavigateToFoodDetail: (String, MealSlot, String) -> Unit,
    onNavigateToRecipeDetail: (String, MealSlot, String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (pickerSlot != null) {
        val foodPickerViewModel: FoodPickerViewModel =
            koinViewModel {
                parametersOf(FoodPickerTarget.DIARY, date)
            }
        FoodPickerSheet(
            isVisible = true,
            initialSlot = pickerSlot,
            date = date,
            onAddToDiary = onAddToDiaryFromPicker,
            onAddAsIngredient = { _, _, _, _ -> },
            onNavigateToFoodCreate = onNavigateToFoodCreate,
            onNavigateToQuickAdd = { quickAddDate, slot ->
                onDismiss()
                onNavigateToQuickAdd(quickAddDate, slot)
            },
            onNavigateToFoodDetail = { foodId -> onNavigateToFoodDetail(foodId, pickerSlot, date) },
            onNavigateToRecipeDetail = { recipeId -> onNavigateToRecipeDetail(recipeId, pickerSlot, date) },
            onDismiss = onDismiss,
            viewModel = foodPickerViewModel,
        )
    }
}

private fun findEntryById(
    state: DiaryDayState,
    entryId: String?,
): DiaryEntryUi? {
    if (entryId == null) return null
    return (state.breakfast + state.lunch + state.dinner + state.snacks).firstOrNull { it.id == entryId }
}

private fun DiaryDayState.isEmptyDay(): Boolean =
    breakfast.isEmpty() && lunch.isEmpty() && dinner.isEmpty() && snacks.isEmpty()

data class DiaryDayCallbacks(
    val onAction: (DiaryDayAction) -> Unit,
    val onRequestDelete: (String, MealSlot) -> Unit,
    val onRequestEdit: (String) -> Unit,
    val onRequestAdd: (MealSlot) -> Unit,
    val onNavigateToHistory: () -> Unit,
    val onNavigateToGoals: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun DiaryDayScreen(
    state: DiaryDayState,
    snackbarHostState: SnackbarHostState,
    callbacks: DiaryDayCallbacks,
) {
    val breakfastTitle = stringResource(R.string.diary_slot_breakfast)
    val lunchTitle = stringResource(R.string.diary_slot_lunch)
    val dinnerTitle = stringResource(R.string.diary_slot_dinner)
    val snacksTitle = stringResource(R.string.diary_slot_snacks)

    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy") }

    val listState = rememberLazyListState()

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    state.date
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate =
                                Instant
                                    .ofEpochMilli(millis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            callbacks.onAction(DiaryDayAction.OnGoToDate(selectedDate))
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diary_title)) },
                actions = {
                    IconButton(
                        onClick = { callbacks.onAction(DiaryDayAction.OnCopyYesterdayClick) },
                        enabled = !state.isCopyingYesterday,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.diary_copy_yesterday),
                        )
                    }
                    IconButton(onClick = callbacks.onNavigateToHistory) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = stringResource(R.string.diary_history),
                        )
                    }
                    IconButton(onClick = callbacks.onNavigateToGoals) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.diary_configure_goals),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.summary?.let { summary ->
                CollapsingSummaryHeader(
                    summary = summary,
                    listState = listState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DiaryDateRow(
                date = state.date,
                dateFormatter = dateFormatter,
                onPreviousDay = { callbacks.onAction(DiaryDayAction.OnPreviousDay) },
                onNextDay = { callbacks.onAction(DiaryDayAction.OnNextDay) },
                onTodayClick = { callbacks.onAction(DiaryDayAction.OnTodayClick) },
                onOpenDatePicker = { showDatePicker = true },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                item {
                    mealSlotSection(
                        slot = MealSlot.BREAKFAST,
                        title = breakfastTitle,
                        entries = state.breakfast,
                        isExpanded = state.expandedSlots.contains(MealSlot.BREAKFAST),
                        onToggle = { callbacks.onAction(DiaryDayAction.OnToggleSlot(MealSlot.BREAKFAST)) },
                        onEditEntry = callbacks.onRequestEdit,
                        onRequestDelete = callbacks.onRequestDelete,
                        onRequestAdd = callbacks.onRequestAdd,
                    )
                }
                item {
                    mealSlotSection(
                        slot = MealSlot.LUNCH,
                        title = lunchTitle,
                        entries = state.lunch,
                        isExpanded = state.expandedSlots.contains(MealSlot.LUNCH),
                        onToggle = { callbacks.onAction(DiaryDayAction.OnToggleSlot(MealSlot.LUNCH)) },
                        onEditEntry = callbacks.onRequestEdit,
                        onRequestDelete = callbacks.onRequestDelete,
                        onRequestAdd = callbacks.onRequestAdd,
                    )
                }
                item {
                    mealSlotSection(
                        slot = MealSlot.DINNER,
                        title = dinnerTitle,
                        entries = state.dinner,
                        isExpanded = state.expandedSlots.contains(MealSlot.DINNER),
                        onToggle = { callbacks.onAction(DiaryDayAction.OnToggleSlot(MealSlot.DINNER)) },
                        onEditEntry = callbacks.onRequestEdit,
                        onRequestDelete = callbacks.onRequestDelete,
                        onRequestAdd = callbacks.onRequestAdd,
                    )
                }
                item {
                    mealSlotSection(
                        slot = MealSlot.SNACKS,
                        title = snacksTitle,
                        entries = state.snacks,
                        isExpanded = state.expandedSlots.contains(MealSlot.SNACKS),
                        onToggle = { callbacks.onAction(DiaryDayAction.OnToggleSlot(MealSlot.SNACKS)) },
                        onEditEntry = callbacks.onRequestEdit,
                        onRequestDelete = callbacks.onRequestDelete,
                        onRequestAdd = callbacks.onRequestAdd,
                    )
                }

                if (state.isEmptyDay()) {
                    item { Text(stringResource(R.string.diary_empty), modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun mealSlotSection(
    slot: MealSlot,
    title: String,
    entries: List<DiaryEntryUi>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEditEntry: (String) -> Unit,
    onRequestDelete: (String, MealSlot) -> Unit,
    onRequestAdd: (MealSlot) -> Unit,
) {
    val summary = entries.sumNutrition()
    CollapsibleMealSlot(
        title = title,
        entries = entries.map { it.toDetailData() },
        isExpanded = isExpanded,
        onToggle = onToggle,
        onEditEntry = onEditEntry,
        onRequestDelete = { entryId -> onRequestDelete(entryId, slot) },
        onRequestAdd = { onRequestAdd(slot) },
        summaryKcal = summary.kcal.formatMacro(),
        summaryProtein = summary.proteinG.formatMacro(),
        summaryFat = summary.fatG.formatMacro(),
        summaryCarbs = summary.carbsG.formatMacro(),
        summaryFiber = summary.fiberG.formatMacro(),
    )
}

private fun DiaryEntryUi.toDetailData(): DiaryEntryDetailData =
    DiaryEntryDetailData(
        id = id,
        name = name,
        detail = detail,
        kcal = kcal,
        protein = protein,
        fat = fat,
        carbs = carbs,
        fiber = fiber,
    )

@Composable
private fun DiaryDateRow(
    date: LocalDate,
    dateFormatter: DateTimeFormatter,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onTodayClick: () -> Unit,
    onOpenDatePicker: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.diary_previous_day),
            )
        }
        TextButton(onClick = onOpenDatePicker, modifier = Modifier.weight(1f)) {
            Text(date.format(dateFormatter))
        }
        IconButton(onClick = onNextDay) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.diary_next_day),
            )
        }
        TextButton(onClick = onTodayClick) {
            Text(stringResource(R.string.diary_today))
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = { Text(stringResource(R.string.diary_delete_confirm)) },
    )
}
