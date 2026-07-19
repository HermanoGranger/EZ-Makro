package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Aggregate Root: a food item tracked by the app.
 *
 * Two flavors:
 * - user-created foods ([isUserCreated] = true), editable freely.
 * - cached OpenFoodFacts entries, carrying a [CachedFoodItemStatus] and an
 *   optional [originalSnapshot] of the untouched OFF data so "reset to original"
 *   is always possible (ADR-004).
 *
 * [nutritionPer100g] is the reference nutrition expressed per 100 g. [servingUnit]
 * is optional: when present, the food can also be logged by count (e.g. "1 Ei").
 *
 * [isFavorite] marks the food for the favorites section in the food picker.
 * [lastUsedAt] is the epoch-milli timestamp of the last time the food was used;
 * it drives the "recently used" ordering and is `null` until first use (wired in
 * Phase 7 with the meal log).
 */
@Serializable
data class FoodItem(
    val id: FoodId,
    val name: String,
    val barcode: String?,
    val nutritionPer100g: NutritionValues,
    val servingUnit: ServingUnit?,
    val isUserCreated: Boolean,
    val cachedStatus: CachedFoodItemStatus,
    val originalSnapshot: FoodItem?,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
) {
    init {
        require(name.isNotBlank()) { "FoodItem name must not be blank" }
        if (!isUserCreated) {
            require(cachedStatus is CachedFoodItemStatus.Original || originalSnapshot != null) {
                "Cached OFF entries that are not Original must keep an originalSnapshot"
            }
        }
        lastUsedAt?.let {
            require(it >= 0L) { "lastUsedAt must be a non-negative epoch-milli timestamp, was $it" }
        }
    }
}
