package com.example.ezmakro.feature.diary.presentation.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.designsystem.component.CalorieRing
import com.example.ezmakro.core.designsystem.component.MacroMiniRing
import com.example.ezmakro.core.domain.calculation.Macro
import com.example.ezmakro.feature.diary.presentation.R
import com.example.ezmakro.feature.diary.presentation.model.DiarySummaryUi

/**
 * Day summary header showing the calorie ring plus the four macro rings in a
 * 2×2 grid. Always fully expanded so the diary slots below stay immediately
 * reachable — no scrolling or unfolding required to log entries.
 */
@Composable
fun DiarySummaryHeader(
    summary: DiarySummaryUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CalorieRing(
                    centerText = summary.remainingKcal,
                    caption = stringResource(R.string.diary_summary_remaining_kcal),
                    progress = summary.kcalProgress,
                    isOverGoal = summary.isOverGoal,
                    size = 120.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${summary.consumedKcal} / ${summary.goalKcal} kcal",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    macroMiniRing(summary, Macro.PROTEIN)
                    macroMiniRing(summary, Macro.FAT)
                }
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    macroMiniRing(summary, Macro.CARBS)
                    macroMiniRing(summary, Macro.FIBER)
                }
            }
        }
    }
}

@Composable
private fun macroMiniRing(
    summary: DiarySummaryUi,
    macro: Macro,
) {
    val row = summary.macros.firstOrNull { it.macro == macro }
    MacroMiniRing(
        label = row?.label.orEmpty(),
        centerText = row?.consumed.orEmpty(),
        progress = row?.progress ?: 0f,
        color = MaterialTheme.colorScheme.primary,
    )
}
