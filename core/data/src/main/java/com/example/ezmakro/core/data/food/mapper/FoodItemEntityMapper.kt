package com.example.ezmakro.core.data.food.mapper

import com.example.ezmakro.core.database.dao.FoodItemDao
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.ServingUnit

/** Wire form of [CachedFoodItemStatus] used in the `cached_status` column. */
internal fun CachedFoodItemStatus.toDbString(): String =
    when (this) {
        CachedFoodItemStatus.Original -> "ORIGINAL"
        CachedFoodItemStatus.UserModified -> "USER_MODIFIED"
        CachedFoodItemStatus.UpdateAvailable -> "UPDATE_AVAILABLE"
    }

internal fun String.toCachedStatus(): CachedFoodItemStatus =
    when (this) {
        "USER_MODIFIED" -> CachedFoodItemStatus.UserModified
        "UPDATE_AVAILABLE" -> CachedFoodItemStatus.UpdateAvailable
        else -> CachedFoodItemStatus.Original
    }

/**
 * Rebuild the domain [FoodItem] from its persisted entity, reconstructing the
 * ADR-004 [FoodItem.originalSnapshot] from the `orig_*` columns when present.
 */
fun FoodItemEntity.toFoodItem(): FoodItem {
    val snapshotName = origName
    val snapshot =
        if (snapshotName != null) {
            FoodItem(
                id = FoodId(id),
                name = snapshotName,
                barcode = origBarcode,
                nutritionPer100g =
                    NutritionValues(
                        kcal = origNutriKcal ?: 0.0,
                        proteinG = origNutriProteinG ?: 0.0,
                        fatG = origNutriFatG ?: 0.0,
                        carbsG = origNutriCarbsG ?: 0.0,
                        fiberG = origNutriFiberG ?: 0.0,
                    ),
                servingUnit = origServingUnit(),
                isUserCreated = origIsUserCreated ?: false,
                cachedStatus = CachedFoodItemStatus.Original,
                originalSnapshot = null,
            )
        } else {
            null
        }
    return FoodItem(
        id = FoodId(id),
        name = name,
        barcode = barcode,
        nutritionPer100g =
            NutritionValues(
                kcal = nutriKcal,
                proteinG = nutriProteinG,
                fatG = nutriFatG,
                carbsG = nutriCarbsG,
                fiberG = nutriFiberG,
            ),
        servingUnit = servingUnit(),
        isUserCreated = isUserCreated,
        cachedStatus = cachedStatus.toCachedStatus(),
        originalSnapshot = snapshot,
    )
}

/**
 * Persist a domain [FoodItem] via the DAO's flat-argument upsert, flattening the
 * ADR-004 [FoodItem.originalSnapshot] into the `orig_*` columns.
 */
@Suppress("LongMethod")
fun FoodItemDao.upsertFood(item: FoodItem) {
    val snapshot = item.originalSnapshot
    upsert(
        id = item.id.value,
        name = item.name,
        barcode = item.barcode,
        nutriKcal = item.nutritionPer100g.kcal,
        nutriProteinG = item.nutritionPer100g.proteinG,
        nutriFatG = item.nutritionPer100g.fatG,
        nutriCarbsG = item.nutritionPer100g.carbsG,
        nutriFiberG = item.nutritionPer100g.fiberG,
        servingLabel = item.servingUnit?.label,
        servingGramsEquivalent = item.servingUnit?.gramsEquivalent,
        isUserCreated = item.isUserCreated,
        cachedStatus = item.cachedStatus.toDbString(),
        origName = snapshot?.name,
        origBarcode = snapshot?.barcode,
        origNutriKcal = snapshot?.nutritionPer100g?.kcal,
        origNutriProteinG = snapshot?.nutritionPer100g?.proteinG,
        origNutriFatG = snapshot?.nutritionPer100g?.fatG,
        origNutriCarbsG = snapshot?.nutritionPer100g?.carbsG,
        origNutriFiberG = snapshot?.nutritionPer100g?.fiberG,
        origServingLabel = snapshot?.servingUnit?.label,
        origServingGrams = snapshot?.servingUnit?.gramsEquivalent,
        origIsUserCreated = snapshot?.isUserCreated,
    )
}

private fun FoodItemEntity.servingUnit(): ServingUnit? {
    val grams = servingGramsEquivalent ?: return null
    val label = servingLabel ?: return null
    return ServingUnit(label = label, gramsEquivalent = grams)
}

private fun FoodItemEntity.origServingUnit(): ServingUnit? {
    val grams = origServingGramsEquivalent ?: return null
    val label = origServingLabel ?: return null
    return ServingUnit(label = label, gramsEquivalent = grams)
}
