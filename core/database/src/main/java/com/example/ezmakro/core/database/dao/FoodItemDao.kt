package com.example.ezmakro.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.ezmakro.core.database.entity.FoodFtsEntity
import com.example.ezmakro.core.database.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local data access for [FoodItemEntity]. Acts as the Single Source of Truth
 * for food data (ADR-002). Includes ADR-004 status transition operations and
 * FTS5-backed name search.
 */
@Dao
@Suppress("TooManyFunctions")
interface FoodItemDao {
    /**
     * Insert or replace a food by its stable [id]. [rowId] is an auto-generated
     * surrogate key (also the FTS4 `rowid`), so upsert semantics are keyed on
     * [id] rather than the surrogate PK via `ON CONFLICT(id) DO UPDATE`.
     */
    @Suppress("LongParameterList")
    @Query(
        """
        INSERT INTO food_items (
            id, name, barcode,
            nutri_kcal, nutri_protein_g, nutri_fat_g, nutri_carbs_g, nutri_fiber_g,
            serving_label, serving_grams_equivalent, is_user_created, cached_status,
            orig_name, orig_barcode, orig_nutri_kcal, orig_nutri_protein_g,
            orig_nutri_fat_g, orig_nutri_carbs_g, orig_nutri_fiber_g,
            orig_serving_label, orig_serving_grams_equivalent, orig_is_user_created,
            is_favorite, last_used_at
        ) VALUES (
            :id, :name, :barcode,
            :nutriKcal, :nutriProteinG, :nutriFatG, :nutriCarbsG, :nutriFiberG,
            :servingLabel, :servingGramsEquivalent, :isUserCreated, :cachedStatus,
            :origName, :origBarcode, :origNutriKcal, :origNutriProteinG,
            :origNutriFatG, :origNutriCarbsG, :origNutriFiberG,
            :origServingLabel, :origServingGrams, :origIsUserCreated,
            0, NULL
        )
        ON CONFLICT(id) DO UPDATE SET
            name = excluded.name,
            barcode = excluded.barcode,
            nutri_kcal = excluded.nutri_kcal,
            nutri_protein_g = excluded.nutri_protein_g,
            nutri_fat_g = excluded.nutri_fat_g,
            nutri_carbs_g = excluded.nutri_carbs_g,
            nutri_fiber_g = excluded.nutri_fiber_g,
            serving_label = excluded.serving_label,
            serving_grams_equivalent = excluded.serving_grams_equivalent,
            is_user_created = excluded.is_user_created,
            cached_status = excluded.cached_status,
            orig_name = excluded.orig_name,
            orig_barcode = excluded.orig_barcode,
            orig_nutri_kcal = excluded.orig_nutri_kcal,
            orig_nutri_protein_g = excluded.orig_nutri_protein_g,
            orig_nutri_fat_g = excluded.orig_nutri_fat_g,
            orig_nutri_carbs_g = excluded.orig_nutri_carbs_g,
            orig_nutri_fiber_g = excluded.orig_nutri_fiber_g,
            orig_serving_label = excluded.orig_serving_label,
            orig_serving_grams_equivalent = excluded.orig_serving_grams_equivalent,
            orig_is_user_created = excluded.orig_is_user_created
        """,
    )
    fun upsert(
        id: String,
        name: String,
        barcode: String?,
        nutriKcal: Double?,
        nutriProteinG: Double?,
        nutriFatG: Double?,
        nutriCarbsG: Double?,
        nutriFiberG: Double?,
        servingLabel: String?,
        servingGramsEquivalent: Double?,
        isUserCreated: Boolean,
        cachedStatus: String,
        origName: String?,
        origBarcode: String?,
        origNutriKcal: Double?,
        origNutriProteinG: Double?,
        origNutriFatG: Double?,
        origNutriCarbsG: Double?,
        origNutriFiberG: Double?,
        origServingLabel: String?,
        origServingGrams: Double?,
        origIsUserCreated: Boolean?,
    )

