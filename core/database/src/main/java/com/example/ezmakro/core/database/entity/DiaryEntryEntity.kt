package com.example.ezmakro.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent form of [com.example.ezmakro.core.domain.model.DiaryEntry].
 *
 * A diary entry is an **immutable historical record** of a logged consumption.
 * At creation time the relevant descriptive and nutritional data of the
 * referenced [FoodItem] / [Recipe] is materialized into the JSON snapshot
 * columns ([foodSnapshot] / [recipeSnapshot]) so the entry never needs to
 * reload the source entity to render or calculate its historical values.
 *
 * The original source id ([foodItemId] / [recipeId]) is retained for
 * traceability only; it is NOT used to resolve nutrition.
 *
 * [amountGrams] (raw amount for food, or grams of prepared dish) and
 * [portionCount] are mutually exclusive for recipe entries, matching the domain
 * invariants.
 */
@Entity(
    tableName = "diary_entries",
    indices = [
        Index(value = ["date"]),
        Index(value = ["food_item_id"]),
        Index(value = ["recipe_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DiaryEntryEntity(
    @PrimaryKey
    val id: String,
    val slot: String,
    val date: String,
    @ColumnInfo(name = "food_item_id")
    val foodItemId: String?,
    @ColumnInfo(name = "recipe_id")
    val recipeId: String?,
    @ColumnInfo(name = "amount_grams")
    val amountGrams: Double?,
    @ColumnInfo(name = "portion_count")
    val portionCount: Double?,
    @ColumnInfo(name = "food_snapshot", defaultValue = "NULL")
    val foodSnapshot: String? = null,
    @ColumnInfo(name = "recipe_snapshot", defaultValue = "NULL")
    val recipeSnapshot: String? = null,
)
