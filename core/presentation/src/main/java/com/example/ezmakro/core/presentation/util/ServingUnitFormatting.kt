package com.example.ezmakro.core.presentation.util

import com.example.ezmakro.core.domain.model.ServingUnit

/**
 * Human-readable size of one serving for "Pro Portion (…)". Prefers an amount
 * already present in the label: OFF provides serving sizes like "100 ml"
 * directly, user-created servings build labels like "1 Portion (150 g)". For a
 * bare generic label such as "1 Portion" the gram equivalent is used instead.
 */
fun ServingUnit.servingAmountLabel(): String {
    val amount =
        AMOUNT_IN_PARENS
            .find(label)
            ?.groupValues
            ?.get(1)
            ?.trim()
    if (amount != null) return amount
    if (label.equals("1 Portion", ignoreCase = true)) return "${gramsEquivalent.formatValue()} g"
    return label
}

private val AMOUNT_IN_PARENS = Regex("\\(([^)]+)\\)")
