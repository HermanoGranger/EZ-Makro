package com.example.ezmakro.core.domain.model

import java.time.LocalDate

/**
 * An inclusive date range [from]..[to] used for history queries.
 * Requires [from] <= [to].
 */
data class LocalDateRange(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(!from.isAfter(to)) { "from ($from) must not be after to ($to)" }
    }

    val days: Long get() = from.until(to).days + 1L
}
