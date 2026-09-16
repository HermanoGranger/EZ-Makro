package com.example.ezmakro.core.data.recipe.mapper

import com.example.ezmakro.core.database.converter.JsonConverters
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

suspend fun RecipeEntity.toRecipe(
    ingredientEntities: List<RecipeIngredientEntity>,
    foodLocalDataSource: FoodLocalDataSource,
): Recipe? {
    val ingredients = ArrayList<RecipeIngredient>(ingredientEntities.size)
    for (ing in ingredientEntities) {
        resolveIngredient(ing, foodLocalDataSource)?.let { ingredients += it }
    }
    if (ingredients.isEmpty()) return null
    return Recipe(
        id = RecipeId(id),
        name = name,
        ingredients = ingredients,
        finalWeight = finalWeightGrams?.let { FinalWeight(it) },
        portions = portions,
        category = category,
        lastModifiedAt = lastModifiedAt,
        isFavorite = isFavorite == 1,
        lastUsedAt = lastUsedAt,
    )
}

private suspend fun resolveIngredient(
    ing: RecipeIngredientEntity,
    foodLocalDataSource: FoodLocalDataSource,
): RecipeIngredient? {
    val food =
        ing.foodItemId
            ?.let { id ->
                when (val foodResult = foodLocalDataSource.getById(FoodId(id))) {
                    is Result.Success -> foodResult.data
                    is Result.Error -> null
                }
            }
    val rawWeight = ing.rawWeightGrams?.let { RawWeight(it) }
    val amountDefined = rawWeight != null || ing.servingCount != null
    val servingUnitMissing = ing.servingCount != null && rawWeight == null && food?.servingUnit == null
    val snapshot =
        if (food == null) {
            ing.recipeSnapshotJson?.let { JsonConverters.decodeRecipeSnapshot(it) }
        } else {
            null
        }
    return when {
        food != null && !servingUnitMissing && amountDefined ->
            RecipeIngredient(
                food = food,
                recipeSnapshot = null,
                rawWeight = rawWeight,
                servingCount = ing.servingCount,
            )
        snapshot != null && amountDefined ->
            RecipeIngredient(
                food = null,
                recipeSnapshot = snapshot,
                rawWeight = rawWeight,
                servingCount = ing.servingCount,
            )
        else -> null
    }
}

fun Recipe.toEntities(): Pair<RecipeEntity, List<RecipeIngredientEntity>> {
    val recipeEntity =
        RecipeEntity(
            id = id.value,
            name = name,
            finalWeightGrams = finalWeight?.grams,
            portions = portions,
            category = category,
            lastModifiedAt = lastModifiedAt,
            isFavorite = if (isFavorite) 1 else 0,
            lastUsedAt = lastUsedAt,
        )
    val ingredientEntities =
        ingredients.map { ing ->
            RecipeIngredientEntity(
                recipeId = id.value,
                foodItemId = ing.food?.id?.value,
                rawWeightGrams = ing.rawWeight?.grams,
                servingCount = ing.servingCount,
                recipeSnapshotJson = ing.recipeSnapshot?.let { JsonConverters.encodeRecipeSnapshot(it) },
            )
        }
    return recipeEntity to ingredientEntities
}
