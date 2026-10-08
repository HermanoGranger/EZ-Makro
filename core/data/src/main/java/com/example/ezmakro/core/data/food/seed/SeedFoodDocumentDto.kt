package com.example.ezmakro.core.data.food.seed

import kotlinx.serialization.Serializable

/**
 * Wire format of the bundled BLS seed asset (`assets/seed/food_seed_v1.json`,
 * see `tools/seed/build_seed.py`). One document per app release; a new seed
 * release bumps [seedVersion] so [BlsFoodSeeder] re-runs exactly once.
 *
 * Every nutrient is per 100 g, `null` = BLS value was not vegan-safe/available.
 */
@Serializable
data class SeedFoodDocumentDto(
    val seedVersion: Int,
    val source: SeedSourceDto,
    val foods: List<SeedFoodDto>,
)

/** Attribution payload shipped with the seed (Settings „Datenquellen" + about). */
@Serializable
data class SeedSourceDto(
    val name: String,
    val publisher: String,
    val license: String,
    val citation: String,
    val doi: String,
)

@Serializable
data class SeedFoodDto(
    val id: String,
    val name: String,
    val barcode: String? = null,
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val fiberG: Double? = null,
)
