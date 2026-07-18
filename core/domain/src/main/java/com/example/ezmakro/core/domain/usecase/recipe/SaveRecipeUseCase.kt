package com.example.ezmakro.core.domain.usecase.recipe

import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult

/**
 * Persists a new or updated [Recipe]. The domain model already enforces all
 * ADR-003 invariants (>=1 ingredient, positive portions/scale, raw-weight math),
 * so this use case only delegates to the repository.
 */
class SaveRecipeUseCase(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(recipe: Recipe): EmptyResult<DataError.Local> = repository.save(recipe)
}
