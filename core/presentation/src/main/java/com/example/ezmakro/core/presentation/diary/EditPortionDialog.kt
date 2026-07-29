package com.example.ezmakro.core.presentation.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.example.ezmakro.core.presentation.R

private enum class EditAmountMode { PORTIONS, GRAMS }

@Suppress("LongMethod")
@Composable
fun EditPortionDialog(
    name: String,
    isRecipe: Boolean,
    hasServingUnit: Boolean,
    initialAmountGrams: Double?,
    initialPortionCount: Double?,
    onDismiss: () -> Unit,
    onConfirm: (amountGrams: Double?, portionCount: Double?) -> Unit,
) {
    val showModeSelector = isRecipe || hasServingUnit
    val initialIsPortions = initialPortionCount != null
    val initialValue =
        if (initialIsPortions) {
            initialPortionCount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                ?: "1"
        } else {
            initialAmountGrams?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                ?: "100"
        }

    var amountText by remember { mutableStateOf(initialValue) }
    var amountMode by remember {
        mutableStateOf(if (initialIsPortions) EditAmountMode.PORTIONS else EditAmountMode.GRAMS)
    }
    val amountError = amountText.toDoubleOrNull()?.let { it <= 0.0 } ?: true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diary_edit_title)) },
        confirmButton = {
            TextButton(
                enabled = !amountError,
                onClick = {
                    amountText.toDoubleOrNull()?.let { amount ->
                        if (showModeSelector && amountMode == EditAmountMode.PORTIONS) {
                            onConfirm(null, amount)
                        } else {
                            onConfirm(amount, null)
                        }
                    }
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (showModeSelector) {
                    AmountModeSelector(
                        amountMode = amountMode,
                        onModeChange = { amountMode = it },
                    )
                }
                val labelRes =
                    if (showModeSelector && amountMode == EditAmountMode.PORTIONS) {
                        R.string.diary_add_portions
                    } else {
                        R.string.diary_add_grams
                    }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(labelRes)) },
                    singleLine = true,
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
    )
}

@Composable
private fun AmountModeSelector(
    amountMode: EditAmountMode,
    onModeChange: (EditAmountMode) -> Unit,
) {
    Text(
        stringResource(R.string.diary_add_mode_label),
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        EditAmountMode.entries.forEach { mode ->
            val label =
                if (mode == EditAmountMode.PORTIONS) {
                    R.string.diary_add_mode_portions
                } else {
                    R.string.diary_add_mode_grams
                }
            val isSelected = mode == amountMode
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, onClick = { onModeChange(mode) }, role = Role.RadioButton)
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = { onModeChange(mode) })
                Text(text = stringResource(label), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
