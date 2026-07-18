package com.example.ezmakro.feature.recipes.presentation.model

import com.example.ezmakro.core.domain.calculation.nutritionPer100g
import com.example.ezmakro.core.domain.calculation.nutritionPerPortion
import com.example.ezmakro.core.domain.calculation.totalNutrition
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId

/**
 * Presentation model for a [Recipe] in lists and detail. Nutrition values are
 * pre-formatted strings so composables stay free of formatting logic. Per-100 g
 * is nullable by design (ADR-003): it exists only when a final weight was
 * measured, so [per100gKcal] is null in that case and the UI shows the hint.
 */
data class RecipeUi(
    val id: String,
    val name: String,
    val category: String?,
    val isMealPrep: Boolean,
    val portions: Int,
    val ingredientCount: Int,
    val totalKcal: String,
    val perPortionKcal: String,
    val per100gKcal: String?,
)

private const val MACRO_DECIMAL_PLACES = 10.0

private fun Double.formatMacro(): String {
    val rounded = Math.round(this * MACRO_DECIMAL_PLACES) / MACRO_DECIMAL_PLACES
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

fun Recipe.toRecipeUi(): RecipeUi {
    val total = totalNutrition()
    val perPortion = nutritionPerPortion()
    val per100g = nutritionPer100g()
    return RecipeUi(
        id = id.value,
        name = name,
        category = category,
        isMealPrep = isMealPrep,
        portions = portions,
        ingredientCount = ingredients.size,
        totalKcal = total.kcal.formatMacro(),
        perPortionKcal = perPortion.kcal.formatMacro(),
        per100gKcal = per100g?.kcal?.formatMacro(),
    )
}

fun RecipeId.toRecipeUiId(): String = value
