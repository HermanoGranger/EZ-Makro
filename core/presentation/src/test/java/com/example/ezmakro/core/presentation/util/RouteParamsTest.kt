package com.example.ezmakro.core.presentation.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RouteParamsTest {
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

    @Test
    fun `round trips a plain ascii value`() {
        assertEquals("abc", decodeRouteParam(encodeRouteParam("abc")))
    }

    @Test
    fun `round trips a path separator`() {
        assertEquals("a/b", decodeRouteParam(encodeRouteParam("a/b")))
    }

    @Test
    fun `round trips a query separator`() {
        assertEquals("a?b", decodeRouteParam(encodeRouteParam("a?b")))
    }

    @Test
    fun `round trips a space`() {
        assertEquals("a b", decodeRouteParam(encodeRouteParam("a b")))
    }

    @Test
    fun `round trips umlauts`() {
        val value = "Müsli, Käse & Soße – Brühe"
        assertEquals(value, decodeRouteParam(encodeRouteParam(value)))
    }

    @Test
    fun `round trips a multibyte emoji inside the basic multilingual plane`() {
        val value = "☕ Kaffee ⚽ Emoji"
        assertEquals(value, decodeRouteParam(encodeRouteParam(value)))
    }

    @Test
    fun `does not round trip emoji outside the basic multilingual plane`() {
        // Same limitation as `android.net.Uri.encode`: encoding iterates UTF-16
        // code units, so each half of a surrogate pair becomes a literal `?`.
        // Documented here so a future change does not pass unnoticed.
        assertEquals("?? Rezept", decodeRouteParam(encodeRouteParam("🍎 Rezept")))
    }

    @Test
    fun `round trips the empty string`() {
        assertEquals("", decodeRouteParam(encodeRouteParam("")))
    }
}
