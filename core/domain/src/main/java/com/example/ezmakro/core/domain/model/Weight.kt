package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Weight of an ingredient in its RAW (uncooked) state.
 *
 * Per ADR-003, [RawWeight] and [FinalWeight] are distinct inline value classes.
 * The Kotlin type system makes it impossible to pass a raw weight where a final
 * weight (or vice versa) is expected, eliminating a whole class of nutrition
 * calculation bugs at compile time.
 */
@JvmInline
@Serializable
value class RawWeight(
    val grams: Double,
) {
    init {
        require(grams >= 0.0) { "RawWeight must be non-negative, was $grams" }
    }

    operator fun plus(other: RawWeight): RawWeight = RawWeight(grams + other.grams)
}

/**
 * Measured weight of a prepared dish AFTER cooking (e.g. after water evaporated).
 *
 * Optional in the domain: a recipe may have no final weight, in which case
 * "nutrition per 100 g" cannot be derived and must not be computed from the
 * sum of raw weights (ADR-003).
 */
@JvmInline
@Serializable
value class FinalWeight(
    val grams: Double,
) {
    init {
        require(grams > 0.0) { "FinalWeight must be positive, was $grams" }
    }
}