    /** Insert or replace many foods. Keyed on the surrogate PK; see [upsert]. */
    @androidx.room.Upsert
    fun upsertAll(entities: List<FoodItemEntity>)

    /** Delete a food by its stable [FoodItemEntity.id]. */
    @Query("DELETE FROM food_items WHERE id = :id")
    fun deleteById(id: String)

    @Query("SELECT * FROM food_items WHERE id = :id")
    fun getById(id: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    fun getByBarcode(barcode: String): FoodItemEntity?

    @Query("SELECT * FROM food_items ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE is_favorite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<FoodItemEntity>>

    @Query(
        "SELECT * FROM food_items WHERE last_used_at IS NOT NULL " +
            "ORDER BY last_used_at DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<FoodItemEntity>>

    /** Toggle/set the favorite flag. Never touched by [upsert] re-syncs. */
    @Query("UPDATE food_items SET is_favorite = :favorite WHERE id = :id")
    fun setFavorite(
        id: String,
        favorite: Boolean,
    )

    /** Stamp last-used for recent ordering. Never touched by [upsert] re-syncs. */
    @Query("UPDATE food_items SET last_used_at = :usedAt WHERE id = :id")
    fun markUsed(
        id: String,
        usedAt: Long,
    )

    @Query("SELECT * FROM food_items WHERE is_user_created = 0 ORDER BY name COLLATE NOCASE")
    fun observeCached(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE is_user_created = 1 ORDER BY name COLLATE NOCASE")
    fun observeUserCreated(): Flow<List<FoodItemEntity>>

    /**
     * FTS4 search over the food name. Returns the matching FTS rows (whose
     * [FoodFtsEntity.rowid] equals the [FoodItemEntity.rowId]); the repository
     * resolves the full [FoodItemEntity] instances. The query is expected to be
     * pre-normalized (lowercased, umlauts folded, optional `*` prefix for
     * partial matches) by the caller.
     */
    @Query("SELECT * FROM food_items_fts WHERE food_items_fts MATCH :query")
    fun searchByName(query: String): List<FoodFtsEntity>

    /** Return the integer row ids (FTS `rowid`) matching the FTS query. */
    @Query("SELECT rowid FROM food_items_fts WHERE food_items_fts MATCH :query")
    fun searchRowIds(query: String): List<Int>

    /** Resolve full food entities by their integer row ids (FTS rowid). */
    @Query("SELECT * FROM food_items WHERE row_id IN (:rowIds)")
    fun getByRowIds(rowIds: List<Int>): List<FoodItemEntity>

    /** ADR-004: mark a cached entry as locally edited. */
    @Query("UPDATE food_items SET cached_status = 'USER_MODIFIED' WHERE id = :id")
    fun markUserModified(id: String)

    /** ADR-004: a newer OFF version exists; keep local edits, flag availability. */
    @Query(
        "UPDATE food_items SET cached_status = 'UPDATE_AVAILABLE' " +
            "WHERE id = :id AND cached_status != 'USER_MODIFIED'",
    )
    fun markUpdateAvailable(id: String)

    /**
     * ADR-004: restore the untouched OFF payload. Overwrites the editable
     * columns from the stored [origName]/[origBarcode]/... snapshot and resets
     * status to ORIGINAL.
     */
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE food_items
        SET name = :origName,
            barcode = :origBarcode,
            nutri_kcal = :origKcal,
            nutri_protein_g = :origProteinG,
            nutri_fat_g = :origFatG,
            nutri_carbs_g = :origCarbsG,
            nutri_fiber_g = :origFiberG,
            serving_label = :origServingLabel,
            serving_grams_equivalent = :origServingGrams,
            is_user_created = :origIsUserCreated,
            cached_status = 'ORIGINAL'
        WHERE id = :id
        """,
    )
    fun resetToOriginal(
        id: String,
        origName: String,
        origBarcode: String?,
        origKcal: Double?,
        origProteinG: Double?,
        origFatG: Double?,
        origCarbsG: Double?,
        origFiberG: Double?,
        origServingLabel: String?,
        origServingGrams: Double?,
        origIsUserCreated: Boolean,
    )
}
