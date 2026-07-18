package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ezmakro.feature.recipes.presentation.R

@Composable
fun IngredientPickerDialog(
    items: List<FoodPickerItem>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(stringResource(R.string.recipe_ingredient_picker_title)) },
        text = {
            if (items.isEmpty()) {
                Text(stringResource(R.string.recipe_ingredient_picker_empty))
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { food ->
                        Text(
                            text = food.name,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(food.id) }
                                    .padding(vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}
