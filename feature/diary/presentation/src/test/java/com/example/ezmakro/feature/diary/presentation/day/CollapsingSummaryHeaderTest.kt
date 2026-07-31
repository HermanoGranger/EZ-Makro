package com.example.ezmakro.feature.diary.presentation.day

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class CollapsingSummaryHeaderTest {
    private val collapseDistance = 2000f

    @Test
    fun `expanded at top when list is not scrolled`() {
        assertThat(collapseFractionOf(0, 0, collapseDistance)).isEqualTo(0f)
    }

    @Test
    fun `interpolates while first item is partially scrolled`() {
        assertThat(collapseFractionOf(0, (collapseDistance / 2).toInt(), collapseDistance)).isEqualTo(0.5f)
    }

    @Test
    fun `fully collapsed once first item scrolled past collapse distance`() {
        assertThat(collapseFractionOf(0, collapseDistance.toInt(), collapseDistance)).isEqualTo(1f)
        assertThat(collapseFractionOf(0, (collapseDistance * 2).toInt(), collapseDistance)).isEqualTo(1f)
    }

    @Test
    fun `fully collapsed when first item is not the first visible item`() {
        assertThat(collapseFractionOf(1, 0, collapseDistance)).isEqualTo(1f)
        assertThat(collapseFractionOf(3, 500, collapseDistance)).isEqualTo(1f)
    }

    @Test
    fun `non-positive collapse distance stays expanded`() {
        assertThat(collapseFractionOf(0, 100, 0f)).isEqualTo(0f)
        assertThat(collapseFractionOf(0, 100, -1f)).isEqualTo(0f)
    }
}
