package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Branded ID for any [FoodItem] (user-created or cached OpenFoodFacts entry).
 * Prevents accidental mixing with recipe IDs or raw strings at compile time.
 */
@JvmInline
@Serializable
value class FoodId(
    val value: String,
)

/**
 * Branded ID for a [Recipe].
 */
@JvmInline
@Serializable
value class RecipeId(
    val value: String,
)

/**
 * Branded ID for a [DiaryEntry].
 */
@JvmInline
@Serializable
value class DiaryEntryId(
    val value: String,
)

/**
 * Branded ID for a [WeightLog] entry.
 */
@JvmInline
@Serializable
value class WeightLogId(
    val value: String,
)
