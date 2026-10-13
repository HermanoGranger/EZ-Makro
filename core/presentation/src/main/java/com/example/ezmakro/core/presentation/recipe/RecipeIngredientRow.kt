package com.example.ezmakro.core.presentation.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.domain.util.parseNonNegativeDouble
import com.example.ezmakro.core.presentation.R
import com.example.ezmakro.core.presentation.util.formatValue

/**
 * Generic portion label shown for recipe-as-ingredient amount fields
 * (editor rows and the picker's ingredient amount dialog alike).
 */
const val RECIPE_SERVING_LABEL = "Portionen"

@Composable
fun RecipeIngredientRow(
    ingredient: RecipeIngredientDraft,
    isExpanded: Boolean,
    nutrition: NutritionValues?,
    onRawWeightChange: (String) -> Unit,
    onServingCountChange: (String) -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val rawWeight = ingredient.rawWeight.parseNonNegativeDouble()
    val servingCountVal = ingredient.servingCount.parseNonNegativeDouble()
    val servingLabel = ingredient.servingLabel

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IngredientHeaderRow(
                ingredient = ingredient,
                onClick = onClick,
                onRemove = onRemove,
            )
            IngredientInputRow(
                ingredient = ingredient,
                servingLabel = servingLabel,
                onRawWeightChange = onRawWeightChange,
                onServingCountChange = onServingCountChange,
            )
            if (!isExpanded) {
                IngredientKcalLine(nutrition = nutrition)
            }
            if (isExpanded) {
                IngredientMacroDetail(
                    rawWeight = rawWeight,
                    servingCount = servingCountVal,
                    servingLabel = ingredient.servingLabel,
                    usesGenericServingLabel = ingredient.recipeId != null,
                    nutrition = nutrition,
                )
            }
        }
    }
}

@Composable
private fun IngredientInputRow(
    ingredient: RecipeIngredientDraft,
    servingLabel: String?,
    onRawWeightChange: (String) -> Unit,
    onServingCountChange: (String) -> Unit,
) {
    if (servingLabel == null) {
        SimpleWeightField(ingredient.rawWeight, onRawWeightChange)
        return
    }

    val initialUsesServing =
        ingredient.servingCount.isNotBlank() ||
            (ingredient.rawWeight.isBlank())
    var usesServing by remember(ingredient.id) { mutableStateOf(initialUsesServing) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeSelector(
            usesServing = usesServing,
            onSelectGrams = {
                if (usesServing) {
                    usesServing = false
                    onServingCountChange("")
                }
            },
            onSelectPortions = {
                if (!usesServing) {
                    usesServing = true
                    onRawWeightChange("")
                }
            },
        )
        if (usesServing) {
            ServingField(ingredient.servingCount, onServingCountChange)
        } else {
            SimpleWeightField(ingredient.rawWeight, onRawWeightChange)
        }
    }
}

@Composable
private fun SimpleWeightField(
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.recipe_editor_ingredient_raw_weight)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ServingField(
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.recipe_editor_ingredient_serving_count)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModeSelector(
    usesServing: Boolean,
    onSelectGrams: () -> Unit,
    onSelectPortions: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !usesServing,
            onClick = onSelectGrams,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.diary_add_mode_grams)) }

        SegmentedButton(
            selected = usesServing,
            onClick = onSelectPortions,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.diary_add_mode_portions)) }
    }
}

@Composable
private fun IngredientHeaderRow(
    ingredient: RecipeIngredientDraft,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
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
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.recipe_editor_delete_ingredient),
            )
        }
    }
}

@Composable
private fun IngredientKcalLine(nutrition: NutritionValues?) {
    if (nutrition != null) {
        Text(
            text = "${nutrition.kcal.formatValue()} kcal",
            modifier = Modifier.padding(start = 16.dp),
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun IngredientMacroDetail(
    rawWeight: Double?,
    servingCount: Double?,
    servingLabel: String?,
    usesGenericServingLabel: Boolean,
    nutrition: NutritionValues?,
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
        nutrition?.let {
            Text(
                text =
                    "kcal ${it.kcal.formatValue()} · " +
                        "P ${it.proteinG.formatValue()} g · " +
                        "KH ${it.carbsG.formatValue()} g · " +
                        "F ${it.fatG.formatValue()} g · " +
                        "B ${it.fiberG.formatValue()} g",
                fontStyle = FontStyle.Italic,
            )
        }
    }
}
