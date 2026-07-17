package com.example.ezmakro.core.domain.calculation

import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeIngredient

/**
 * Pure domain calculation helpers. No Android, no IO — fully unit-testable.
 */

/**
 * Nutrition of a single ingredient scaled from its per-100 g basis to its
 * effective raw weight.
 */
fun RecipeIngredient.nutrition(): NutritionValues {
    val grams = effectiveRawWeight().grams
    return food.nutritionPer100g * (grams / 100.0)
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
fun Recipe.nutritionPerPortion(): NutritionValues =
    totalNutrition() * (1.0 / portions)

/**
 * Nutrition for a logged [amountGrams] of a single [FoodItem], scaled from its
 * per-100 g basis.
 */
fun FoodItem.nutritionForGrams(amountGrams: Double): NutritionValues {
    require(amountGrams >= 0.0) { "amountGrams must be non-negative" }
    return nutritionPer100g * (amountGrams / 100.0)
}

/**
 * Sum of all ingredients' raw weights (used for display only — NEVER as the
 * basis for "per 100 g", see [Recipe.nutritionPer100g]).
 */
fun Recipe.totalRawWeight(): RawWeight = ingredients
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
    val food = food
    if (food != null) {
        return food.nutritionForGrams(checkNotNull(amountGrams))
    }
    val recipe = checkNotNull(recipe)
    val portions = portionCount
    if (portions != null) {
        return recipe.nutritionPerPortion() * portions
    }
    val grams = checkNotNull(amountGrams)
    recipe.finalWeight?.let { fw ->
        val per100 = recipe.nutritionPer100g()!!
        return per100 * (grams / 100.0)
    }
    // No measured final weight: derive a per-gram factor from total nutrition
    // over the total raw weight so gram-based logging still works.
    val totalRaw = recipe.totalRawWeight().grams
    return recipe.totalNutrition() * (grams / totalRaw)
}
