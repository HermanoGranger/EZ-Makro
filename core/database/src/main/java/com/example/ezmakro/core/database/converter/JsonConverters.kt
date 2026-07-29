package com.example.ezmakro.core.database.converter

import androidx.room.TypeConverter
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeSnapshot
import kotlinx.serialization.json.Json

/**
 * Room [TypeConverter]s for the complex diary snapshots that are stored as JSON.
 *
 * A [com.example.ezmakro.core.domain.model.DiaryEntry] is an immutable historical
 * record: at creation time the referenced [FoodItem] / [Recipe] is materialized
 * into a JSON snapshot so the entry can render and calculate its (historical)
 * nutrition without reloading the source entity (see `DiaryEntryEntity`).
 *
 * `kotlinx.serialization` is used (domain models are `@Serializable`); the
 * [Json] instance is lenient and ignores unknown keys so older snapshots keep
 * deserializing after the domain model evolves.
 */
object JsonConverters {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @TypeConverter
    fun foodItemToJson(value: FoodItem?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToFoodItem(value: String?): FoodItem? = value?.let { json.decodeFromString(it) }

    @TypeConverter
    fun recipeToJson(value: Recipe?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToRecipe(value: String?): Recipe? = value?.let { json.decodeFromString(it) }

    // Non-`@TypeConverter` entry points so the data-layer mapper can serialize
    // snapshots without going through Room column binding.
    fun encodeFoodItem(value: FoodItem): String = json.encodeToString(value)

    fun decodeFoodItem(value: String): FoodItem = json.decodeFromString(value)

    fun encodeRecipe(value: Recipe): String = json.encodeToString(value)

    fun decodeRecipe(value: String): Recipe = json.decodeFromString(value)

    fun encodeRecipeSnapshot(value: RecipeSnapshot): String = json.encodeToString(value)

    fun decodeRecipeSnapshot(value: String): RecipeSnapshot = json.decodeFromString(value)
}
