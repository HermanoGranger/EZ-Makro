package com.example.ezmakro.feature.diary.presentation.day

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.designsystem.component.CalorieRing
import com.example.ezmakro.feature.diary.presentation.R
import com.example.ezmakro.feature.diary.presentation.model.DiarySummaryUi
import com.example.ezmakro.feature.diary.presentation.model.MacroRowUi
import kotlinx.coroutines.launch

private val RingSectionCollapseHeight = 220.dp
private const val COLLAPSE_CHEVRON_THRESHOLD = 0.5f
private const val COLLAPSE_SCALE_FACTOR = 0.2f

/**
 * Collapses the ring section as the diary list scrolls. Returns 0f while fully
 * expanded and 1f once the ring has scrolled away completely.
 */
internal fun collapseFractionOf(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    collapseDistancePx: Float,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (collapseDistancePx <= 0f) return 0f
    return (firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
}

/**
 * Collapsible day summary header above the diary's lazy list. Expanded it shows
 * the [CalorieRing] plus one progress bar per macro; collapsed it shrinks to the
 * macro bars only. The user can collapse it by tapping (also on short, non-
 * scrollable days) and expand it again via the chevron, which scrolls the list
 * back to the top. The state is purely visual and intentionally not persisted.
 */
@Composable
fun CollapsingSummaryHeader(
    summary: DiarySummaryUi,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    var collapsedByUser by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val collapseDistancePx = with(density) { RingSectionCollapseHeight.toPx() }

    val scrollFraction by remember(collapseDistancePx) {
        derivedStateOf {
            collapseFractionOf(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                collapseDistancePx = collapseDistancePx,
            )
        }
    }

    val targetFraction = if (collapsedByUser) 1f else scrollFraction
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 250),
        label = "summaryHeaderCollapse",
    )

    val scope = rememberCoroutineScope()
    val onToggleCollapse: () -> Unit = {
        if (collapsedByUser || scrollFraction > 0f) {
            collapsedByUser = false
            scope.launch { listState.animateScrollToItem(0) }
        } else {
            collapsedByUser = true
        }
    }

    CollapsingSummaryHeaderContent(
        summary = summary,
        fraction = fraction,
        onToggleCollapse = onToggleCollapse,
        modifier = modifier,
    )
}

@Composable
fun CollapsingSummaryHeaderContent(
    summary: DiarySummaryUi,
    fraction: Float,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clickable(onClick = onToggleCollapse)
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onToggleCollapse) {
                if (fraction < COLLAPSE_CHEVRON_THRESHOLD) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.diary_collapse_header),
                    )
                } else {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.diary_expand_header),
                    )
                }
            }
        }

        CollapsingRingSection(summary = summary, fraction = fraction)

        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            summary.macros.forEach { macro ->
                SummaryMacroRow(macro = macro, fraction = fraction)
            }
        }
    }
}

@Composable
private fun CollapsingRingSection(
    summary: DiarySummaryUi,
    fraction: Float,
) {
    val density = LocalDensity.current
    var ringHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier =
            Modifier
                .height(with(density) { ringHeightPx * (1f - fraction) }.dp.coerceAtLeast(0.dp))
                .clipToBounds(),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .wrapContentHeight(unbounded = true)
                    .onSizeChanged { ringHeightPx = it.height }
                    .graphicsLayer {
                        alpha = 1f - fraction
                        val scale = 1f - COLLAPSE_SCALE_FACTOR * fraction
                        scaleX = scale
                        scaleY = scale
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CalorieRing(
                centerText = summary.remainingKcal,
                caption = stringResource(R.string.diary_summary_remaining_kcal),
                progress = summary.kcalProgress,
                isOverGoal = summary.isOverGoal,
            )
            Text(
                text =
                    stringResource(R.string.diary_summary_consumed_kcal) +
                        " ${summary.consumedKcal} / ${summary.goalKcal} kcal",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SummaryMacroRow(
    macro: MacroRowUi,
    fraction: Float,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = (6.dp * (1f - fraction)) + (2.dp * fraction)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = macro.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${macro.consumed} / ${macro.goal} g",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.graphicsLayer { alpha = 1f - fraction },
            )
        }
        LinearProgressIndicator(
            progress = { macro.progress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height((4.dp * (1f - fraction)) + (2.dp * fraction)),
        )
    }
}
