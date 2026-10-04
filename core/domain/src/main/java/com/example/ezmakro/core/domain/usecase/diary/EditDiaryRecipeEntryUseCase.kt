package com.example.ezmakro.core.domain.usecase.diary

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.map

/**
 * How far a recipe edit inside the diary reaches.
 *
 * [ENTRY_ONLY] adjusts only the logged day — the recipe itself stays untouched.
 * [RECIPE_AND_ENTRY] changes the recipe permanently and refreshes the logged day
 * with the same values.
 */
enum class DiaryRecipeEditScope {
    ENTRY_ONLY,
    RECIPE_AND_ENTRY,
}

/**
 * Edit the recipe snapshot of a logged diary entry, optionally writing the
 * change through to the recipe itself.
 *
 * A diary entry is an immutable historical document, so both scopes produce a
 * new entry snapshot. The difference is the write order: the recipe is written
 * **first** and the diary entry only afterwards. A diary entry showing a
 * permanently intended change that is missing from the recipe would be data loss;
 * an entry keeping its own snapshot after a recipe update is correct. A failing
 * recipe write therefore leaves the diary untouched and propagates the error.
 *
 * The recipe itself is derived from the **live** recipe, not from the diary
 * snapshot: `name`, `category`, `isFavorite` and `lastUsedAt` are taken over
 * unchanged, otherwise stale snapshot metadata would overwrite it.
 *
 * Editing is not a new usage, so `RecipeRepository.markUsed` is deliberately not
 * called — the recipe would otherwise jump to the top of `observeRecent` although
 * nothing was cooked.
 */
class EditDiaryRecipeEntryUseCase(
    private val diaryRepository: DiaryRepository,
    private val recipeRepository: RecipeRepository,
) {
    suspend operator fun invoke(
        entryId: DiaryEntryId,
        editedRecipe: Recipe,
        amountGrams: Double?,
        portionCount: Double?,
        scope: DiaryRecipeEditScope,
    ): EmptyResult<DataError.Local> {
        val entry =
            when (val result = diaryRepository.getById(entryId).notNull()) {
                is Result.Error -> return Result.Error(result.error)
                is Result.Success -> result.data
            }
        val write =
            when (val result = resolveWrite(entry, editedRecipe, scope)) {
                is Result.Error -> return Result.Error(result.error)
                is Result.Success -> result.data
            }
        return diaryRepository.save(
            entry.copy(
                recipe = write.recipe,
                amountGrams = amountGrams,
                portionCount = portionCount,
                isRecipeOverridden = write.isRecipeOverridden,
            ),
        )
    }

    private suspend fun resolveWrite(
        entry: DiaryEntry,
        editedRecipe: Recipe,
        scope: DiaryRecipeEditScope,
    ): Result<ResolvedSnapshot, DataError.Local> {
        val snapshot = entry.recipe ?: return Result.Error(DataError.Local.NOT_FOUND)
        if (scope == DiaryRecipeEditScope.ENTRY_ONLY) {
            // Der Snapshot behält seinen Zeitstempel, damit der Vergleich
            // "Rezept inzwischen geändert" weiterhin stimmt.
            val dayOnly = editedRecipe.copy(lastModifiedAt = snapshot.lastModifiedAt)
            return Result.Success(ResolvedSnapshot(dayOnly, isRecipeOverridden = true))
        }
        return writeRecipeAndSnapshot(snapshot, editedRecipe)
    }

    private suspend fun writeRecipeAndSnapshot(
        snapshot: Recipe,
        editedRecipe: Recipe,
    ): Result<ResolvedSnapshot, DataError.Local> {
        val live =
            when (val result = recipeRepository.getById(snapshot.id).notNull()) {
                // Ein gelöschtes Quellrezept wird nicht neu angelegt.
                is Result.Error -> return Result.Error(result.error)
                is Result.Success -> result.data
            }
        // Ein Zeitstempel für beide Schreibvorgänge: unterschiedliche Werte würden
        // sofort ein falsches "Rezept geändert" anzeigen.
        val now = System.currentTimeMillis()
        val saved =
            live.copy(
                ingredients = editedRecipe.ingredients,
                portions = editedRecipe.portions,
                finalWeight = editedRecipe.finalWeight,
                lastModifiedAt = now,
            )
        return recipeRepository.save(saved).map {
            ResolvedSnapshot(saved, isRecipeOverridden = false)
        }
    }

    private data class ResolvedSnapshot(
        val recipe: Recipe,
        val isRecipeOverridden: Boolean,
    )

    private fun <T : Any> Result<T?, DataError.Local>.notNull(): Result<T, DataError.Local> =
        when (this) {
            is Result.Error -> this
            is Result.Success ->
                this.data?.let { Result.Success(it) }
                    ?: Result.Error(DataError.Local.NOT_FOUND)
        }
}
