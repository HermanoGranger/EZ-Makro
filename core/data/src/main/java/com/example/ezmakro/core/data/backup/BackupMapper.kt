package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.database.entity.WeightLogEntity

/**
 * Entity ↔ DTO mapping of the backup format, next to (never instead of) the
 * existing entity → domain mappers.
 *
 * Rules:
 * - `rowId` is **not** part of the format (A13): entities built for an import
 *   get `rowId = 0` so Room assigns fresh surrogate keys.
 * - `recipes.is_favorite` is an `Int` column, the DTO a `Boolean` — converted
 *   `1`/`0` on write and `!= 0` on read.
 * - Snapshots (`food_snapshot`, `recipe_snapshot`, `recipe_snapshot_json`)
 *   stay opaque strings (A8): they are carried over byte-for-byte.
 */

fun FoodItemEntity.toBackup(): BackupFoodItemDto =
    BackupFoodItemDto(
        id = id,
        name = name,
        barcode = barcode,
        nutriKcal = nutriKcal,
        nutriProteinG = nutriProteinG,
        nutriFatG = nutriFatG,
        nutriCarbsG = nutriCarbsG,
        nutriFiberG = nutriFiberG,
        servingLabel = servingLabel,
        servingGramsEquivalent = servingGramsEquivalent,
        isUserCreated = isUserCreated,
        cachedStatus = cachedStatus,
        origName = origName,
        origBarcode = origBarcode,
        origNutriKcal = origNutriKcal,
        origNutriProteinG = origNutriProteinG,
        origNutriFatG = origNutriFatG,
        origNutriCarbsG = origNutriCarbsG,
        origNutriFiberG = origNutriFiberG,
        origServingLabel = origServingLabel,
        origServingGramsEquivalent = origServingGramsEquivalent,
        origIsUserCreated = origIsUserCreated,
        isFavorite = isFavorite,
        lastUsedAt = lastUsedAt,
    )

fun BackupFoodItemDto.toEntity(): FoodItemEntity =
    FoodItemEntity(
        rowId = 0,
        id = id,
        name = name,
        barcode = barcode,
        nutriKcal = nutriKcal,
        nutriProteinG = nutriProteinG,
        nutriFatG = nutriFatG,
        nutriCarbsG = nutriCarbsG,
        nutriFiberG = nutriFiberG,
        servingLabel = servingLabel,
        servingGramsEquivalent = servingGramsEquivalent,
        isUserCreated = isUserCreated,
        cachedStatus = cachedStatus,
        origName = origName,
        origBarcode = origBarcode,
        origNutriKcal = origNutriKcal,
        origNutriProteinG = origNutriProteinG,
        origNutriFatG = origNutriFatG,
        origNutriCarbsG = origNutriCarbsG,
        origNutriFiberG = origNutriFiberG,
        origServingLabel = origServingLabel,
        origServingGramsEquivalent = origServingGramsEquivalent,
        origIsUserCreated = origIsUserCreated,
        isFavorite = isFavorite,
        lastUsedAt = lastUsedAt,
    )

/** Without ingredients — they ride along inside [BackupRecipeDto.ingredients]. */
fun RecipeEntity.toBackup(): BackupRecipeDto =
    BackupRecipeDto(
        id = id,
        name = name,
        finalWeightGrams = finalWeightGrams,
        portions = portions,
        category = category,
        lastModifiedAt = lastModifiedAt,
        isFavorite = isFavorite != 0,
        lastUsedAt = lastUsedAt,
    )

/** `rowId` stays `0` — the surrogate key has no counterpart in the format (A13). */
fun RecipeIngredientEntity.toBackup(): BackupIngredientDto =
    BackupIngredientDto(
        foodItemId = foodItemId,
        rawWeightGrams = rawWeightGrams,
        servingCount = servingCount,
        recipeSnapshotJson = recipeSnapshotJson,
    )

fun BackupRecipeDto.toEntities(): Pair<RecipeEntity, List<RecipeIngredientEntity>> {
    val recipe =
        RecipeEntity(
            id = id,
            name = name,
            finalWeightGrams = finalWeightGrams,
            portions = portions,
            category = category,
            lastModifiedAt = lastModifiedAt,
            isFavorite = if (isFavorite) 1 else 0,
            lastUsedAt = lastUsedAt,
        )
    val ingredientEntities =
        ingredients.map { ingredient ->
            RecipeIngredientEntity(
                rowId = 0,
                recipeId = id,
                foodItemId = ingredient.foodItemId,
                rawWeightGrams = ingredient.rawWeightGrams,
                servingCount = ingredient.servingCount,
                recipeSnapshotJson = ingredient.recipeSnapshotJson,
            )
        }
    return recipe to ingredientEntities
}

fun DiaryEntryEntity.toBackup(): BackupDiaryEntryDto =
    BackupDiaryEntryDto(
        id = id,
        slot = slot,
        date = date,
        foodItemId = foodItemId,
        recipeId = recipeId,
        amountGrams = amountGrams,
        portionCount = portionCount,
        recipeOverridden = recipeOverridden,
        foodSnapshot = foodSnapshot,
        recipeSnapshot = recipeSnapshot,
    )

fun BackupDiaryEntryDto.toEntity(): DiaryEntryEntity =
    DiaryEntryEntity(
        id = id,
        slot = slot,
        date = date,
        foodItemId = foodItemId,
        recipeId = recipeId,
        amountGrams = amountGrams,
        portionCount = portionCount,
        recipeOverridden = recipeOverridden,
        foodSnapshot = foodSnapshot,
        recipeSnapshot = recipeSnapshot,
    )

fun WeightLogEntity.toBackup(): BackupWeightLogDto =
    BackupWeightLogDto(
        date = date,
        kilograms = kilograms,
    )

fun BackupWeightLogDto.toEntity(): WeightLogEntity =
    WeightLogEntity(
        date = date,
        kilograms = kilograms,
    )
