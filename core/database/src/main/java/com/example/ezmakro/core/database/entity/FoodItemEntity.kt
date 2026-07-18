package com.example.ezmakro.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent form of [com.example.ezmakro.core.domain.model.FoodItem].
 *
 * [nutritionPer100g] is stored as five explicit columns (not JSON) so it stays
 * queryable and avoids any serialization dependency in the domain module. The
 * ADR-004 original snapshot (untouched OFF payload) is stored as explicit
 * `orig_*` columns so "reset to original" is always possible.
 */
@Entity(
    tableName = "food_items",
    indices = [
        Index(value = ["barcode"], unique = false),
        Index(value = ["cached_status"]),
        Index(value = ["id"], unique = true),
        Index(value = ["is_favorite"]),
        Index(value = ["last_used_at"]),
    ],
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Int = 0,
    val id: String,
    val name: String,
    @ColumnInfo(name = "barcode")
    val barcode: String?,
    @ColumnInfo(name = "nutri_kcal")
    val nutriKcal: Double,
    @ColumnInfo(name = "nutri_protein_g")
    val nutriProteinG: Double,
    @ColumnInfo(name = "nutri_fat_g")
    val nutriFatG: Double,
    @ColumnInfo(name = "nutri_carbs_g")
    val nutriCarbsG: Double,
    @ColumnInfo(name = "nutri_fiber_g")
    val nutriFiberG: Double,
    @ColumnInfo(name = "serving_label")
    val servingLabel: String?,
    @ColumnInfo(name = "serving_grams_equivalent")
    val servingGramsEquivalent: Double?,
    @ColumnInfo(name = "is_user_created")
    val isUserCreated: Boolean,
    @ColumnInfo(name = "cached_status")
    val cachedStatus: String,
    // ADR-004 original snapshot (untouched OFF payload) stored as explicit
    // columns so it stays queryable and avoids referencing the domain FoodItem
    // type from the database layer.
    @ColumnInfo(name = "orig_name")
    val origName: String?,
    @ColumnInfo(name = "orig_barcode")
    val origBarcode: String?,
    @ColumnInfo(name = "orig_nutri_kcal")
    val origNutriKcal: Double?,
    @ColumnInfo(name = "orig_nutri_protein_g")
    val origNutriProteinG: Double?,
    @ColumnInfo(name = "orig_nutri_fat_g")
    val origNutriFatG: Double?,
    @ColumnInfo(name = "orig_nutri_carbs_g")
    val origNutriCarbsG: Double?,
    @ColumnInfo(name = "orig_nutri_fiber_g")
    val origNutriFiberG: Double?,
    @ColumnInfo(name = "orig_serving_label")
    val origServingLabel: String?,
    @ColumnInfo(name = "orig_serving_grams_equivalent")
    val origServingGramsEquivalent: Double?,
    @ColumnInfo(name = "orig_is_user_created")
    val origIsUserCreated: Boolean?,
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,
    // Epoch-milli timestamp of last use; null until first used (recent ordering).
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
)

/**
 * FTS4 virtual table mirroring [FoodItemEntity.name] for local full-text search.
 * `contentEntity` links it to the parent table; updates to [FoodItemEntity] are
 * reflected here via the Room FTS `content` sync.
 *
 * Search is normalized by the caller (lowercase, umlaut folding done in the
 * DAO query) so case and umlauts match.
 */
@Fts4(
    contentEntity = FoodItemEntity::class,
    tokenizer = "unicode61",
    tokenizerArgs = ["remove_diacritics=1"],
)
@Entity(tableName = "food_items_fts")
data class FoodFtsEntity(
    val name: String,
)
