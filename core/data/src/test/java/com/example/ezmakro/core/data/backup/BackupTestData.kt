package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.data.food.mapper.toFoodItem
import com.example.ezmakro.core.database.converter.JsonConverters
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.database.entity.WeightLogEntity
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient

/**
 * Row and document fixtures for the WP3 backup tests.
 *
 * The seed set mirrors the A5 scope rules: one user-created food, one favorite
 * OFF food, one plain cached OFF food (only exported with the OFF-cache
 * switch), one non-favorite OFF food that a recipe references, one recipe
 * referencing that food and one diary entry per reference kind.
 *
 * Snapshots are produced through the real entity → domain mappers plus
 * [JsonConverters] so they are byte-identical to what the app writes — A8
 * round-trip assertions depend on that.
 */
object BackupTestData {
    const val OWN_FOOD = "food-own"
    const val FAVORITE_FOOD = "food-fav"
    const val CACHED_FOOD = "food-cached"
    const val REFERENCED_FOOD = "food-ref"
    const val RECIPE_ID = "recipe-1"
    const val DATE = "2026-10-05"

    fun food(
        id: String,
        name: String = "Haferflocken",
        isUserCreated: Boolean = false,
        isFavorite: Boolean = false,
        lastUsedAt: Long? = null,
        barcode: String? = null,
        cachedStatus: String = "ORIGINAL",
    ) = FoodItemEntity(
        rowId = 0,
        id = id,
        name = name,
        barcode = barcode,
        nutriKcal = 350.0,
        nutriProteinG = 12.0,
        nutriFatG = 6.0,
        nutriCarbsG = 60.0,
        nutriFiberG = 8.0,
        servingLabel = null,
        servingGramsEquivalent = null,
        isUserCreated = isUserCreated,
        cachedStatus = cachedStatus,
        origName = name,
        origBarcode = barcode,
        origNutriKcal = 350.0,
        origNutriProteinG = 12.0,
        origNutriFatG = 6.0,
        origNutriCarbsG = 60.0,
        origNutriFiberG = 8.0,
        origServingLabel = null,
        origServingGramsEquivalent = null,
        origIsUserCreated = isUserCreated,
        isFavorite = isFavorite,
        lastUsedAt = lastUsedAt,
    )

    fun recipe(
        id: String = RECIPE_ID,
        name: String = "Porridge",
        portions: Int = 4,
        finalWeightGrams: Double? = 500.0,
    ) = RecipeEntity(
        id = id,
        name = name,
        finalWeightGrams = finalWeightGrams,
        portions = portions,
        category = "Frühstück",
        lastModifiedAt = 1749000000000L,
        isFavorite = 0,
        lastUsedAt = null,
    )

    fun ingredient(
        recipeId: String = RECIPE_ID,
        foodId: String? = REFERENCED_FOOD,
        grams: Double? = 80.0,
        servingCount: Double? = null,
        recipeSnapshotJson: String? = null,
    ) = RecipeIngredientEntity(
        rowId = 0,
        recipeId = recipeId,
        foodItemId = foodId,
        rawWeightGrams = grams,
        servingCount = servingCount,
        recipeSnapshotJson = recipeSnapshotJson,
    )

    fun weightLog(
        date: String = DATE,
        kilograms: Double = 80.5,
    ) = WeightLogEntity(date = date, kilograms = kilograms)

    /** Builds the entry against the real seed row so snapshot and stored food match. */
    fun foodEntry(
        foodEntity: FoodItemEntity = food(OWN_FOOD),
        id: String = "diary-food",
        date: String = DATE,
        amountGrams: Double? = 150.0,
        portionCount: Double? = null,
    ): DiaryEntryEntity {
        val snapshot = foodSnapshot(foodEntity)
        return DiaryEntryEntity(
            id = id,
            slot = "BREAKFAST",
            date = date,
            foodItemId = foodEntity.id,
            recipeId = null,
            amountGrams = amountGrams,
            portionCount = portionCount,
            recipeOverridden = false,
            foodSnapshot = snapshot,
            recipeSnapshot = null,
        )
    }

    fun recipeEntry(
        recipeId: String = RECIPE_ID,
        snapshotFood: FoodItemEntity = food(OWN_FOOD),
        id: String = "diary-recipe",
        date: String = DATE,
        portionCount: Double? = 1.0,
    ): DiaryEntryEntity {
        val snapshot = recipeSnapshot(recipeId, snapshotFood)
        return DiaryEntryEntity(
            id = id,
            slot = "DINNER",
            date = date,
            foodItemId = null,
            recipeId = recipeId,
            amountGrams = null,
            portionCount = portionCount,
            recipeOverridden = false,
            foodSnapshot = null,
            recipeSnapshot = snapshot,
        )
    }

    /** The canonical A5 seed: 4 foods, 1 recipe + ingredient, 2 diary entries, 1 weight log. */
    fun seedRows(): BackupRows =
        BackupRows(
            foods =
                listOf(
                    food(OWN_FOOD, name = "Eigenes Brot", isUserCreated = true),
                    food(FAVORITE_FOOD, name = "Lieblings Müsli", isFavorite = true),
                    food(CACHED_FOOD, name = "Couch Chips"),
                    food(REFERENCED_FOOD, name = "Milch"),
                ),
            recipes = listOf(recipe()),
            ingredients = listOf(ingredient()),
            diaryEntries = listOf(foodEntry(), recipeEntry()),
            weightLogs = listOf(weightLog()),
        )

    /** The same seed in archive form — handy for a document that must survive a decode. */
    fun seedContent(): BackupContentDto {
        val rows = seedRows()
        return BackupContentDto(
            foodItems = rows.foods.map { it.toBackup() },
            recipes = rows.recipes.map { it.toBackup() },
            diaryEntries = rows.diaryEntries.map { it.toBackup() },
            weightLogs = rows.weightLogs.map { it.toBackup() },
        )
    }

    fun foodSnapshot(food: FoodItemEntity): String = JsonConverters.encodeFoodItem(food.toFoodItem())

    fun recipeSnapshot(
        recipeId: String,
        food: FoodItemEntity,
    ): String {
        val recipe =
            Recipe(
                id = RecipeId(recipeId),
                name = "Porridge",
                ingredients =
                    listOf(
                        RecipeIngredient(
                            food = food.toFoodItem(),
                            recipeSnapshot = null,
                            rawWeight = RawWeight(80.0),
                            servingCount = null,
                        ),
                    ),
                finalWeight = null,
                portions = 4,
            )
        return JsonConverters.encodeRecipe(recipe)
    }

    /** A ready-to-encode document with a matching checksum. */
    fun document(content: BackupContentDto): BackupDocumentDto =
        BackupDocumentDto(
            format = BackupCodec.FORMAT_ID,
            formatVersion = BackupCodec.FORMAT_VERSION,
            appVersionName = "1.0",
            appVersionCode = 1,
            exportedAtEpochMillis = 1750000000000L,
            dbSchemaVersion = 9,
            checksum = BackupCodec.checksum(content),
            content = content,
        )
}
