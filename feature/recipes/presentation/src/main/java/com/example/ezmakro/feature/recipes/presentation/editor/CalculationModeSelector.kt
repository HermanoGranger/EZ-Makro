package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ezmakro.feature.recipes.presentation.R

private const val HALF_WIDTH_FRACTION = 0.5f

@Composable
fun CalculationModeSelector(
    mode: RecipeCalculationMode,
    onModeChange: (RecipeCalculationMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.recipe_editor_calculation_mode))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onModeChange(RecipeCalculationMode.Portion) },
                modifier = Modifier.fillMaxWidth(HALF_WIDTH_FRACTION),
                enabled = mode != RecipeCalculationMode.Portion,
            ) {
                Text(stringResource(R.string.recipe_editor_mode_portion))
            }
            Button(
                onClick = { onModeChange(RecipeCalculationMode.Weight) },
                modifier = Modifier.fillMaxWidth(HALF_WIDTH_FRACTION),
                enabled = mode != RecipeCalculationMode.Weight,
            ) {
                Text(stringResource(R.string.recipe_editor_mode_weight))
            }
        }
    }
}
