package com.example.ezmakro.core.presentation.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FormattingTest {
    @Test
    fun `rounds half up to two decimals`() {
        assertThat(12.345.formatValue()).isEqualTo("12.35")
        assertThat(2.999.formatValue()).isEqualTo("3")
    }

    @Test
    fun `trims trailing zeros`() {
        assertThat(12.300.formatValue()).isEqualTo("12.3")
        assertThat(12.0.formatValue()).isEqualTo("12")
    }

    @Test
    fun `whole numbers render without decimal part`() {
        assertThat(42.0.formatValue()).isEqualTo("42")
        assertThat(200.0.formatValue()).isEqualTo("200")
    }

    @Test
    fun `maxDecimals zero rounds to whole number`() {
        assertThat(12.5.formatValue(0)).isEqualTo("13")
        assertThat(200.4.formatValue(0)).isEqualTo("200")
    }

    @Test
    fun `rejects negative maxDecimals`() {
        assertThrows<IllegalStateException> { 42.0.formatValue(-1) }
    }
}
