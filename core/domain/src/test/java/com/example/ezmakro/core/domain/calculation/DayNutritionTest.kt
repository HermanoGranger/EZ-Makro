package com.example.ezmakro.core.domain.calculation

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.model.NutritionValues
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DayNutritionTest {
    private val date = LocalDate.of(2026, 7, 19)

    private fun food(id: String): FoodItem =
        FoodItem(
            id = FoodId(id),
            name = "Food $id",
            barcode = null,
            nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
            servingUnit = null,
            isUserCreated = true,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )

    private fun entry(
        id: String,
        foodId: String,
        amountGrams: Double,
    ): DiaryEntry =
        DiaryEntry(
            id = DiaryEntryId(id),
            slot = MealSlot.BREAKFAST,
            date = date,
            food = food(foodId),
            recipe = null,
            amountGrams = amountGrams,
            portionCount = null,
        )

    @Test
    fun `consumed is sum of entry nutrition and remaining is goal minus consumed`() {
        val goal = NutritionGoal(kcal = 2000.0, proteinG = 150.0, fatG = 56.0, carbsG = 225.0, fiberG = 30.0)
        val entries =
            listOf(
                entry("a", "f1", 100.0), // 100 kcal, 10 protein, ...
                entry("b", "f2", 200.0), // 200 kcal, 20 protein, ...
            )

        val day = dayNutrition(entries, goal)

        assertThat(day.consumed.kcal).isEqualTo(300.0)
        assertThat(day.consumed.proteinG).isEqualTo(30.0)
        assertThat(day.remaining.kcal).isEqualTo(1700.0)
        assertThat(day.remaining.proteinG).isEqualTo(120.0)
    }

    @Test
    fun `empty day leaves remaining equal to goal`() {
        val goal = NutritionGoal(kcal = 2000.0, proteinG = 150.0, fatG = 56.0, carbsG = 225.0, fiberG = 30.0)

        val day = dayNutrition(emptyList(), goal)

        assertThat(day.consumed.kcal).isEqualTo(0.0)
        assertThat(day.remaining.kcal).isEqualTo(2000.0)
    }

    @Test
    fun `remaining is negative when over goal`() {
        val goal = NutritionGoal(kcal = 100.0, proteinG = 10.0, fatG = 5.0, carbsG = 20.0, fiberG = 1.0)

        val day = dayNutrition(listOf(entry("a", "f1", 200.0)), goal)

        assertThat(day.remaining.kcal).isEqualTo(-100.0)
    }

    @Test
    fun `progress is clamped and zero when goal is zero`() {
        val goal = NutritionGoal(kcal = 100.0, proteinG = 0.0, fatG = 5.0, carbsG = 20.0, fiberG = 1.0)

        val day = dayNutrition(listOf(entry("a", "f1", 300.0)), goal)

        val progress = day.progress()
        assertThat(progress[Macro.KCAL]!!).isEqualTo(1f)
        assertThat(progress[Macro.PROTEIN]!!).isEqualTo(0f) // zero goal -> 0
        assertThat(progress[Macro.FAT]!!).isEqualTo(1f) // over goal clamped
    }

    @Test
    fun `progress ratio is fractional when partially consumed`() {
        val goal = NutritionGoal(kcal = 200.0, proteinG = 20.0, fatG = 10.0, carbsG = 40.0, fiberG = 2.0)

        val day = dayNutrition(listOf(entry("a", "f1", 100.0)), goal)

        assertThat(day.progress()[Macro.KCAL]!!).isCloseTo(0.5f, 0.0001f)
    }
}
