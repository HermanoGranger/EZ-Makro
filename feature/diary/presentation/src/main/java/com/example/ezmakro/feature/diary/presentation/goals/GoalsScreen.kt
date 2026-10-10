package com.example.ezmakro.feature.diary.presentation.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.diary.presentation.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun GoalsRoot(
    onNavigateBack: () -> Unit,
    viewModel: GoalsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            GoalsEvent.NavigateBack -> onNavigateBack()
        }
    }

    GoalsScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    state: GoalsState,
    onAction: (GoalsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(GoalsAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        GoalsForm(state = state, onAction = onAction, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun GoalsForm(
    state: GoalsState,
    onAction: (GoalsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NumberField(state.kcal, R.string.goals_kcal) { onAction(GoalsAction.OnKcalChange(it)) }
        NumberField(state.protein, R.string.goals_protein) { onAction(GoalsAction.OnProteinChange(it)) }
        NumberField(state.fat, R.string.goals_fat) { onAction(GoalsAction.OnFatChange(it)) }
        NumberField(state.carbs, R.string.goals_carbs) { onAction(GoalsAction.OnCarbsChange(it)) }
        NumberField(state.fiber, R.string.goals_fiber) { onAction(GoalsAction.OnFiberChange(it)) }

        state.numberError?.let { Text(it.asString()) }
        state.saveError?.let { Text(it.asString()) }

        Button(
            onClick = { onAction(GoalsAction.OnSaveClick) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.goals_save)) }

        OutlinedButton(
            onClick = { onAction(GoalsAction.OnResetToDefaultClick) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.goals_reset_default)) }
    }
}

@Composable
private fun NumberField(
    value: String,
    labelRes: Int,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
