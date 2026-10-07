package com.example.ezmakro.core.database.dao

/**
 * Narrow projection of a food row for the import's target-DB context: the id
 * plus the two columns that make up its serving unit. The stage-4 recipe
 * validation needs exactly this (W3) — `SELECT id` alone cannot answer
 * "does this food have a serving unit", and loading full entities would
 * defeat the point of a projection.
 */
data class FoodServingUnitRow(
    val id: String,
    val servingLabel: String?,
    val servingGramsEquivalent: Double?,
)
