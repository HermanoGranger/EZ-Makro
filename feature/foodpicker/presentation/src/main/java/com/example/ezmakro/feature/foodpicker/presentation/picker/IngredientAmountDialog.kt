package com.example.ezmakro.feature.foodpicker.presentation.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.domain.util.parseDecimalFlexible
import com.example.ezmakro.feature.foodpicker.presentation.R
import com.example.ezmakro.core.presentation.R as CoreR

/**
 * Amount dialog for a pending recipe ingredient. Collects the same fields as the
 * editor's ingredient row (raw weight, optional serving count) so an added
 * ingredient arrives fully populated instead of empty (0 kcal). At least one
 * field must hold a valid non-negative number to confirm.
 *
 * [servingAmount] is the human-readable size of one portion (e.g. "300 g") and
 * is rendered as a hint above the serving-count field when present.
 */
@Composable
fun IngredientAmountDialog(
    itemName: String,
    servingLabel: String?,
    servingAmount: String? = null,
    initialRawWeight: String,
    initialServingCount: String,
    onDismiss: () -> Unit,
    onConfirm: (rawWeight: String, servingCount: String) -> Unit,
) {
    var rawWeight by remember { mutableStateOf(initialRawWeight) }
    var servingCount by remember { mutableStateOf(initialServingCount) }

    fun isValid(value: String): Boolean = value.parseDecimalFlexible()?.let { it >= 0.0 } == true

    val canConfirm =
        (rawWeight.isNotBlank() && isValid(rawWeight)) ||
            (servingCount.isNotBlank() && isValid(servingCount))

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(rawWeight, servingCount) },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.foodpicker_ingredient_amount_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(itemName)
                OutlinedTextField(
                    value = rawWeight,
                    onValueChange = { rawWeight = it },
                    label = { Text(stringResource(CoreR.string.recipe_editor_ingredient_raw_weight)) },
                    singleLine = true,
                    isError = rawWeight.isNotBlank() && !isValid(rawWeight),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (servingLabel != null) {
                    ServingCountField(
                        servingAmount = servingAmount,
                        servingCount = servingCount,
                        onServingCountChange = { servingCount = it },
                        isError = servingCount.isNotBlank() && !isValid(servingCount),
                    )
                }
            }
        },
    )
}

/**
 * Portion-count field with an optional hint line above it showing how many
 * grams one portion weighs ([servingAmount], e.g. "Pro Portion (300 g)").
 */
@Composable
private fun ServingCountField(
    servingAmount: String?,
    servingCount: String,
    onServingCountChange: (String) -> Unit,
    isError: Boolean,
) {
    if (servingAmount != null) {
        Text(
            text = stringResource(CoreR.string.ingredient_serving_amount_hint, servingAmount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
    OutlinedTextField(
        value = servingCount,
        onValueChange = onServingCountChange,
        label = { Text(stringResource(CoreR.string.recipe_editor_ingredient_serving_count)) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = if (servingAmount != null) 4.dp else 12.dp),
    )
}
