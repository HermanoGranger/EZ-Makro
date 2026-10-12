package com.example.ezmakro.feature.diary.presentation.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.ServingUnit
import org.junit.jupiter.api.Test
import java.time.LocalDate

private fun testFood(servingUnit: ServingUnit? = null): FoodItem =
    FoodItem(
        id = FoodId("f1"),
        name = "Brot",
        barcode = null,
        nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
        servingUnit = servingUnit,
        isUserCreated = true,
        cachedStatus = CachedFoodItemStatus.Original,
        originalSnapshot = null,
    )

private fun testRecipe(
    finalWeight: FinalWeight?,
    portions: Int,
    rawGrams: Double = 200.0,
): Recipe =
    Recipe(
        id = RecipeId("r1"),
        name = "Brot",
        ingredients =
            listOf(
                RecipeIngredient(
                    food = testFood(),
                    rawWeight = RawWeight(rawGrams),
                    servingCount = null,
                ),
            ),
        finalWeight = finalWeight,
        portions = portions,
    )

private fun entry(
    food: FoodItem? = null,
    recipe: Recipe? = null,
    amountGrams: Double? = null,
    portionCount: Double? = null,
): DiaryEntry =
    DiaryEntry(
        id = DiaryEntryId("d1"),
        slot = MealSlot.LUNCH,
        date = LocalDate.of(2026, 1, 1),
        food = food,
        recipe = recipe,
        amountGrams = amountGrams,
        portionCount = portionCount,
    )

class DiaryUiTest {
    @Test
    fun `food entry exposes serving grams equivalent as grams per portion`() {
        val ui =
            entry(food = testFood(servingUnit = ServingUnit("1 Scheibe", 45.0)), portionCount = 2.0)
                .toDiaryEntryUi()

        assertThat(ui.gramsPerPortion).isEqualTo(45.0)
        assertThat(ui.gramsPerPortionEstimated).isFalse()
    }

    @Test
    fun `food entry without serving unit has no grams per portion`() {
        val ui = entry(food = testFood(), amountGrams = 100.0).toDiaryEntryUi()

        assertThat(ui.gramsPerPortion).isNull()
        assertThat(ui.gramsPerPortionEstimated).isFalse()
    }

    @Test
    fun `recipe entry with final weight derives exact grams per portion`() {
        val recipe = testRecipe(finalWeight = FinalWeight(250.0), portions = 2, rawGrams = 200.0)
        val ui = entry(recipe = recipe, portionCount = 1.0).toDiaryEntryUi()

        assertThat(ui.gramsPerPortion).isEqualTo(125.0)
        assertThat(ui.gramsPerPortionEstimated).isFalse()
    }

    @Test
    fun `recipe entry without final weight estimates grams per portion from raw total`() {
        val recipe = testRecipe(finalWeight = null, portions = 4, rawGrams = 200.0)
        val ui = entry(recipe = recipe, portionCount = 1.0).toDiaryEntryUi()

        assertThat(ui.gramsPerPortion).isEqualTo(50.0)
        assertThat(ui.gramsPerPortionEstimated).isTrue()
    }
}
