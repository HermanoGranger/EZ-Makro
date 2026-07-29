package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RecipeSnapshot(
    val recipeId: RecipeId,
    val recipeName: String,
    val nutritionPer100g: NutritionValues,
    val originalLastModifiedAt: Long?,
    val portions: Int,
    val gramsPerPortion: Double,
)
