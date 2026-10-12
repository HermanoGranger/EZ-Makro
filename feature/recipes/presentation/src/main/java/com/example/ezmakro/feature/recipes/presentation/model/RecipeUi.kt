package com.example.ezmakro.feature.recipes.presentation.model

import com.example.ezmakro.core.domain.calculation.estimatedPer100g
import com.example.ezmakro.core.domain.calculation.gramsPerPortion
import com.example.ezmakro.core.domain.calculation.nutritionPer100g
import com.example.ezmakro.core.domain.calculation.nutritionPerPortion
import com.example.ezmakro.core.domain.calculation.totalNutrition
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.presentation.util.formatValue

/**
 * Pre-formatted nutrition block used for the recipe summaries (total,
 * per portion, per 100 g). All values are strings so composables stay free of
 * formatting logic.
 */
data class RecipeNutritionUi(
    val kcal: String,
    val proteinG: String,
    val fatG: String,
    val carbsG: String,
    val fiberG: String,
)

/**
 * Presentation model for a [Recipe] in lists and detail. Nutrition values are
 * pre-formatted strings so composables stay free of formatting logic. Per-100 g
 * is nullable by design (ADR-003): it exists only when a final weight was
 * measured, otherwise an estimate based on raw weights is used; only when
 * neither applies is [per100gMacros] null and the UI shows the hint.
 */
data class RecipeUi(
    val id: String,
    val name: String,
    val category: String?,
    val portions: Int,
    val ingredientCount: Int,
    /** Weight of one portion in grams; estimated from raw weights when no final weight is set. */
    val gramsPerPortion: Double? = null,
    val isEstimated: Boolean = false,
    val isFavorite: Boolean = false,
    val totalMacros: RecipeNutritionUi,
    val perPortionMacros: RecipeNutritionUi,
    val per100gMacros: RecipeNutritionUi?,
)

fun Recipe.toRecipeUi(): RecipeUi {
    val total = totalNutrition()
    val perPortion = nutritionPerPortion()
    val per100g = nutritionPer100g()
    val per100gMacros =
        if (per100g != null) {
            per100g.toRecipeNutritionUi()
        } else if (finalWeight == null) {
            estimatedPer100g().toRecipeNutritionUi()
        } else {
            null
        }
    return RecipeUi(
        id = id.value,
        name = name,
        category = category,
        portions = portions,
        ingredientCount = ingredients.size,
        gramsPerPortion = gramsPerPortion().takeIf { it > 0.0 },
        isEstimated = finalWeight == null,
        isFavorite = isFavorite,
        totalMacros = total.toRecipeNutritionUi(),
        perPortionMacros = perPortion.toRecipeNutritionUi(),
        per100gMacros = per100gMacros,
    )
}

private fun NutritionValues.toRecipeNutritionUi(): RecipeNutritionUi =
    RecipeNutritionUi(
        kcal = kcal.formatValue(),
        proteinG = proteinG.formatValue(),
        fatG = fatG.formatValue(),
        carbsG = carbsG.formatValue(),
        fiberG = fiberG.formatValue(),
    )

fun RecipeId.toRecipeUiId(): String = value
