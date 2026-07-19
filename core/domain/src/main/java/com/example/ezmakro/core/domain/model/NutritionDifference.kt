package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Difference between two [NutritionValues], e.g. goal − consumed. Unlike
 * [NutritionValues], components may be NEGATIVE: going over a daily target is a
 * real, meaningful signal the dashboard surfaces (e.g. "−500 kcal remaining").
 * Keeping this separate from [NutritionValues] preserves the invariant that
 * logged/goal nutrition is never negative.
 */
@Serializable
data class NutritionDifference(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val fiberG: Double,
) {
    operator fun plus(other: NutritionDifference): NutritionDifference =
        NutritionDifference(
            kcal = kcal + other.kcal,
            proteinG = proteinG + other.proteinG,
            fatG = fatG + other.fatG,
            carbsG = carbsG + other.carbsG,
            fiberG = fiberG + other.fiberG,
        )

    companion object {
        val ZERO = NutritionDifference(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
