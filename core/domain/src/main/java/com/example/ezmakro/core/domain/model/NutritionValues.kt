package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Macro and calorie values for a food or dish, always expressed relative to a
 * defined basis (e.g. per 100 g or per 1 portion). The basis is communicated
 * via the surrounding context (a [FoodItem] carries its reference weight /
 * [ServingUnit]); this value object itself is unit-agnostic.
 *
 * All components are non-negative. Fiber is tracked separately from
 * carbohydrates (ADR/scope: Ballaststoffe is its own tracked macro).
 */
@Serializable
data class NutritionValues(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val fiberG: Double,
) {
    init {
        require(kcal >= 0.0) { "kcal must be non-negative, was $kcal" }
        require(proteinG >= 0.0) { "proteinG must be non-negative, was $proteinG" }
        require(fatG >= 0.0) { "fatG must be non-negative, was $fatG" }
        require(carbsG >= 0.0) { "carbsG must be non-negative, was $carbsG" }
        require(fiberG >= 0.0) { "fiberG must be non-negative, was $fiberG" }
    }

    /**
     * Scale every component by [factor]. Used to convert a per-100 g basis to
     * an actual consumed amount or to scale a recipe.
     */
    operator fun times(factor: Double): NutritionValues {
        require(factor >= 0.0) { "factor must be non-negative, was $factor" }
        return NutritionValues(
            kcal = kcal * factor,
            proteinG = proteinG * factor,
            fatG = fatG * factor,
            carbsG = carbsG * factor,
            fiberG = fiberG * factor,
        )
    }

    operator fun plus(other: NutritionValues): NutritionValues =
        NutritionValues(
            kcal = kcal + other.kcal,
            proteinG = proteinG + other.proteinG,
            fatG = fatG + other.fatG,
            carbsG = carbsG + other.carbsG,
            fiberG = fiberG + other.fiberG,
        )

    companion object {
        val ZERO = NutritionValues(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
