package com.example.ezmakro.core.presentation.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.R

private enum class RecipeAmountMode { PORTIONS, GRAMS }

@Composable
fun AddToDiarySheet(
    isRecipe: Boolean,
    initialSlot: MealSlot = MealSlot.BREAKFAST,
    initialAmount: String = if (isRecipe) "1" else "100",
    onDismiss: () -> Unit,
    onConfirm: (slot: MealSlot, amount: Double, isGrams: Boolean) -> Unit,
) {
    var selectedSlot by remember { mutableStateOf(initialSlot) }
    var amountText by remember { mutableStateOf(initialAmount) }
    var amountMode by remember { mutableStateOf(RecipeAmountMode.PORTIONS) }
    val amountError = amountText.toDoubleOrNull()?.let { it <= 0.0 } ?: true

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !amountError,
                onClick = {
                    amountText.toDoubleOrNull()?.let { amount ->
                        onConfirm(selectedSlot, amount, amountMode == RecipeAmountMode.GRAMS)
                    }
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = {
            Text(stringResource(if (isRecipe) R.string.diary_add_recipe_title else R.string.diary_add_food_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SlotSelector(selectedSlot = selectedSlot, onSelect = { selectedSlot = it })
                if (isRecipe) {
                    AmountModeSelector(amountMode = amountMode, onModeChange = { amountMode = it })
                }
                AmountField(
                    labelRes =
                        if (!isRecipe) {
                            R.string.diary_add_grams
                        } else if (amountMode == RecipeAmountMode.PORTIONS) {
                            R.string.diary_add_portions
                        } else {
                            R.string.diary_add_grams
                        },
                    value = amountText,
                    isError = amountError,
                    onValueChange = { amountText = it },
                )
            }
        },
    )
}

@Composable
private fun SlotSelector(
    selectedSlot: MealSlot,
    onSelect: (MealSlot) -> Unit,
) {
    Text(stringResource(R.string.diary_add_slot))
    MealSlot.entries.forEach { slot ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = slot == selectedSlot, onClick = { onSelect(slot) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = slot == selectedSlot, onClick = { onSelect(slot) })
            Text(text = stringResource(slot.labelRes()), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AmountModeSelector(
    amountMode: RecipeAmountMode,
    onModeChange: (RecipeAmountMode) -> Unit,
) {
    Text(stringResource(R.string.diary_add_mode_label), modifier = Modifier.padding(top = 8.dp))
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        RecipeAmountMode.entries.forEach { mode ->
            val label =
                if (mode == RecipeAmountMode.PORTIONS) {
                    R.string.diary_add_mode_portions
                } else {
                    R.string.diary_add_mode_grams
                }
            val isSelected = mode == amountMode
            val onClick = { onModeChange(mode) }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = onClick)
                Text(text = stringResource(label), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AmountField(
    labelRes: Int,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

private fun MealSlot.labelRes(): Int =
    when (this) {
        MealSlot.BREAKFAST -> R.string.diary_slot_breakfast
        MealSlot.LUNCH -> R.string.diary_slot_lunch
        MealSlot.DINNER -> R.string.diary_slot_dinner
        MealSlot.SNACKS -> R.string.diary_slot_snacks
    }
