package com.example.ezmakro.feature.diary.presentation.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.diary.presentation.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun QuickAddRoot(
    onSaved: () -> Unit,
    viewModel: QuickAddViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            QuickAddEvent.Saved -> onSaved()
        }
    }

    QuickAddScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    state: QuickAddState,
    onAction: (QuickAddAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.quick_add_title)) }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onAction(QuickAddAction.OnNameChange(it)) },
                label = { Text(stringResource(R.string.quick_add_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                label = stringResource(R.string.quick_add_kcal),
                value = state.kcal,
            ) { onAction(QuickAddAction.OnKcalChange(it)) }
            NumberField(
                label = stringResource(R.string.quick_add_protein),
                value = state.protein,
            ) { onAction(QuickAddAction.OnProteinChange(it)) }
            NumberField(
                label = stringResource(R.string.quick_add_fat),
                value = state.fat,
            ) { onAction(QuickAddAction.OnFatChange(it)) }
            NumberField(
                label = stringResource(R.string.quick_add_carbs),
                value = state.carbs,
            ) { onAction(QuickAddAction.OnCarbsChange(it)) }
            NumberField(
                label = stringResource(R.string.quick_add_fiber),
                value = state.fiber,
            ) { onAction(QuickAddAction.OnFiberChange(it)) }
            NumberField(
                label = stringResource(R.string.quick_add_amount),
                value = state.amountGrams,
            ) { onAction(QuickAddAction.OnAmountChange(it)) }

            SlotDropdown(state.slot) { onAction(QuickAddAction.OnSlotChange(it)) }

            state.error?.let { Text(it.asString()) }

            Button(
                onClick = { onAction(QuickAddAction.OnSaveClick) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.quick_add_save)) }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotDropdown(
    selected: MealSlot,
    onSelect: (MealSlot) -> Unit,
) {
    val slots = MealSlot.entries
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.quick_add_slot)) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            slots.forEach { slot ->
                DropdownMenuItem(text = { Text(slot.name) }, onClick = {
                    onSelect(slot)
                    expanded = false
                })
            }
        }
    }
}
