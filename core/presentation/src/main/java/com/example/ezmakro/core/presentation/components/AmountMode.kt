package com.example.ezmakro.core.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ezmakro.core.presentation.R

enum class AmountMode {
    GRAMS,
    PORTIONS,
}

@Composable
fun AmountModeSelector(
    mode: AmountMode,
    onModeChange: (AmountMode) -> Unit,
) {
    Text(stringResource(R.string.diary_add_mode_label), modifier = Modifier.padding(top = 8.dp))
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        AmountMode.entries.forEach { currentMode ->
            val label =
                if (currentMode == AmountMode.PORTIONS) {
                    R.string.diary_add_mode_portions
                } else {
                    R.string.diary_add_mode_grams
                }
            val isSelected = currentMode == mode
            val onClick = { onModeChange(currentMode) }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = onClick)
                Text(text = stringResource(label), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
