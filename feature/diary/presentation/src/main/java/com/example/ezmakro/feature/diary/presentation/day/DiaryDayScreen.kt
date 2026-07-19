package com.example.ezmakro.feature.diary.presentation.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.feature.diary.presentation.R
import com.example.ezmakro.feature.diary.presentation.model.DiaryEntryUi
import com.example.ezmakro.feature.diary.presentation.model.SlotSummaryUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun DiaryDayRoot(
    onNavigateToAddFood: (String) -> Unit,
    onNavigateToAddRecipe: (String) -> Unit,
    onNavigateToQuickAdd: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: DiaryDayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Pair<String, MealSlot>?>(null) }

    LaunchedEffect(state.copyYesterdayMessage) {
        state.copyYesterdayMessage?.let {
            snackbarHostState.showSnackbar(it)
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
                onNavigateToAddFood = onNavigateToAddFood,
                onNavigateToAddRecipe = onNavigateToAddRecipe,
                onNavigateToQuickAdd = onNavigateToQuickAdd,
                onNavigateToHistory = onNavigateToHistory,
            ),
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
}

private fun DiaryDayState.isEmptyDay(): Boolean =
    breakfast.isEmpty() && lunch.isEmpty() && dinner.isEmpty() && snacks.isEmpty()

data class DiaryDayCallbacks(
    val onAction: (DiaryDayAction) -> Unit,
    val onRequestDelete: (String, MealSlot) -> Unit,
    val onNavigateToAddFood: (String) -> Unit,
    val onNavigateToAddRecipe: (String) -> Unit,
    val onNavigateToQuickAdd: () -> Unit,
    val onNavigateToHistory: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.diary_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            DiaryDayBottomBar(
                state = state,
                onAction = callbacks.onAction,
                onNavigateToHistory = callbacks.onNavigateToHistory,
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.dayTotal?.let { total ->
                item {
                    SummaryRow(
                        total.copy(title = stringResource(R.string.diary_slot_total)),
                        isTotal = true,
                    )
                }
            }
            item { slotSection(MealSlot.BREAKFAST, breakfastTitle, state.breakfast, callbacks.onRequestDelete) }
            item { slotSection(MealSlot.LUNCH, lunchTitle, state.lunch, callbacks.onRequestDelete) }
            item { slotSection(MealSlot.DINNER, dinnerTitle, state.dinner, callbacks.onRequestDelete) }
            item { slotSection(MealSlot.SNACKS, snacksTitle, state.snacks, callbacks.onRequestDelete) }

            if (state.isEmptyDay()) {
                item { Text(stringResource(R.string.diary_empty), modifier = Modifier.padding(16.dp)) }
            }

            item {
                DiaryDayActions(
                    onNavigateToAddFood = callbacks.onNavigateToAddFood,
                    onNavigateToAddRecipe = callbacks.onNavigateToAddRecipe,
                    date = state.date.toString(),
                    onNavigateToQuickAdd = callbacks.onNavigateToQuickAdd,
                )
            }
        }
    }
}

@Composable
private fun DiaryDayBottomBar(
    state: DiaryDayState,
    onAction: (DiaryDayAction) -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { onAction(DiaryDayAction.OnCopyYesterdayClick) },
            modifier = Modifier.weight(1f),
            enabled = !state.isCopyingYesterday,
        ) { Text(stringResource(R.string.diary_copy_yesterday)) }
        OutlinedButton(
            onClick = { onNavigateToHistory() },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.diary_history)) }
    }
}

@Composable
private fun DiaryDayActions(
    onNavigateToAddFood: (String) -> Unit,
    onNavigateToAddRecipe: (String) -> Unit,
    date: String,
    onNavigateToQuickAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { onNavigateToAddFood(date) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.diary_add))
        }
        Button(onClick = { onNavigateToAddRecipe(date) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.diary_add_recipe))
        }
        Button(onClick = { onNavigateToQuickAdd() }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.quick_add_title))
        }
    }
}

@Composable
private fun slotSection(
    slot: MealSlot,
    title: String,
    entries: List<DiaryEntryUi>,
    onRequestDelete: (String, MealSlot) -> Unit,
) {
    Text(title, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
    if (entries.isEmpty()) {
        Text(stringResource(R.string.diary_slot_empty), modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
    }
    entries.forEach { entry ->
        EntryRow(entry = entry, onDelete = { onRequestDelete(entry.id, slot) })
    }
}

@Composable
private fun EntryRow(
    entry: DiaryEntryUi,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${entry.detail} · ${entry.kcal} kcal")
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.diary_entry_delete)) }
    }
    HorizontalDivider()
}

@Composable
private fun SummaryRow(
    summary: SlotSummaryUi,
    isTotal: Boolean,
) {
    val titleStyle = MaterialTheme.typography.titleMedium
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isTotal) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(summary.title, modifier = Modifier.weight(1f), style = titleStyle)
        Text("${summary.kcal} kcal", style = titleStyle)
    }
    if (isTotal) HorizontalDivider()
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
