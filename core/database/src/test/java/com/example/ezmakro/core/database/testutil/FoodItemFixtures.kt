package com.example.ezmakro.core.database.testutil

import com.example.ezmakro.core.database.dao.FoodItemDao
import com.example.ezmakro.core.database.entity.FoodItemEntity

/**
 * Test-only convenience bridging [FoodItemEntity] to the scalar-parameter
 * [FoodItemDao.upsert] (Room requires scalar params for the id-keyed upsert).
 */
fun FoodItemDao.upsertFood(entity: FoodItemEntity) {
    upsert(
        id = entity.id,
        name = entity.name,
        barcode = entity.barcode,
        nutriKcal = entity.nutriKcal,
        nutriProteinG = entity.nutriProteinG,
        nutriFatG = entity.nutriFatG,
        nutriCarbsG = entity.nutriCarbsG,
        nutriFiberG = entity.nutriFiberG,
        servingLabel = entity.servingLabel,
        servingGramsEquivalent = entity.servingGramsEquivalent,
        isUserCreated = entity.isUserCreated,
        cachedStatus = entity.cachedStatus,
        origName = entity.origName,
        origBarcode = entity.origBarcode,
        origNutriKcal = entity.origNutriKcal,
        origNutriProteinG = entity.origNutriProteinG,
        origNutriFatG = entity.origNutriFatG,
        origNutriCarbsG = entity.origNutriCarbsG,
        origNutriFiberG = entity.origNutriFiberG,
        origServingLabel = entity.origServingLabel,
        origServingGrams = entity.origServingGramsEquivalent,
        origIsUserCreated = entity.origIsUserCreated,
    )
}

/**
 * Test fixtures for [FoodItemEntity] so DAO tests stay readable.
 */
object FoodItemFixtures {
    fun food(
        id: String = "f1",
        name: String = "Haferflocken",
        barcode: String? = null,
        isUserCreated: Boolean = false,
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
    )
}
