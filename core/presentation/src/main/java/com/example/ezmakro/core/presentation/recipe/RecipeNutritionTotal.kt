package com.example.ezmakro.core.presentation.recipe

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.presentation.util.formatValue

/**
 * Single-line nutrition summary of a whole recipe. [label] is passed in instead
 * of being resolved here so that callers stay the single source for their own
 * label resource.
 */
@Composable
fun RecipeNutritionTotal(
    totalNutrition: NutritionValues,
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            "$label: ${totalNutrition.kcal.formatValue()} kcal · " +
                "P ${totalNutrition.proteinG.formatValue()} g · " +
                "KH ${totalNutrition.carbsG.formatValue()} g · " +
                "F ${totalNutrition.fatG.formatValue()} g · " +
                "B ${totalNutrition.fiberG.formatValue()} g",
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}
