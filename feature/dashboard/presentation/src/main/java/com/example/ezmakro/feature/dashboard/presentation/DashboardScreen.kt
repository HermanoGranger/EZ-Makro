package com.example.ezmakro.feature.dashboard.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.designsystem.component.CalorieRing
import com.example.ezmakro.core.designsystem.component.LoadingIndicator
import com.example.ezmakro.core.designsystem.component.MacroProgressBar
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.dashboard.presentation.model.DashboardUi
import com.example.ezmakro.feature.dashboard.presentation.model.MacroRowUi
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

@Composable
fun DashboardRoot(
    onNavigateToDiary: (String) -> Unit,
    onNavigateToGoals: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DashboardEvent.NavigateToDiary -> onNavigateToDiary(event.date)
            DashboardEvent.NavigateToGoals -> onNavigateToGoals()
        }
    }

    DashboardScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenDiary = { viewModel.onAction(DashboardAction.OnOpenDiaryClick) },
        onConfigureGoals = { viewModel.onAction(DashboardAction.OnConfigureGoalsClick) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    onOpenDiary: () -> Unit,
    onConfigureGoals: () -> Unit,
) {
    Scaffold(
        topBar = { DashboardTopBar(state = state, onAction = onAction, onOpenDiary = onOpenDiary) },
    ) { padding ->
        if (state.isLoading && state.dashboard == null) {
            LoadingIndicator(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val dashboard = state.dashboard ?: return@Scaffold
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            item {
                CalorieSummary(dashboard = dashboard)
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    dashboard.macros.forEach { macro ->
                        MacroRow(macro = macro)
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onOpenDiary,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.dashboard_open_diary)) }
                    OutlinedButton(
                        onClick = onConfigureGoals,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.dashboard_configure_goals)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    onOpenDiary: () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy") }
    TopAppBar(
        title = {
            TextButton(onClick = onOpenDiary) {
                Text(state.date.format(dateFormatter))
            }
        },
        navigationIcon = {
            IconButton(onClick = { onAction(DashboardAction.OnPreviousDay) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.dashboard_previous_day),
                )
            }
        },
        actions = {
            IconButton(onClick = { onAction(DashboardAction.OnNextDay) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.dashboard_next_day),
                )
            }
            TextButton(onClick = { onAction(DashboardAction.OnTodayClick) }) {
                Text(stringResource(R.string.dashboard_today))
            }
        },
    )
}

@Composable
private fun CalorieSummary(dashboard: DashboardUi) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CalorieRing(
            centerText = dashboard.remainingKcal,
            caption = stringResource(R.string.dashboard_remaining_kcal),
            progress = dashboard.kcalProgress,
            isOverGoal = dashboard.isOverGoal,
        )
        Text(
            text =
                stringResource(R.string.dashboard_consumed_kcal) +
                    " ${dashboard.consumedKcal} / ${dashboard.goalKcal} kcal",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MacroRow(macro: MacroRowUi) {
    MacroProgressBar(
        label = macro.label,
        consumed = macro.consumed,
        goal = macro.goal,
        progress = macro.progress,
    )
}
