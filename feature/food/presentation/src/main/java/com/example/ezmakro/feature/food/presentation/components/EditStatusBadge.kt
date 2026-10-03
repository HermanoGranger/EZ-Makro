package com.example.ezmakro.feature.food.presentation.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.ezmakro.feature.food.presentation.R
import com.example.ezmakro.feature.food.presentation.model.FoodEditStatusUi

/**
 * ADR-004: makes the edit status of a cached OFF food visible (ADR-004 requires
 * visible status communication). Renders nothing for [FoodEditStatusUi.Original]
 * and [FoodEditStatusUi.None]; shared by the detail screen and the food editor.
 */
@Composable
fun EditStatusBadge(status: FoodEditStatusUi) {
    val label =
        when (status) {
            FoodEditStatusUi.UserModified -> stringResource(R.string.food_badge_user_modified)
            FoodEditStatusUi.UpdateAvailable -> stringResource(R.string.food_badge_update_available)
            else -> null
        }
    if (label != null) {
        AssistChip(onClick = {}, label = { Text(label) })
    }
}
