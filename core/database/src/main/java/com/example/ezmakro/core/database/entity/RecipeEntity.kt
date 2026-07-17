package com.example.ezmakro.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent form of [com.example.ezmakro.core.domain.model.Recipe].
 *
 * Per ADR-003, [finalWeightGrams] is OPTIONAL (null when the user has not
 * measured the cooked dish). "Per 100 g" is derived only from total raw
 * nutrition / finalWeight and must never be computed from summed raw weights.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "final_weight_grams")
    val finalWeightGrams: Double?,
    val portions: Int,
    val scale: Double,
)

/**
 * One ingredient line of a [RecipeEntity]. Always expressed in the RAW state
 * ([rawWeightGrams]); per ADR-003 cooking-related weight changes live only at
 * the recipe level. When [servingCount] is set, [rawWeightGrams] is null and
 * resolved through the food's [ServingUnit] (see domain).
 */
@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipe_id", "food_item_id"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_item_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["food_item_id"])],
)
data class RecipeIngredientEntity(
    @ColumnInfo(name = "recipe_id")
    val recipeId: String,
    @ColumnInfo(name = "food_item_id")
    val foodItemId: String,
    @ColumnInfo(name = "raw_weight_grams")
    val rawWeightGrams: Double?,
    @ColumnInfo(name = "serving_count")
    val servingCount: Double?,
)
