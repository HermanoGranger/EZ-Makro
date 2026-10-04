package com.example.ezmakro.feature.food.presentation.navigation

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoodRoutesTest {
    @Test
    fun foodDetail_route_roundTrips() {
        val route = FoodDetail(foodId = "abc123")

        val encoded = Json.encodeToString(route)
        val decoded = Json.decodeFromString<FoodDetail>(encoded)

        assertEquals(route, decoded)
        assertEquals("abc123", decoded.foodId)
    }

    @Test
    fun foodEdit_route_roundTrips_withOptionalFields() {
        val withBarcode = FoodEdit(foodId = null, prefillBarcode = "999")
        val createOnly = FoodEdit()

        assertEquals(withBarcode, Json.decodeFromString<FoodEdit>(Json.encodeToString(withBarcode)))
        assertEquals(createOnly, Json.decodeFromString<FoodEdit>(Json.encodeToString(createOnly)))
        assertEquals("999", Json.decodeFromString<FoodEdit>(Json.encodeToString(withBarcode)).prefillBarcode)
    }

    @Test
    fun objectRoutes_encodeAsUnit() {
        assertEquals("{}", Json.encodeToString(FoodSearch))
    }
}
