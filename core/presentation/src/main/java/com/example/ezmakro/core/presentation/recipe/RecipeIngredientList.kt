package com.example.ezmakro.core.presentation.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.presentation.uitext.UiText

// Rows no longer inherit the surrounding list's Arrangement, so the spacing
// value is repeated here. Must stay in sync with the caller's list spacing.
private val ITEM_SPACING = 12.dp

/**
 * Add-ingredient button, ingredient error and the ingredient rows of a recipe
 * editor.
 *
 * Deliberately a [Column] and not a `LazyColumn`: callers embed this block into
 * their own scrolling list, and Compose forbids a vertically scrollable child
 * that is measured with an infinite height. Rows are identified by
 * `ingredient.id` so that removing an ingredient cannot bleed `OutlinedTextField`
 * state into its neighbour.
 */
@Composable
@Suppress("LongParameterList")
fun RecipeIngredientList(
    ingredients: List<RecipeIngredientDraft>,
    ingredientNutrition: List<NutritionValues>,
    expandedIngredientIndex: Int?,
    onAddIngredient: () -> Unit,
    onRawWeightChange: (Int, String) -> Unit,
    onServingCountChange: (Int, String) -> Unit,
    onRemoveIngredient: (Int) -> Unit,
    onIngredientClick: (Int) -> Unit,
    onReplaceIngredient: ((Int) -> Unit)? = null,
    addIngredientLabel: String,
    ingredientError: UiText?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ITEM_SPACING),
    ) {
        AddIngredientBlock(
            label = addIngredientLabel,
            error = ingredientError,
            onAddIngredient = onAddIngredient,
        )
        ingredients.forEachIndexed { index, ingredient ->
            key(ingredient.id) {
                RecipeIngredientRow(
                    ingredient = ingredient,
                    isExpanded = expandedIngredientIndex == index,
                    nutrition = ingredientNutrition.getOrNull(index),
                    onRawWeightChange = { onRawWeightChange(index, it) },
                    onServingCountChange = { onServingCountChange(index, it) },
                    onRemove = { onRemoveIngredient(index) },
                    onClick = { onIngredientClick(index) },
                    onReplace = onReplaceIngredient?.let { { it(index) } },
                )
            }
        }
    }
}

@Composable
private fun AddIngredientBlock(
    label: String,
    error: UiText?,
    onAddIngredient: () -> Unit,
) {
    Column {
        Button(onClick = onAddIngredient, modifier = Modifier.fillMaxWidth()) {
            Text(label)
        }
        error?.let { Text(text = it.asString()) }
    }
}