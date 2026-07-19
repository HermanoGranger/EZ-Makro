package com.example.ezmakro.feature.dashboard.presentation.model

import com.example.ezmakro.core.domain.calculation.Macro
import com.example.ezmakro.core.domain.calculation.dayNutrition
import com.example.ezmakro.core.domain.calculation.progress
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.model.NutritionValues

/**
 * Pre-formatted, presentation-ready dashboard figures. All numbers are already
 * strings so composables never do math or rounding (see [DiaryEntryUi]).
 */
data class DashboardUi(
    val remainingKcal: String,
    val consumedKcal: String,
    val goalKcal: String,
    val kcalProgress: Float,
    val isOverGoal: Boolean,
    val macros: List<MacroRowUi>,
)

data class MacroRowUi(
    val macro: Macro,
    val label: String,
    val consumed: String,
    val goal: String,
    val progress: Float,
)

/**
 * Build the dashboard UI model from raw domain inputs. [goal] is converted to a
 * [NutritionGoal]; the remaining kcal sign drives [DashboardUi.isOverGoal].
 */
fun buildDashboardUi(
    entries: List<DiaryEntry>,
    goal: NutritionGoal,
    macroLabels: (Macro) -> String,
): DashboardUi {
    val day = dayNutrition(entries, goal)
    val remaining = day.remaining
    val overGoal = remaining.kcal < 0

    return DashboardUi(
        remainingKcal = formatSigned(remaining.kcal),
        consumedKcal = day.consumed.kcal.formatMacro(),
        goalKcal = goal.kcal.formatMacro(),
        kcalProgress = day.progress()[Macro.KCAL] ?: 0f,
        isOverGoal = overGoal,
        macros =
            listOf(
                MacroRowUi(
                    macro = Macro.PROTEIN,
                    label = macroLabels(Macro.PROTEIN),
                    consumed = day.consumed.proteinG.formatMacro(),
                    goal = goal.proteinG.formatMacro(),
                    progress = day.progress()[Macro.PROTEIN] ?: 0f,
                ),
                MacroRowUi(
                    macro = Macro.FAT,
                    label = macroLabels(Macro.FAT),
                    consumed = day.consumed.fatG.formatMacro(),
                    goal = goal.fatG.formatMacro(),
                    progress = day.progress()[Macro.FAT] ?: 0f,
                ),
                MacroRowUi(
                    macro = Macro.CARBS,
                    label = macroLabels(Macro.CARBS),
                    consumed = day.consumed.carbsG.formatMacro(),
                    goal = goal.carbsG.formatMacro(),
                    progress = day.progress()[Macro.CARBS] ?: 0f,
                ),
                MacroRowUi(
                    macro = Macro.FIBER,
                    label = macroLabels(Macro.FIBER),
                    consumed = day.consumed.fiberG.formatMacro(),
                    goal = goal.fiberG.formatMacro(),
                    progress = day.progress()[Macro.FIBER] ?: 0f,
                ),
            ),
    )
}

internal const val MACRO_DECIMAL_PLACES = 10.0

internal fun Double.formatMacro(): String {
    val rounded = Math.round(this * MACRO_DECIMAL_PLACES) / MACRO_DECIMAL_PLACES
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** Format remaining kcal with an explicit leading "+" when still budget remains. */
private fun formatSigned(value: Double): String {
    val base = value.formatMacro()
    return if (value < 0) base else "+$base"
}

fun NutritionValues.toConsumedLabels(): String = kcal.formatMacro()
