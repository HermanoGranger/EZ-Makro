package com.example.ezmakro.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "final_weight_grams")
    val finalWeightGrams: Double?,
    val portions: Int,
    val scale: Double,
    @ColumnInfo(name = "category")
    val category: String?,
    @ColumnInfo(name = "is_meal_prep", defaultValue = "0")
    val isMealPrep: Boolean,
    @ColumnInfo(name = "last_modified_at", defaultValue = "0")
    val lastModifiedAt: Long = 0L,
)

@Entity(
    tableName = "recipe_ingredients",
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
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["food_item_id"]), Index(value = ["recipe_id"])],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0,
    @ColumnInfo(name = "recipe_id")
    val recipeId: String,
    @ColumnInfo(name = "food_item_id")
    val foodItemId: String?,
    @ColumnInfo(name = "raw_weight_grams")
    val rawWeightGrams: Double?,
    @ColumnInfo(name = "serving_count")
    val servingCount: Double?,
    @ColumnInfo(name = "recipe_snapshot_json")
    val recipeSnapshotJson: String? = null,
)
