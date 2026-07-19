package com.example.ezmakro.core.domain.calculation

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.NutritionDifference
import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.model.NutritionValues

/**
 * Aggregated, goal-aware nutrition for a single day, computed from the consumed
 * diary entries and the active [NutritionGoal].
 *
 * All arithmetic lives here in the domain layer (ADR-001) so the presentation
 * layer only formats — never calculates — and the math is unit-testable in
 * pure Kotlin.
 *
 * @property consumed What the user actually logged.
 * @property goal The active daily target.
 * @property remaining [goal] minus [consumed]; may be negative when over target.
 */
data class DayNutrition(
    val consumed: NutritionValues,
    val goal: NutritionGoal,
    val remaining: NutritionDifference,
)

/**
 * Combine observed diary entries with the active goal into a [DayNutrition].
 * Consumed nutrition is the sum of every entry's [DiaryEntry.consumedNutrition];
 * remaining is [goal] − consumed (never clamped — going over target is a real
 * signal the dashboard surfaces in red).
 */
fun dayNutrition(
    entries: List<DiaryEntry>,
    goal: NutritionGoal,
): DayNutrition {
    val consumed =
        entries.fold(NutritionValues.ZERO) { acc, entry -> acc + entry.consumedNutrition() }
    val remaining =
        NutritionDifference(
            kcal = goal.kcal - consumed.kcal,
            proteinG = goal.proteinG - consumed.proteinG,
            fatG = goal.fatG - consumed.fatG,
            carbsG = goal.carbsG - consumed.carbsG,
            fiberG = goal.fiberG - consumed.fiberG,
        )
    return DayNutrition(consumed = consumed, goal = goal, remaining = remaining)
}

/**
 * Progress of [consumed] towards [goal] per macro, clamped to 0..1 so UI
 * progress indicators never overflow. A zero goal yields 0.0 (avoids div-by-
 * zero); consuming nothing yields 0.0.
 */
fun DayNutrition.progress(): Map<Macro, Float> =
    mapOf(
        Macro.KCAL to ratio(consumed.kcal, goal.kcal),
        Macro.PROTEIN to ratio(consumed.proteinG, goal.proteinG),
        Macro.FAT to ratio(consumed.fatG, goal.fatG),
        Macro.CARBS to ratio(consumed.carbsG, goal.carbsG),
        Macro.FIBER to ratio(consumed.fiberG, goal.fiberG),
    )

private fun ratio(
    consumed: Double,
    goal: Double,
): Float = if (goal <= 0.0) 0f else (consumed / goal).toFloat().coerceIn(0f, 1f)

/**
 * The five tracked macros, in display order.
 */
enum class Macro {
    KCAL,
    PROTEIN,
    FAT,
    CARBS,
    FIBER,
}
