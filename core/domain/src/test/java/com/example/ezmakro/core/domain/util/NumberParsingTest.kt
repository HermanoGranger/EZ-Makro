package com.example.ezmakro.core.domain.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class NumberParsingTest {
    @Test
    fun `parseDecimalFlexible accepts german decimal comma`() {
        assertThat("0,5".parseDecimalFlexible()).isEqualTo(0.5)
        assertThat(",5".parseDecimalFlexible()).isEqualTo(0.5)
        assertThat("1,50".parseDecimalFlexible()).isEqualTo(1.5)
        assertThat("1,5".parseDecimalFlexible()).isEqualTo(1.5)
    }

    @Test
    fun `parseDecimalFlexible keeps dot notation working`() {
        assertThat("1.5".parseDecimalFlexible()).isEqualTo(1.5)
        assertThat("100".parseDecimalFlexible()).isEqualTo(100.0)
    }

    @Test
    fun `parseDecimalFlexible trims surrounding whitespace`() {
        assertThat(" 2,25 ".parseDecimalFlexible()).isEqualTo(2.25)
    }

    @Test
    fun `parseDecimalFlexible rejects multiple decimal separators without thousand heuristic`() {
        assertThat("1,2,3".parseDecimalFlexible()).isNull()
        assertThat("1.2.3".parseDecimalFlexible()).isNull()
        assertThat("1,2.3".parseDecimalFlexible()).isNull()
    }

    @Test
    fun `parseDecimalFlexible returns null for empty blank null and garbage input`() {
        val nullInput: String? = null
        assertThat(nullInput.parseDecimalFlexible()).isNull()
        assertThat("".parseDecimalFlexible()).isNull()
        assertThat("   ".parseDecimalFlexible()).isNull()
        assertThat("-".parseDecimalFlexible()).isNull()
        assertThat("abc".parseDecimalFlexible()).isNull()
    }

    @Test
    fun `parsePositiveDouble accepts comma and still requires a positive value`() {
        assertThat("0,5".parsePositiveDouble()).isEqualTo(0.5)
        assertThat("0".parsePositiveDouble()).isNull()
        assertThat("-1,5".parsePositiveDouble()).isNull()
        assertThat("x".parsePositiveDouble()).isNull()
    }

    @Test
    fun `parseNonNegativeDouble accepts comma and still rejects negative values`() {
        assertThat("0".parseNonNegativeDouble()).isEqualTo(0.0)
        assertThat("1,5".parseNonNegativeDouble()).isEqualTo(1.5)
        assertThat("-1".parseNonNegativeDouble()).isNull()
    }

    @Test
    fun `parsePositiveDecimalInput separates empty valid and invalid with comma support`() {
        assertThat("".parsePositiveDecimalInput()).isEqualTo(NumberInput.Empty)
        assertThat("  ".parsePositiveDecimalInput()).isEqualTo(NumberInput.Empty)
        assertThat("0,5".parsePositiveDecimalInput()).isEqualTo(NumberInput.Valid(0.5))
        assertThat("0".parsePositiveDecimalInput()).isEqualTo(NumberInput.Invalid)
        assertThat("1,2,3".parsePositiveDecimalInput()).isEqualTo(NumberInput.Invalid)
    }
}
