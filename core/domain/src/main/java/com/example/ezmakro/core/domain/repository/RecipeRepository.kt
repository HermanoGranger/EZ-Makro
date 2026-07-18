package com.example.ezmakro.core.domain.repository

import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first coordination for recipes (ADR-002): the UI observes local flows
 * only. Recipes have no remote source — they are always user-authored.
 *
 * ADR-003 invariants are enforced at the domain-model level ([Recipe],
 * [com.example.ezmakro.core.domain.model.RawWeight] / FinalWeight); this
 * repository only persists and retrieves what the model already validated.
 */
interface RecipeRepository {
    /** Observe all recipes, name-sorted (Single Source of Truth). */
    fun observeAll(): Flow<List<Recipe>>

    /** Observe a single recipe by id, or null when it does not exist. */
    fun observeById(id: RecipeId): Flow<Recipe?>

    /** Persist a new or updated recipe (ingredients written atomically). */
    suspend fun save(recipe: Recipe): EmptyResult<DataError.Local>

    /** Delete a recipe and its ingredient lines (cascade). */
    suspend fun delete(id: RecipeId): EmptyResult<DataError.Local>

    /** Duplicate an existing recipe under a new id with a "(Kopie)" suffix. */
    suspend fun duplicate(id: RecipeId): Result<RecipeId, DataError.Local>
}
