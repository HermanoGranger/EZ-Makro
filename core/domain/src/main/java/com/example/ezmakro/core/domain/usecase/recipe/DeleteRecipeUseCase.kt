package com.example.ezmakro.core.domain.usecase.recipe

import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult

/**
 * Deletes a [Recipe] and its ingredient lines (cascade in the DAO).
 */
class DeleteRecipeUseCase(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(id: RecipeId): EmptyResult<DataError.Local> = repository.delete(id)
}
