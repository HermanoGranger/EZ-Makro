package com.example.ezmakro.core.domain.model

/**
 * A single body-weight measurement with an optional trend note.
 */
data class WeightLog(
    val id: WeightLogId,
    val date: java.time.LocalDate,
    val kilograms: Double,
) {
    init {
        require(kilograms > 0.0) { "kilograms must be positive, was $kilograms" }
    }
}

/**
 * Configurable daily nutrition targets shown on the dashboard (scope:
 * Kalorien, Makros, Ballaststoffe).
 */
data class NutritionGoal(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val fiberG: Double,
) {
    init {
        require(kcal >= 0.0) { "kcal must be non-negative" }
        require(proteinG >= 0.0) { "proteinG must be non-negative" }
        require(fatG >= 0.0) { "fatG must be non-negative" }
        require(carbsG >= 0.0) { "carbsG must be non-negative" }
        require(fiberG >= 0.0) { "fiberG must be non-negative" }
    }
}
