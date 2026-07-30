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
            "RecipeIngredient needs at least one of rawWeight or servingCount"
        }
        if (servingCount != null) {
            require(servingCount >= 0.0) { "servingCount must be non-negative" }
            if (food != null && rawWeight == null) {
                require(food.servingUnit != null) {
                    "servingCount requires the food to define a ServingUnit when rawWeight is not set"
                }
            }
        }
    }

    fun effectiveRawWeight(): RawWeight =
        if (rawWeight != null) {
            rawWeight
        } else if (recipeSnapshot != null) {
            RawWeight(recipeSnapshot.gramsPerPortion * checkNotNull(servingCount))
        } else {
            RawWeight(checkNotNull(food?.servingUnit).toGrams(checkNotNull(servingCount)))
        }
}
