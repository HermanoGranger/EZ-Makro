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

/**
 * Ergebnis einer Zahleneingabe aus einem Textfeld.
 *
 * [Empty] und [Invalid] müssen unterscheidbar sein: ein leeres Feld ist für
 * optionale Werte (z. B. Endgewicht) gültig, ein nicht parsebares oder nicht
 * positives nie. Ohne diese Trennung würden Zwischenwerte wie `0` oder ein
 * halb getipptes `-` beim Speichern eine `require`-Verletzung in den
 * Domain-Modellen auslösen.
 */
sealed interface NumberInput {
    /** Feld ist leer — für optionale Werte erlaubt. */
    data object Empty : NumberInput

    /** Feld enthält eine gültige Zahl > 0. */
    data class Valid(
        val value: Double,
    ) : NumberInput

    /** Feld enthält etwas anderes als eine Zahl > 0. */
    data object Invalid : NumberInput
}

/** Gültiger Zahlenwert oder `null` bei leerem bzw. ungültigem Feld. */
val NumberInput.valueOrNull: Double?
    get() = (this as? NumberInput.Valid)?.value

/** `true`, wenn das Feld leer oder eine gültige Zahl enthält. */
val NumberInput.isAccepted: Boolean
    get() = this !is NumberInput.Invalid

/**
 * Portionen-Feld: muss eine ganze Zahl > 0 sein — dieselbe Strenge wie im
 * Rezept-Editor (`parsePositiveInt`). Die Auswertung entscheidet selbst, ob ein
 * leeres Feld zählt.
 */
fun String?.parsePortionsInput(): NumberInput =
    when {
        this == null || trim().isBlank() -> NumberInput.Empty
        else ->
            this
                .trim()
                .toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { NumberInput.Valid(it.toDouble()) }
                ?: NumberInput.Invalid
    }

/** Optionales Dezimalfeld (Endgewicht, Menge): Zahl > 0, Dezimalkomma erlaubt. */
fun String?.parsePositiveDecimalInput(): NumberInput =
    when {
        this == null || trim().isBlank() -> NumberInput.Empty
        else ->
            this
                .parseNonNegativeDouble()
                ?.takeIf { it > 0.0 }
                ?.let { NumberInput.Valid(it) }
                ?: NumberInput.Invalid
    }
