package com.example.ezmakro.core.domain.calculation

import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeSnapshot

/**
 * Freezes a recipe into a [RecipeSnapshot] for storage inside a parent recipe.
 *
 * Per ADR-003, the totals always come from the sum of the raw ingredients.
 * `nutritionPer100g` is only derived from the measured final weight; without one
 * it falls back to the raw total as an estimate. A raw total of 0 degrades to
 * [NutritionValues.ZERO] instead of dividing by zero.
 */
fun createRecipeSnapshot(recipe: Recipe): RecipeSnapshot {
    val totalRaw = recipe.totalRawWeight().grams
    val totalWeight = recipe.finalWeight?.grams ?: totalRaw
    val nutritionPer100g =
        recipe.nutritionPer100g()
            ?: if (totalRaw > 0.0) {
                recipe.totalNutrition() * (100.0 / totalRaw)
            } else {
                NutritionValues.ZERO
            }
    val gramsPerPortion = totalWeight / recipe.portions
    return RecipeSnapshot(
        recipeId = recipe.id,
        recipeName = recipe.name,
        nutritionPer100g = nutritionPer100g,
        originalLastModifiedAt = recipe.lastModifiedAt,
        portions = recipe.portions,
        gramsPerPortion = gramsPerPortion,
    )
}
