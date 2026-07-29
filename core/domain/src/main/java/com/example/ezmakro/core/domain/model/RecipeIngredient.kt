package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RecipeIngredient(
    val food: FoodItem?,
    val recipeSnapshot: RecipeSnapshot? = null,
    val rawWeight: RawWeight?,
    val servingCount: Double?,
) {
    init {
        require(food != null || recipeSnapshot != null) {
            "RecipeIngredient needs either food or recipeSnapshot"
        }
        require(!(food != null && recipeSnapshot != null)) {
            "RecipeIngredient must not reference both food and recipeSnapshot"
        }
        require(rawWeight != null || servingCount != null) {
            "RecipeIngredient needs either rawWeight or servingCount"
        }
        require(!(rawWeight != null && servingCount != null)) {
            "RecipeIngredient must not specify both rawWeight and servingCount"
        }
        if (servingCount != null) {
            require(servingCount >= 0.0) { "servingCount must be non-negative" }
            if (food != null) {
                require(food.servingUnit != null) {
                    "servingCount requires the food to define a ServingUnit"
                }
            }
        }
    }

    fun effectiveRawWeight(): RawWeight =
        rawWeight
            ?: if (recipeSnapshot != null) {
                RawWeight(recipeSnapshot.gramsPerPortion * checkNotNull(servingCount))
            } else {
                RawWeight(checkNotNull(food?.servingUnit).toGrams(checkNotNull(servingCount)))
            }
}
