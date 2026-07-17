package com.example.ezmakro.core.domain.model

/**
 * A non-gram serving description such as "1 Ei", "1 Scheibe", "1 EL", mapped to
 * a gram equivalent so ingredients can be entered without forcing gram input
 * (scope: Mengenangaben jenseits von Gramm via ServingUnit).
 *
 * [gramsEquivalent] is the weight of one unit of this serving. [label] is the
 * human-readable form shown in the UI (e.g. "1 Ei").
 */
data class ServingUnit(
    val label: String,
    val gramsEquivalent: Double,
) {
    init {
        require(label.isNotBlank()) { "ServingUnit label must not be blank" }
        require(gramsEquivalent > 0.0) { "gramsEquivalent must be positive, was $gramsEquivalent" }
    }

    /**
     * Convert [count] units of this serving into grams.
     */
    fun toGrams(count: Double): Double {
        require(count >= 0.0) { "count must be non-negative, was $count" }
        return count * gramsEquivalent
    }
}
