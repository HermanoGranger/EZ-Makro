package com.example.ezmakro.core.presentation.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DiaryEntryDetail(
    data: DiaryEntryDetailData,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEditIngredients: (() -> Unit)? = null,
    onEditIngredientsLabel: String = "Zutaten",
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    MacroValue("K", data.kcal)
                    MacroValue("P", data.protein)
                    MacroValue("F", data.fat)
                    MacroValue("KH", data.carbs)
                    MacroValue("B", data.fiber)
                }
                Text(
                    text = data.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onEdit) {
                    Text("Bearbeiten", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onDelete) {
                    Text(
                        "Löschen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (onEditIngredients != null && data.isRecipe) {
                    TextButton(onClick = onEditIngredients) {
                        Text(
                            onEditIngredientsLabel,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun MacroValue(
    label: String,
    value: String,
) {
    Text(
        text = "$label $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
