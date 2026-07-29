package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.ezmakro.feature.recipes.presentation.R

@Composable
fun IngredientPickerDialog(
    foods: List<FoodPickerItem>,
    recipes: List<RecipePickerItem>,
    onDismiss: () -> Unit,
    onPickFood: (String) -> Unit,
    onPickRecipe: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(stringResource(R.string.recipe_ingredient_picker_title)) },
        text = {
            if (foods.isEmpty() && recipes.isEmpty()) {
                Text(stringResource(R.string.recipe_ingredient_picker_empty))
            } else {
                LazyColumn {
                    recipeSection(recipes, onPickRecipe)
                    foodSection(foods, onPickFood)
                }
            }
        },
    )
}

private fun LazyListScope.recipeSection(
    recipes: List<RecipePickerItem>,
    onPickRecipe: (String) -> Unit,
) {
    if (recipes.isEmpty()) return
    item {
        Text(
            text = stringResource(R.string.recipe_ingredient_picker_section_recipes),
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    items(recipes, key = { "r_${it.id}" }) { recipe ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onPickRecipe(recipe.id) }
                    .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = recipe.name, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.recipe_ingredient_picker_recipe_badge),
                fontStyle = FontStyle.Italic,
            )
        }
        HorizontalDivider()
    }
}

private fun LazyListScope.foodSection(
    foods: List<FoodPickerItem>,
    onPickFood: (String) -> Unit,
) {
    if (foods.isEmpty()) return
    item {
        Text(
            text = stringResource(R.string.recipe_ingredient_picker_section_foods),
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    items(foods, key = { it.id }) { food ->
        Text(
            text = food.name,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onPickFood(food.id) }
                    .padding(vertical = 12.dp),
        )
        HorizontalDivider()
    }
}
