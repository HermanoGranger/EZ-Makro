package com.example.ezmakro.core.presentation.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats a [Double] for user-facing display: rounds HALF_UP to at most
 * [maxDecimals] places, trims trailing fractional zeros and never uses exponent
 * notation. Whole numbers render without a decimal part (42.0 -> "42").
 */
fun Double.formatValue(maxDecimals: Int = 2): String {
    check(maxDecimals >= 0)
    val scaled = BigDecimal(toString()).setScale(maxDecimals, RoundingMode.HALF_UP)
    return if (maxDecimals == 0) {
        scaled.toPlainString()
    } else {
        scaled.stripTrailingZeros().toPlainString()
    }
}
