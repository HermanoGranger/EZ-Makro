package com.example.ezmakro.feature.recipes.presentation.editor

internal fun String?.parsePositiveInt(): Int? =
    this
        ?.trim()
        ?.ifBlank { null }
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

internal fun String?.parsePositiveDouble(): Double? =
    this
        ?.trim()
        ?.ifBlank { null }
        ?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }

internal fun String?.parseNonNegativeDouble(): Double? =
    this
        ?.trim()
        ?.ifBlank { null }
        ?.replace(',', '.')
        ?.toDoubleOrNull()
        ?.takeIf { it >= 0.0 }
