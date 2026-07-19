package com.example.ezmakro.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
 * [centerText] (e.g. remaining kcal).
 */
@Composable
fun CalorieRing(
    centerText: String,
    caption: String,
    progress: Float,
    modifier: Modifier = Modifier,
    isOverGoal: Boolean = false,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val color =
        if (isOverGoal) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val stroke = 14.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = color.copy(alpha = 0.15f),
                radius = radius,
                center = center,
                style = Stroke(width = stroke),
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
                        width = stroke,
                        cap = StrokeCap.Round,
                    ),
            )
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
