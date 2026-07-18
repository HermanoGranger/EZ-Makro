package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.ezmakro.feature.recipes.presentation.R

@Composable
fun IngredientRow(
    ingredient: IngredientDraft,
    onRawWeightChange: (String) -> Unit,
    onServingCountChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = ingredient.foodName, modifier = Modifier.fillMaxWidth())
            IconButton(onClick = onRemove) { Text("✕") }
        }
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
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = ingredient.rawWeight,
                onValueChange = onRawWeightChange,
                label = { Text(stringResource(R.string.recipe_editor_ingredient_raw_weight)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    HorizontalDivider()
}
