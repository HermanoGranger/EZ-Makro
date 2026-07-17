package com.example.ezmakro.core.domain.model

/**
 * A single ingredient line inside a [Recipe]. Always references its food in the
 * RAW state ([rawWeight]); cooking-related weight changes are captured only at
 * the recipe level via [Recipe.finalWeight] (ADR-003).
 *
 * If the referenced [food] defines a [ServingUnit], the ingredient can instead
 * be expressed by [servingCount]; [rawWeight] is then derived. Exactly one of
 * [rawWeight] / [servingCount] must be provided.
 */
data class RecipeIngredient(
    val food: FoodItem,
    val rawWeight: RawWeight?,
    val servingCount: Double?,
) {
    init {
        require(rawWeight != null || servingCount != null) {
            "RecipeIngredient needs either rawWeight or servingCount"
        }
        require(!(rawWeight != null && servingCount != null)) {
            "RecipeIngredient must not specify both rawWeight and servingCount"
        }
        if (servingCount != null) {
            require(food.servingUnit != null) {
                "servingCount requires the food to define a ServingUnit"
            }
            require(servingCount >= 0.0) { "servingCount must be non-negative" }
        }
    }

    /**
     * Effective raw weight of this ingredient, resolving [servingCount] through
     * the food's [ServingUnit] when needed.
     */
    fun effectiveRawWeight(): RawWeight =
        rawWeight
            ?: RawWeight(checkNotNull(food.servingUnit).toGrams(checkNotNull(servingCount)))
}
