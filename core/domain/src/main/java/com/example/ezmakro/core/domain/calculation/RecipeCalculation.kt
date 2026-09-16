package com.example.ezmakro.core.domain.calculation

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeIngredient

fun RecipeIngredient.nutrition(): NutritionValues {
    val grams = effectiveRawWeight().grams
    return if (recipeSnapshot != null) {
        recipeSnapshot.nutritionPer100g * (grams / 100.0)
    } else {
        food?.nutritionPer100g?.times(grams / 100.0) ?: NutritionValues.ZERO
    }
}

fun Recipe.totalNutrition(): NutritionValues =
    ingredients.fold(NutritionValues.ZERO) { acc, ing -> acc + ing.nutrition() }

fun Recipe.nutritionPer100g(): NutritionValues? {
    val finalWeight = finalWeight ?: return null
    return totalNutrition() * (100.0 / finalWeight.grams)
}

fun Recipe.estimatedPer100g(): NutritionValues {
    val totalRaw = totalRawWeight().grams
    return if (totalRaw > 0.0) totalNutrition() * (100.0 / totalRaw) else NutritionValues.ZERO
}

fun Recipe.nutritionPerPortion(): NutritionValues = totalNutrition() * (1.0 / portions)

fun FoodItem.nutritionForGrams(amountGrams: Double): NutritionValues {
    require(amountGrams >= 0.0) { "amountGrams must be non-negative" }
    return nutritionPer100g?.times(amountGrams / 100.0) ?: NutritionValues.ZERO
}

fun FoodItem.nutritionForPortions(portionCount: Double): NutritionValues {
    require(portionCount >= 0.0) { "portionCount must be non-negative" }
    val unit = checkNotNull(servingUnit) { "FoodItem must have servingUnit for portion logging" }
    return nutritionPer100g?.times(unit.gramsEquivalent * portionCount / 100.0) ?: NutritionValues.ZERO
}

fun Recipe.totalRawWeight(): RawWeight =
    ingredients
        .fold(RawWeight(0.0)) { acc, ing -> acc + ing.effectiveRawWeight() }

fun DiaryEntry.consumedNutrition(): NutritionValues {
    if (food != null) {
        return if (portionCount != null) {
            food.nutritionForPortions(portionCount)
        } else {
            food.nutritionForGrams(checkNotNull(amountGrams))
        }
    }

    val recipe = checkNotNull(recipe)
    portionCount?.let { return recipe.nutritionPerPortion() * it }

    val grams = checkNotNull(amountGrams)
    val factor =
        recipe.finalWeight
            ?.let { recipe.nutritionPer100g()!! * (1.0 / 100.0) }
            ?: run {
                val totalRaw = recipe.totalRawWeight().grams
                if (totalRaw > 0.0) recipe.totalNutrition() * (1.0 / totalRaw) else NutritionValues.ZERO
            }
    return factor * grams
}
