package com.example.ezmakro.core.domain.usecase.recipe

import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.first

/**
 * Returns a copy of [Recipe] scaled by [factor]. Scaling only changes the dish
 * yield via [Recipe.scale]; ingredient raw weights stay fixed, so the
 * per-100 g basis is unaffected and only the absolute totals grow (ADR-003).
 */
class ScaleRecipeUseCase(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(
        id: RecipeId,
        factor: Double,
    ): Result<Recipe, DataError.Local> {
        require(factor > 0.0) { "scale factor must be positive, was $factor" }
        val source =
            repository.observeById(id).first()
                ?: return Result.Error(DataError.Local.NOT_FOUND)
        return Result.Success(source.copy(scale = source.scale * factor))
    }
}
