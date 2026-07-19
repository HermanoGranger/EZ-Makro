package com.example.ezmakro.core.presentation.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.R

/**
 * Shared "how much / in which slot" editor used by both the food and recipe
 * add-to-diary flows. Kept in [core.presentation] so feature modules (food,
 * recipes) can reuse it without depending on each other.
 *
 * For a [food] entry the amount is expressed in grams ([amountGrams]); for a
 * [recipe] entry it is expressed in [portionCount] portions. Exactly one of
 * the two modes is active per usage.
 */
@Composable
fun AddToDiarySheet(
    isRecipe: Boolean,
    initialSlot: MealSlot = MealSlot.BREAKFAST,
    initialAmount: String = if (isRecipe) "1" else "100",
    onDismiss: () -> Unit,
    onConfirm: (slot: MealSlot, amount: Double) -> Unit,
) {
    var selectedSlot by remember { mutableStateOf(initialSlot) }
    var amountText by remember { mutableStateOf(initialAmount) }
    val amountError = amountText.toDoubleOrNull()?.let { it <= 0.0 } ?: true

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !amountError,
                onClick = {
                    amountText.toDoubleOrNull()?.let { onConfirm(selectedSlot, it) }
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = {
            Text(stringResource(if (isRecipe) R.string.diary_add_recipe_title else R.string.diary_add_food_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diary_add_slot))
                MealSlot.entries.forEach { slot ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = slot == selectedSlot,
                                    onClick = { selectedSlot = slot },
                                ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = slot == selectedSlot, onClick = { selectedSlot = slot })
                        Text(
                            text = stringResource(slot.labelRes()),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(
                            stringResource(
                                if (isRecipe) R.string.diary_add_portions else R.string.diary_add_grams,
                            ),
                        )
                    },
                    singleLine = true,
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
    )
}

private fun MealSlot.labelRes(): Int =
    when (this) {
        MealSlot.BREAKFAST -> R.string.diary_slot_breakfast
        MealSlot.LUNCH -> R.string.diary_slot_lunch
        MealSlot.DINNER -> R.string.diary_slot_dinner
        MealSlot.SNACKS -> R.string.diary_slot_snacks
    }
