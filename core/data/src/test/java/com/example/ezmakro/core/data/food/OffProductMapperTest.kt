package com.example.ezmakro.core.data.food

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.example.ezmakro.core.data.food.dto.OffNutrimentsDto
import com.example.ezmakro.core.data.food.dto.OffProductDto
import com.example.ezmakro.core.data.food.mapper.toFoodItem
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import org.junit.jupiter.api.Test

class OffProductMapperTest {
    @Test
    fun `maps core fields and keeps an original snapshot`() {
        val dto =
            OffProductDto(
                code = "12345",
                productName = "Oat Flakes",
                productNameDe = "Haferflocken",
                nutriments =
                    OffNutrimentsDto(
                        energyKcal100g = 350.0,
                        proteins100g = 12.0,
                        fat100g = 6.0,
                        carbohydrates100g = 60.0,
                        fiber100g = 8.0,
                    ),
            )

        val food = dto.toFoodItem()

        assertThat(food).isNotNull()
        requireNotNull(food)
        assertThat(food.name).isEqualTo("Haferflocken")
        assertThat(food.barcode).isEqualTo("12345")
        assertThat(food.id.value).isEqualTo("12345")
        assertThat(food.nutritionPer100g!!.kcal).isEqualTo(350.0)
        assertThat(food.nutritionPer100g!!.fiberG).isEqualTo(8.0)
        assertThat(food.cachedStatus).isEqualTo(CachedFoodItemStatus.Original)
        assertThat(food.originalSnapshot?.name).isEqualTo("Haferflocken")
    }

    @Test
    fun `prefers german name when present`() {
        val dto = OffProductDto(productName = "Milk", productNameDe = "Milch")

        assertThat(dto.toFoodItem()?.name).isEqualTo("Milch")
    }

    @Test
    fun `returns null when there is no usable name`() {
        val dto = OffProductDto(code = "999", productName = null, productNameDe = "  ")

        assertThat(dto.toFoodItem()).isNull()
    }

    @Test
    fun `converts kJ to kcal when kcal is missing`() {
        val dto =
            OffProductDto(
                productName = "Water-ish",
                nutriments = OffNutrimentsDto(energy100gKj = 418.4),
            )

        val food = dto.toFoodItem()
        requireNotNull(food)
        assertThat(food.nutritionPer100g!!.kcal).isCloseTo(100.0, 0.001)
    }

    @Test
    fun `defaults missing macros to zero`() {
        val dto = OffProductDto(productName = "Mystery", nutriments = null)

        val food = dto.toFoodItem()
        requireNotNull(food)
        assertThat(food.nutritionPer100g!!.kcal).isEqualTo(0.0)
        assertThat(food.nutritionPer100g!!.proteinG).isEqualTo(0.0)
    }

    @Test
    fun `maps serving quantity to a serving unit`() {
        val dto =
            OffProductDto(
                productName = "Egg",
                servingQuantity = "50",
                servingSize = "1 Ei (50 g)",
            )

        val food = dto.toFoodItem()
        requireNotNull(food)
        assertThat(food.servingUnit?.gramsEquivalent).isEqualTo(50.0)
        assertThat(food.servingUnit?.label).isEqualTo("1 Ei (50 g)")
    }

    @Test
    fun `no serving unit when quantity is absent`() {
        val dto = OffProductDto(productName = "Flour")

        assertThat(dto.toFoodItem()?.servingUnit).isNull()
    }
}
