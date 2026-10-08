package com.example.ezmakro.feature.foodpicker.presentation.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.domain.calculation.estimatedPer100g
import com.example.ezmakro.core.domain.calculation.nutritionPer100g
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.foodpicker.presentation.R

@Composable
fun FoodPickerDetailPanel(
    food: FoodItem?,
    recipe: Recipe?,
    onDismiss: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddClick: () -> Unit,
    onOpenFullscreen: () -> Unit,
    added: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val name = food?.name ?: recipe?.name.orEmpty()
    val detailId = food?.id?.value ?: recipe?.id?.value.orEmpty()
    val isFavorite = food?.isFavorite ?: recipe?.isFavorite ?: false
    val nutrition =
        when {
            food != null -> food.nutritionPer100g ?: NutritionValues.ZERO
            recipe != null -> recipe.nutritionPer100g() ?: recipe.estimatedPer100g()
            else -> NutritionValues.ZERO
        }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FoodPickerDetailHeader(
            name = name,
            detailId = detailId,
            isFavorite = isFavorite,
            onDismiss = onDismiss,
            onToggleFavorite = onToggleFavorite,
        )

        FoodPickerNutritionValues(nutrition = nutrition)

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) {
            if (added) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.padding(start = 4.dp))
            }
            Text(stringResource(R.string.foodpicker_detail_add))
        }
        OutlinedButton(onClick = onOpenFullscreen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.foodpicker_detail_open_fullscreen))
        }
    }
}

@Composable
private fun FoodPickerDetailHeader(
    name: String,
    detailId: String,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.foodpicker_detail_back),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onToggleFavorite(detailId) }) {
            Icon(
                imageVector = Icons.Filled.Star,
                tint =
                    if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun FoodPickerNutritionValues(nutrition: NutritionValues) {
    FoodPickerNutritionSection(title = stringResource(R.string.foodpicker_detail_per_100g)) {
        FoodPickerNutritionRow(
            stringResource(R.string.foodpicker_detail_kcal),
            "${nutrition.kcal.formatValue()} kcal",
        )
        FoodPickerNutritionRow(
            stringResource(R.string.foodpicker_detail_protein),
            "${nutrition.proteinG.formatValue()} g",
        )
        FoodPickerNutritionRow(
            stringResource(R.string.foodpicker_detail_fat),
            "${nutrition.fatG.formatValue()} g",
        )
        FoodPickerNutritionRow(
            stringResource(R.string.foodpicker_detail_carbs),
            "${nutrition.carbsG.formatValue()} g",
        )
        FoodPickerNutritionRow(
            stringResource(R.string.foodpicker_detail_fiber),
            "${nutrition.fiberG.formatValue()} g",
        )
    }
}

@Composable
private fun FoodPickerNutritionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun FoodPickerNutritionRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value)
    }
}
