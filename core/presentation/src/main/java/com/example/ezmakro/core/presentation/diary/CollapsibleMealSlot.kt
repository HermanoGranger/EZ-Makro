package com.example.ezmakro.core.presentation.diary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.presentation.R

private const val ANIM_DURATION = 300
private const val MACRO_SPACING = 12
private const val CONTENT_PADDING = 16

@Suppress("LongParameterList")
@Composable
fun CollapsibleMealSlot(
    title: String,
    entries: List<DiaryEntryDetailData>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEditEntry: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRequestAdd: () -> Unit,
    summaryKcal: String,
    summaryProtein: String,
    summaryFat: String,
    summaryCarbs: String,
    summaryFiber: String,
    onEditIngredientsEntry: ((String) -> Unit)? = null,
    onEditIngredientsLabel: String = "Zutaten",
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CollapsibleHeader(
            title = title,
            entries = entries,
            isExpanded = isExpanded,
            onToggle = onToggle,
            onRequestAdd = onRequestAdd,
            summaryKcal = summaryKcal,
            summaryProtein = summaryProtein,
            summaryFat = summaryFat,
            summaryCarbs = summaryCarbs,
            summaryFiber = summaryFiber,
        )
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(ANIM_DURATION)),
            exit = shrinkVertically(animationSpec = tween(ANIM_DURATION)),
        ) {
            Column {
                entries.forEach { entry ->
                    DiaryEntryDetail(
                        data = entry,
                        onEdit = { onEditEntry(entry.id) },
                        onDelete = { onRequestDelete(entry.id) },
                        onEditIngredients = onEditIngredientsEntry?.let { fn -> { fn(entry.id) } },
                        onEditIngredientsLabel = onEditIngredientsLabel,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Suppress("LongParameterList")
@Composable
private fun CollapsibleHeader(
    title: String,
    entries: List<DiaryEntryDetailData>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRequestAdd: () -> Unit,
    summaryKcal: String,
    summaryProtein: String,
    summaryFat: String,
    summaryCarbs: String,
    summaryFiber: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = CONTENT_PADDING.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (entries.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(MACRO_SPACING.dp),
                ) {
                    MacroChip("K", summaryKcal)
                    MacroChip("P", summaryProtein)
                    MacroChip("F", summaryFat)
                    MacroChip("KH", summaryCarbs)
                    MacroChip("B", summaryFiber)
                }
            }
            if (entries.isEmpty()) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (entries.isNotEmpty()) {
            Text(
                text = "${entries.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        IconButton(onClick = onRequestAdd) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
        )
    }
}

@Composable
private fun MacroChip(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
