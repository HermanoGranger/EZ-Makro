package com.example.ezmakro.core.domain.calculation

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeIngredient

/**
 * Nutrition of a single ingredient scaled from its per-100 g basis to its
 * effective raw weight.
 */
fun RecipeIngredient.nutrition(): NutritionValues {
    val grams = effectiveRawWeight().grams
    return food.nutritionPer100g?.times(grams / 100.0) ?: NutritionValues.ZERO
}

/**
 * Total nutrition of the whole recipe = SUM of all ingredients' RAW nutrition
 * (ADR-003). Cooking does not change this. The recipe [Recipe.scale] is applied
 * so meal-prep scaling is reflected.
 */
fun Recipe.totalNutrition(): NutritionValues {
    val sum = ingredients.fold(NutritionValues.ZERO) { acc, ing -> acc + ing.nutrition() }
    return sum * scale
}

/**
 * Nutrition "per 100 g" of the prepared dish. Derived EXCLUSIVELY from
 * [Recipe.totalNutrition] / [FinalWeight] (ADR-003). Returns null when no final
 * weight was measured, so callers can never fall back to dividing by the sum of
 * raw weights by accident.
 */
fun Recipe.nutritionPer100g(): NutritionValues? {
    val finalWeight = finalWeight ?: return null
    return totalNutrition() * (100.0 / finalWeight.grams)
}

/**
 * Nutrition per single PORTION of the dish = [Recipe.totalNutrition] /
 * [Recipe.portions]. Unlike per-100 g, this does NOT require a measured final
 * weight — it only needs the configured number of portions. Cooking weight loss
 * is irrelevant here because the total nutrition is always the raw sum.
 */
fun Recipe.nutritionPerPortion(): NutritionValues = totalNutrition() * (1.0 / portions)

/**
 * Nutrition for a logged [amountGrams] of a single [FoodItem], scaled from its
 * per-100 g basis.
 */
fun FoodItem.nutritionForGrams(amountGrams: Double): NutritionValues {
    require(amountGrams >= 0.0) { "amountGrams must be non-negative" }
    return nutritionPer100g?.times(amountGrams / 100.0) ?: NutritionValues.ZERO
}

/**
 * Sum of all ingredients' raw weights (used for display only — NEVER as the
 * basis for "per 100 g", see [Recipe.nutritionPer100g]).
 */
fun Recipe.totalRawWeight(): RawWeight =
    ingredients
        .fold(RawWeight(0.0)) { acc, ing -> acc + ing.effectiveRawWeight() }

/**
 * Actual nutrition consumed by a [DiaryEntry].
 *
 * - Food entry: [FoodItem.nutritionForGrams] over the logged grams.
 * - Recipe entry:
 *   - by [DiaryEntry.portionCount]: [Recipe.nutritionPerPortion] × portions.
 *   - by [DiaryEntry.amountGrams]: if a [FinalWeight] is known, scaled from the
 *     per-100 g basis; otherwise falls back to per-portion using the recipe's
 *     total weight proxy (raw-weight sum) so grams are still meaningful even
 *     without a measured final weight.
 */
fun DiaryEntry.consumedNutrition(): NutritionValues {
    food?.let { return it.nutritionForGrams(checkNotNull(amountGrams)) }

    val recipe = checkNotNull(recipe)
    portionCount?.let { return recipe.nutritionPerPortion() * it }

    val grams = checkNotNull(amountGrams)
    val factor =
        recipe.finalWeight
            ?.let { recipe.nutritionPer100g()!! * (1.0 / 100.0) }
            ?: run {
                val totalRaw = recipe.totalRawWeight().grams
                recipe.totalNutrition() * (1.0 / totalRaw)
            }
    return factor * grams
}
