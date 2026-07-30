package com.example.ezmakro.core.database.testutil

import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity

object RecipeFixtures {
    fun recipe(
        id: String = "r1",
        name: String = "Pfannkuchen",
        finalWeightGrams: Double? = null,
        portions: Int = 2,
        category: String? = null,
        isFavorite: Boolean = false,
        lastUsedAt: Long? = null,
    ) = RecipeEntity(
        id = id,
        name = name,
        finalWeightGrams = finalWeightGrams,
        portions = portions,
        category = category,
        isFavorite = if (isFavorite) 1 else 0,
        lastUsedAt = lastUsedAt,
    )

    fun ingredient(
        recipeId: String = "r1",
        foodId: String = "f1",
        grams: Double = 100.0,
    ) = RecipeIngredientEntity(
        recipeId = recipeId,
        foodItemId = foodId,
        rawWeightGrams = grams,
        servingCount = null,
    )
}
