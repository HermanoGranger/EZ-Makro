package com.example.ezmakro.feature.food.presentation.model

import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.ServingUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoodUiTest {
    private fun item(serving: ServingUnit?): FoodItem =
        FoodItem(
            id = FoodId("1"),
            name = "Test",
            barcode = null,
            nutritionPer100g = NutritionValues(100.0, 1.0, 1.0, 1.0, 1.0),
            servingUnit = serving,
            isUserCreated = false,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )

    @Test
    fun `servingAmount uses the OFF serving size directly`() {
        val ui = item(ServingUnit(label = "100 ml", gramsEquivalent = 100.0)).toFoodUi()

        assertEquals("100 ml", ui.servingAmount)
    }

    @Test
    fun `servingAmount extracts the value from a Portion label`() {
        val ui = item(ServingUnit(label = "1 Portion (150 g)", gramsEquivalent = 150.0)).toFoodUi()

        assertEquals("150 g", ui.servingAmount)
    }

    @Test
    fun `servingAmount falls back to grams for a generic label`() {
        val ui = item(ServingUnit(label = "1 Portion", gramsEquivalent = 150.0)).toFoodUi()

        assertEquals("150 g", ui.servingAmount)
    }

    @Test
    fun `servingAmount is null without a serving unit`() {
        assertEquals(null, item(serving = null).toFoodUi().servingAmount)
    }
}
