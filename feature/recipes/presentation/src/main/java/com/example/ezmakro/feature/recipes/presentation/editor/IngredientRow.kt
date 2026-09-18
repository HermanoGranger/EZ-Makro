package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.recipes.presentation.R

@Composable
fun IngredientRow(
    ingredient: IngredientDraft,
    isExpanded: Boolean,
    onRawWeightChange: (String) -> Unit,
    onServingCountChange: (String) -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val rawWeight = ingredient.rawWeight.parseNonNegativeDouble()
    val servingCountVal = ingredient.servingCount.parseNonNegativeDouble()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = ingredient.foodName, modifier = Modifier.weight(1f).padding(end = 8.dp))
            if (ingredient.snapshotChanged) {
                Text(
                    text = stringResource(R.string.recipe_editor_recipe_snapshot_changed),
                    fontStyle = FontStyle.Italic,
                )
            }
            IconButton(onClick = onRemove) { Text("✕") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = ingredient.rawWeight,
                onValueChange = onRawWeightChange,
                label = { Text(stringResource(R.string.recipe_editor_ingredient_raw_weight)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (ingredient.servingLabel != null) {
                OutlinedTextField(
                    value = ingredient.servingCount,
                    onValueChange = onServingCountChange,
                    label = {
                        Text(
                            stringResource(
                                R.string.recipe_editor_ingredient_serving_count,
                                ingredient.servingLabel,
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (isExpanded) {
            IngredientMacroDetail(
                rawWeight = rawWeight,
                servingCount = servingCountVal,
                servingLabel = ingredient.servingLabel,
                usesGenericServingLabel = ingredient.recipeId != null,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun IngredientMacroDetail(
    rawWeight: Double?,
    servingCount: Double?,
    servingLabel: String?,
    usesGenericServingLabel: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val amountText =
            when {
                rawWeight != null -> "${rawWeight.formatValue()} g"
                servingCount != null && servingLabel != null && usesGenericServingLabel ->
                    "${servingCount.formatValue()} $servingLabel"
                servingCount != null && servingLabel != null ->
                    "${servingCount.formatValue()} × $servingLabel"
                else -> ""
            }
        if (amountText.isNotEmpty()) {
            Text(text = "Menge: $amountText", fontStyle = FontStyle.Italic)
        }
        Text(text = stringResource(R.string.recipe_editor_ingredient_macros_hint), fontStyle = FontStyle.Italic)
    }
}
