package com.example.ezmakro.core.presentation.diary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.presentation.R
import com.example.ezmakro.core.presentation.util.gramsPerPortionLabel

/**
 * Hint line showing how many grams one portion weighs (e.g. "Pro Portion (150 g)",
 * estimated recipes prefixed with "~"). Renders nothing when the weight is unknown.
 */
@Composable
fun GramsPerPortionHint(
    gramsPerPortion: Double?,
    isEstimated: Boolean,
) {
    if (gramsPerPortion == null || gramsPerPortion <= 0.0) return
    Text(
        text =
            stringResource(
                R.string.ingredient_serving_amount_hint,
                gramsPerPortionLabel(gramsPerPortion, isEstimated),
            ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
