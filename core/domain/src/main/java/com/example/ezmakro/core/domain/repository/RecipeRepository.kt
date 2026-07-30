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

    /** Observe favorited recipes for the favorites section of the recipe picker. */
    fun observeFavorites(): Flow<List<Recipe>>

    /**
     * Observe the [limit] most recently used recipes, newest first. Used-tracking
     * is wired with the meal log in Phase 7; until then this is empty.
     */
    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<Recipe>>

    /** Fetch a single recipe by id, or null when it does not exist. */
    suspend fun getById(id: RecipeId): Result<Recipe?, DataError.Local>

    /** Persist a new or updated recipe (ingredients written atomically). */
    suspend fun save(recipe: Recipe): EmptyResult<DataError.Local>

    /** Delete a recipe and its ingredient lines (cascade). */
    suspend fun delete(id: RecipeId): EmptyResult<DataError.Local>

    /** Duplicate an existing recipe under a new id with a "(Kopie)" suffix. */
    suspend fun duplicate(id: RecipeId): Result<RecipeId, DataError.Local>

    /** Toggle the favorite flag for a recipe. */
    suspend fun toggleFavorite(id: RecipeId): EmptyResult<DataError.Local>

    /**
     * Stamp a recipe as just used (recent ordering). Wired with the meal log in
     * Phase 7; defined now so the data contract is complete.
     */
    suspend fun markUsed(id: RecipeId): EmptyResult<DataError.Local>

    companion object {
        const val DEFAULT_RECENT_LIMIT = 20
    }
}
