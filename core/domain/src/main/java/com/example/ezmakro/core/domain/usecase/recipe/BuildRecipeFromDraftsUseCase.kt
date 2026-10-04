package com.example.ezmakro.core.domain.usecase.recipe

import com.example.ezmakro.core.domain.calculation.createRecipeSnapshot
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.domain.result.Error
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.util.parseNonNegativeDouble

/**
 * Turns the untyped form state of a recipe editor ([RecipeIngredientDraft])
 * into typed [RecipeIngredient]s. The boundary from `String` form state to
 * typed domain values is exactly what ADR-003 asks for; expected problems are
 * returned as [RecipeDraftError] instead of being thrown.
 */
class BuildRecipeFromDraftsUseCase {
    operator fun invoke(
        drafts: List<RecipeIngredientDraft>,
        foods: List<FoodItem>,
        recipes: List<Recipe>,
    ): Result<List<RecipeIngredient>, RecipeDraftError> {
        val result = ArrayList<RecipeIngredient>(drafts.size)
        for (draft in drafts) {
            val resolved =
                if (draft.recipeId != null) {
                    resolveRecipeIngredient(draft, recipes)
                } else {
                    resolveFoodIngredient(draft, foods)
                }
            when (resolved) {
                is Result.Success -> result += resolved.data
                is Result.Error -> return Result.Error(resolved.error)
            }
        }
        return Result.Success(result)
    }

    private fun resolveRecipeIngredient(
        draft: RecipeIngredientDraft,
        recipes: List<Recipe>,
    ): Result<RecipeIngredient, RecipeDraftError> {
        val rawWeight = draft.rawWeight.parseNonNegativeDouble()?.let { RawWeight(it) }
        val servingCount = draft.servingCount.parseNonNegativeDouble()
        val hasPositiveAmount =
            (rawWeight != null && rawWeight.grams > 0.0) || (servingCount != null && servingCount > 0.0)
        if (!hasPositiveAmount) return Result.Error(RecipeDraftError.MissingAmount)
        // A deleted sub-recipe still keeps its stored snapshot in the parent
        // recipe; fall back to it so a mere save does not fail.
        val recipe = recipes.firstOrNull { it.id.value == draft.recipeId }
        val snapshot =
            recipe?.let { createRecipeSnapshot(it) }
                ?: draft.recipeSnapshot
                ?: return Result.Error(RecipeDraftError.MissingSnapshot)
        return Result.Success(
            RecipeIngredient(
                food = null,
                recipeSnapshot = snapshot,
                rawWeight = rawWeight,
                servingCount = servingCount,
            ),
        )
    }

    private fun resolveFoodIngredient(
        draft: RecipeIngredientDraft,
        foods: List<FoodItem>,
    ): Result<RecipeIngredient, RecipeDraftError> {
        val rawWeight = draft.rawWeight.parseNonNegativeDouble()?.let { RawWeight(it) }
        val servingCount = draft.servingCount.parseNonNegativeDouble()
        val hasPositiveWeight =
            (rawWeight != null && rawWeight.grams > 0.0) || (servingCount != null && servingCount > 0.0)
        val food = foods.firstOrNull { it.id.value == draft.foodId }
        val needsServingUnit = servingCount != null && rawWeight == null
        val missingAmount = !hasPositiveWeight || (needsServingUnit && food?.servingUnit == null)
        if (missingAmount) return Result.Error(RecipeDraftError.MissingAmount)
        val resolvedFood = food ?: return Result.Error(RecipeDraftError.UnknownFood)
        return Result.Success(
            RecipeIngredient(
                food = resolvedFood,
                recipeSnapshot = null,
                rawWeight = rawWeight,
                servingCount = servingCount,
            ),
        )
    }
}

sealed interface RecipeDraftError : Error {
    /** Weder Rohgewicht noch Anzahl gesetzt (bzw. beides 0). */
    data object MissingAmount : RecipeDraftError

    /** Food-Id nicht in `foods` — z. B. wurde das Lebensmittel gelöscht. */
    data object UnknownFood : RecipeDraftError

    /** Sub-Rezept fehlt und es liegt kein älterer Snapshot im Draft. */
    data object MissingSnapshot : RecipeDraftError
}
