package com.example.ezmakro.core.data.recipe.mapper

import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.domain.datasource.FoodLocalDataSource
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.result.Result

/**
 * Rebuild a domain [Recipe] from its persisted entities. Ingredient foods are
 * referenced by id only ([RecipeIngredientEntity.foodItemId]); they are resolved
 * through [foodLocalDataSource]. A missing or unresolvable food (e.g. deleted)
 * is treated as a corrupt reference: the whole recipe is dropped (returns null)
 * so it never surfaces with silently lost ingredients in the UI.
 */
suspend fun RecipeEntity.toRecipe(
    ingredientEntities: List<RecipeIngredientEntity>,
    foodLocalDataSource: FoodLocalDataSource,
): Recipe? {
    val ingredients = ArrayList<RecipeIngredient>(ingredientEntities.size)
    var resolutionFailed = false
    for (ing in ingredientEntities) {
        val foodResult = foodLocalDataSource.getById(FoodId(ing.foodItemId))
        val food =
            when (foodResult) {
                is Result.Success -> foodResult.data
                is Result.Error -> null
            }
        if (food == null) {
            resolutionFailed = true
            break
        }
        ingredients +=
            RecipeIngredient(
                food = food,
                rawWeight = ing.rawWeightGrams?.let { RawWeight(it) },
                servingCount = ing.servingCount,
            )
    }
    if (resolutionFailed || ingredients.isEmpty()) return null
    return Recipe(
        id = RecipeId(id),
        name = name,
        ingredients = ingredients,
        finalWeight = finalWeightGrams?.let { FinalWeight(it) },
        portions = portions,
        scale = scale,
        category = category,
        isMealPrep = isMealPrep,
    )
}

/** Split a domain [Recipe] into its persistable entities. */
fun Recipe.toEntities(): Pair<RecipeEntity, List<RecipeIngredientEntity>> {
    val recipeEntity =
        RecipeEntity(
            id = id.value,
            name = name,
            finalWeightGrams = finalWeight?.grams,
            portions = portions,
            scale = scale,
            category = category,
            isMealPrep = isMealPrep,
        )
    val ingredientEntities =
        ingredients.map { ing ->
            RecipeIngredientEntity(
                recipeId = id.value,
                foodItemId = ing.food.id.value,
                rawWeightGrams = ing.rawWeight?.grams,
                servingCount = ing.servingCount,
            )
        }
    return recipeEntity to ingredientEntities
}
