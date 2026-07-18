package com.example.ezmakro.core.domain.usecase.recipe

import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Creates a copy of an existing [Recipe] under a fresh id. The copy keeps all
 * ingredients, nutrition math and portions but is renamed with a "(Kopie)"
 * suffix. Scale is preserved so a duplicated meal-prep batch stays identical.
 */
class DuplicateRecipeUseCase(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(id: RecipeId): Result<RecipeId, DataError.Local> {
        val source =
            repository.observeById(id).first()
                ?: return Result.Error(DataError.Local.NOT_FOUND)
        val copyId = RecipeId(UUID.randomUUID().toString())
        val copy =
            Recipe(
                id = copyId,
                name = "${source.name} (Kopie)",
                ingredients = source.ingredients,
                finalWeight = source.finalWeight,
                portions = source.portions,
                scale = source.scale,
                category = source.category,
                isMealPrep = source.isMealPrep,
            )
        return when (val saved: EmptyResult<DataError.Local> = repository.save(copy)) {
            is Result.Error -> saved
            is Result.Success -> Result.Success(copyId)
        }
    }
}
