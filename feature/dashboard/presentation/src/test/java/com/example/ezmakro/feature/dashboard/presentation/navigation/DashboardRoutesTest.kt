package com.example.ezmakro.feature.dashboard.presentation.navigation

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardRoutesTest {
    @Test
    fun dashboard_objectRoute_roundTrips() {
        assertEquals("{}", Json.encodeToString(Dashboard))
        assertEquals(Dashboard, Json.decodeFromString<Dashboard>(Json.encodeToString(Dashboard)))
    }
}
