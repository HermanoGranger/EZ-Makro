package com.example.ezmakro.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultRingSize = 160.dp
private val DefaultRingStroke = 14.dp

/**
 * A labelled macro progress row used by the dashboard (and reusable by later
 * statistics screens). [progress] must be in 0f..1f; it is clamped defensively
 * so a value computed elsewhere can never overflow the bar.
 */
@Composable
fun MacroProgressBar(
    label: String,
    consumed: String,
    goal: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$consumed / $goal g",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LinearProgressIndicator(
            progress = { clamped },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/**
 * Round calorie gauge. [progress] is 0f..1f and clamped; the center shows
 * [centerText] (e.g. remaining kcal). [size] scales the canvas and the stroke
 * proportionally; [color] defaults to the primary/error logic driven by
 * [isOverGoal] when null.
 */
@Composable
fun CalorieRing(
    centerText: String,
    caption: String,
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = DefaultRingSize,
    color: Color? = null,
    isOverGoal: Boolean = false,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val resolvedColor =
        color
            ?: if (isOverGoal) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
    val stroke = size * (DefaultRingStroke / DefaultRingSize)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val radius = (this.size.minDimension - strokePx) / 2f
            val center = Offset(this.size.width / 2, this.size.height / 2)
            drawCircle(
                color = resolvedColor.copy(alpha = 0.15f),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx),
            )
            drawArc(
                color = resolvedColor,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style =
                    Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                    ),
            )
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.headlineMedium,
            color = resolvedColor,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Compact circular macro gauge used in grids (e.g. the 2×2 diary header).
 * Shows a label above and a small colored donut with the consumed value in
 * the center. [progress] is 0f..1f and clamped defensively.
 */
@Composable
fun MacroMiniRing(
    label: String,
    centerText: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val stroke = size * (DefaultRingStroke / DefaultRingSize)
    Column(
        modifier = modifier.width(size),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val radius = (this.size.minDimension - strokePx) / 2f
            val center = Offset(this.size.width / 2, this.size.height / 2)
            drawCircle(
                color = color.copy(alpha = 0.15f),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style =
                    Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                    ),
            )
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
