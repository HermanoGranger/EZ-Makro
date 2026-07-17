package com.example.ezmakro.core.domain.model

/**
 * Aggregate Root: a recipe composed of raw ingredients.
 *
 * Per ADR-003:
 * - Total nutrition = sum of the ingredients' RAW nutrition (never changes by cooking).
 * - [finalWeight] is the OPTIONAL measured post-cooking weight. "Per 100 g" is
 *   derived exclusively from totalNutrition / finalWeight and must never be
 *   computed from the sum of raw weights.
 * - [portions] is the number of equal servings the dish is divided into. It is
 *   used to derive per-portion nutrition; the user can later log either a number
 *   of [portions] or an amount in grams (see [DiaryEntry]).
 * - [scale] allows meal-prep scaling of the whole dish without altering the
 *   ingredient list.
 */
data class Recipe(
    val id: RecipeId,
    val name: String,
    val ingredients: List<RecipeIngredient>,
    val finalWeight: FinalWeight?,
    val portions: Int,
    val scale: Double = 1.0,
) {
    init {
        require(name.isNotBlank()) { "Recipe name must not be blank" }
        require(portions > 0) { "portions must be positive, was $portions" }
        require(scale > 0.0) { "scale must be positive, was $scale" }
        require(ingredients.isNotEmpty()) { "Recipe must have at least one ingredient" }
    }
}
