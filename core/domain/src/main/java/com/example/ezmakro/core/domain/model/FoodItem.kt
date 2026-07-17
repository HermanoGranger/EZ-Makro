package com.example.ezmakro.core.domain.model

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
 */
data class FoodItem(
    val id: FoodId,
    val name: String,
    val barcode: String?,
    val nutritionPer100g: NutritionValues,
    val servingUnit: ServingUnit?,
    val isUserCreated: Boolean,
    val cachedStatus: CachedFoodItemStatus,
    val originalSnapshot: FoodItem?,
) {
    init {
        require(name.isNotBlank()) { "FoodItem name must not be blank" }
        if (!isUserCreated) {
            require(cachedStatus is CachedFoodItemStatus.Original || originalSnapshot != null) {
                "Cached OFF entries that are not Original must keep an originalSnapshot"
            }
        }
    }
}
