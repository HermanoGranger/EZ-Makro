package com.example.ezmakro.core.domain.util

fun String?.parsePositiveInt(): Int? =
    this
        ?.trim()
        ?.ifBlank { null }
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

fun String?.parsePositiveDouble(): Double? =
    this
        ?.trim()
        ?.ifBlank { null }
        ?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }

fun String?.parseNonNegativeDouble(): Double? =
    this
        ?.trim()
        ?.ifBlank { null }
        ?.replace(',', '.')
        ?.toDoubleOrNull()
        ?.takeIf { it >= 0.0 }
