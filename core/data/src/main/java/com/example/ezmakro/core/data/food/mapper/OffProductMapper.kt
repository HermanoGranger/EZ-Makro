package com.example.ezmakro.core.data.food.mapper

import com.example.ezmakro.core.data.food.dto.OffProductDto
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.util.parseDecimalFlexible

private const val KJ_PER_KCAL = 4.184

/**
 * Map an [OffProductDto] to a domain [FoodItem]. Freshly fetched OFF entries are
 * [CachedFoodItemStatus.Original] and carry their own value as [FoodItem.originalSnapshot]
 * (ADR-004: the untouched snapshot is kept so "reset to original" is possible).
 *
 * Returns `null` when the product lacks the minimum data we need (a usable name).
 */
fun OffProductDto.toFoodItem(): FoodItem? {
    val name = displayName() ?: return null
    val nutrition = nutritments()
    val original =
        FoodItem(
            id = FoodId(stableId(name)),
            name = name,
            barcode = code,
            nutritionPer100g = nutrition,
            servingUnit = servingUnit(),
            isUserCreated = false,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )
    return original.copy(originalSnapshot = original)
}

private fun OffProductDto.displayName(): String? =
    productNameDe?.takeIf { it.isNotBlank() }
        ?: productName?.takeIf { it.isNotBlank() }

private fun OffProductDto.stableId(name: String): String =
    code?.takeIf { it.isNotBlank() } ?: "off:${name.lowercase().trim()}"

private fun OffProductDto.nutritments(): NutritionValues {
    val n = nutriments ?: return NutritionValues.ZERO
    val kcal =
        n.energyKcal100g
            ?: n.energy100gKj?.let { it / KJ_PER_KCAL }
            ?: 0.0
    return NutritionValues(
        kcal = kcal.coerceAtLeast(0.0),
        proteinG = (n.proteins100g ?: 0.0).coerceAtLeast(0.0),
        fatG = (n.fat100g ?: 0.0).coerceAtLeast(0.0),
        carbsG = (n.carbohydrates100g ?: 0.0).coerceAtLeast(0.0),
        fiberG = (n.fiber100g ?: 0.0).coerceAtLeast(0.0),
    )
}

private fun OffProductDto.servingUnit(): ServingUnit? {
    val grams = servingQuantity.parseDecimalFlexible()?.takeIf { it > 0.0 } ?: return null
    val label = servingSize?.takeIf { it.isNotBlank() } ?: "1 Portion"
    return ServingUnit(label = label, gramsEquivalent = grams)
}
