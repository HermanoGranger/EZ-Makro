package com.example.ezmakro.feature.food.presentation.navigation

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test
    fun routeParam_roundTripsIdWithSlashesSpacesQuestionMarkAndUmlauts() {
        val id = "off:Sahne Torte 100% / Müsli? bio"
        assertEquals(id, decodeRouteParam(encodeRouteParam(id)))
    }

    @Test
    fun routeParam_roundTripsLiteralPercentSequence() {
        val id = "off:a%20b"
        assertEquals(id, decodeRouteParam(encodeRouteParam(id)))
    }

    @Test
    fun routeParam_encodingEscapesRouteDelimiters() {
        val encoded = encodeRouteParam("off:a/b c?d&e=f")

        assertFalse(encoded.contains("/"))
        assertFalse(encoded.contains("?"))
        assertFalse(encoded.contains("&"))
        assertFalse(encoded.contains("="))
    }

    @Test
    fun routeParam_encodingMatchesUriSemantics() {
        assertEquals("a%2Fb%20c%3F", encodeRouteParam("a/b c?"))
        assertEquals("M%C3%BCsli", encodeRouteParam("Müsli"))
        assertEquals("off_Foo-Bar.baz~qux", encodeRouteParam("off_Foo-Bar.baz~qux"))
        assertEquals("100%2520", encodeRouteParam("100%20"))
    }
}
