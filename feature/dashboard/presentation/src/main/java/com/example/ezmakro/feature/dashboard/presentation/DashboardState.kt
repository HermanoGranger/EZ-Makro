package com.example.ezmakro.feature.dashboard.presentation

import com.example.ezmakro.feature.dashboard.presentation.model.DashboardUi
import java.time.LocalDate

data class DashboardState(
    val date: LocalDate = LocalDate.now(),
    val dashboard: DashboardUi? = null,
    val isLoading: Boolean = true,
)
