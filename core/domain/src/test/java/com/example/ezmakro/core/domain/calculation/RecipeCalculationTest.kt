package com.example.ezmakro.core.domain.calculation

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private fun food(
    id: String = "f1",
    per100g: NutritionValues? = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
    servingUnit: ServingUnit? = null,
): FoodItem =
    FoodItem(
        id = FoodId(id),
        name = "Food $id",
        barcode = null,
        nutritionPer100g = per100g,
        servingUnit = servingUnit,
        isUserCreated = true,
        cachedStatus = CachedFoodItemStatus.Original,
        originalSnapshot = null,
    )

private fun ingredient(
    food: FoodItem,
    grams: Double,
): RecipeIngredient = RecipeIngredient(food = food, rawWeight = RawWeight(grams), servingCount = null)

class RecipeCalculationTest {
    @Test
    fun `totalNutrition sums raw ingredient nutrition`() {
        val f = food()
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 100.0), ingredient(f, 200.0)),
                finalWeight = null,
                portions = 1,
            )
        val total = recipe.totalNutrition()
        assertEquals(NutritionValues(300.0, 30.0, 15.0, 6.0, 3.0), total)
    }

    @Test
    fun `nutritionPer100g uses finalWeight not raw weight`() {
        val f = food(per100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0))
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 100.0), ingredient(f, 100.0)),
                finalWeight = FinalWeight(250.0),
                portions = 2,
            )
        val per100 = recipe.nutritionPer100g()!!
        // total = 200 kcal over 250 g final => 80 kcal / 100 g
        assertEquals(80.0, per100.kcal, 1e-9)
    }

    @Test
    fun `nutritionPer100g is null when no final weight`() {
        val f = food()
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 100.0)),
                finalWeight = null,
                portions = 1,
            )
        assertNull(recipe.nutritionPer100g())
    }

    @Test
    fun `ingredient via servingCount resolves through ServingUnit`() {
        val egg =
            food(
                id = "egg",
                per100g = NutritionValues(150.0, 13.0, 10.0, 1.0, 0.0),
                servingUnit = ServingUnit("1 Ei", 50.0),
            )
        val ing = RecipeIngredient(food = egg, rawWeight = null, servingCount = 2.0)
        assertEquals(RawWeight(100.0), ing.effectiveRawWeight())
        // 2 eggs = 100 g => 150 kcal
        assertEquals(NutritionValues(150.0, 13.0, 10.0, 1.0, 0.0), ing.nutrition())
    }

    @Test
    fun `food nutritionForGrams scales per 100g basis`() {
        val f = food(per100g = NutritionValues(200.0, 20.0, 10.0, 5.0, 2.0))
        val n = f.nutritionForGrams(50.0)
        assertEquals(NutritionValues(100.0, 10.0, 5.0, 2.5, 1.0), n)
    }

    @Test
    fun `totalRawWeight sums ingredient raw weights`() {
        val f = food()
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 150.0), ingredient(f, 50.0)),
                finalWeight = null,
                portions = 1,
            )
        assertEquals(RawWeight(200.0), recipe.totalRawWeight())
    }

    @Test
    fun `recipe without final weight never derives per100g from raw weight`() {
        val f = food()
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 300.0)),
                finalWeight = null,
                portions = 1,
            )
        assertEquals(RawWeight(300.0), recipe.totalRawWeight())
        assertNull(recipe.nutritionPer100g())
    }

    @Test
    fun `nutritionPerPortion divides total by portions`() {
        val f = food(per100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0))
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 100.0), ingredient(f, 100.0)),
                finalWeight = FinalWeight(250.0),
                portions = 2,
            )
        // total = 200 kcal over 2 portions => 100 kcal / portion
        val perPortion = recipe.nutritionPerPortion()
        assertEquals(100.0, perPortion.kcal, 1e-9)
    }

    @Test
    fun `recipe requires positive portions`() {
        val f = food()
        assertFailsWith<IllegalArgumentException> {
            Recipe(
                id = RecipeId("r"),
                name = "T",
                ingredients = listOf(ingredient(f, 100.0)),
                finalWeight = null,
                portions = 0,
            )
        }
    }

    @Test
    fun `negative weights are rejected`() {
        assertFailsWith<IllegalArgumentException> { RawWeight(-1.0) }
        assertFailsWith<IllegalArgumentException> { FinalWeight(0.0) }
    }

    @Test
    fun `ingredient requires at least one of rawWeight or servingCount`() {
        val f = food(servingUnit = ServingUnit("1 Ei", 50.0))
        assertFailsWith<IllegalArgumentException> {
            RecipeIngredient(food = f, rawWeight = null, servingCount = null)
        }
    }

    @Test
    fun `ingredient allows both rawWeight and servingCount simultaneously`() {
        val f = food(servingUnit = ServingUnit("1 Ei", 50.0))
        val ing = RecipeIngredient(food = f, rawWeight = RawWeight(60.0), servingCount = 2.0)
        // effectiveRawWeight should prefer rawWeight
        assertEquals(RawWeight(60.0), ing.effectiveRawWeight())
    }

    @Test
    fun `servingCount without ServingUnit is rejected`() {
        val f = food(servingUnit = null)
        assertFailsWith<IllegalArgumentException> {
            RecipeIngredient(food = f, rawWeight = null, servingCount = 2.0)
        }
    }

    @Test
    fun `empty recipe is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Recipe(RecipeId("r"), "Empty", ingredients = emptyList(), finalWeight = null, portions = 1)
        }
    }

    @Test
    fun `diary food entry consumes by grams`() {
        val f = food(per100g = NutritionValues(200.0, 20.0, 10.0, 5.0, 2.0))
        val entry =
            DiaryEntry(
                id = DiaryEntryId("d1"),
                slot = MealSlot.LUNCH,
                date = java.time.LocalDate.of(2026, 1, 1),
                food = f,
                recipe = null,
                amountGrams = 50.0,
                portionCount = null,
            )
        assertEquals(NutritionValues(100.0, 10.0, 5.0, 2.5, 1.0), entry.consumedNutrition())
    }

    @Test
    fun `diary recipe entry consumes by portions`() {
        val f = food()
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 100.0), ingredient(f, 100.0)),
                finalWeight = FinalWeight(250.0),
                portions = 2,
            )
        val entry =
            DiaryEntry(
                id = DiaryEntryId("d1"),
                slot = MealSlot.DINNER,
                date = java.time.LocalDate.of(2026, 1, 1),
                food = null,
                recipe = recipe,
                amountGrams = null,
                portionCount = 1.5,
            )
        // total = 200 kcal, 2 portions => 100/portion * 1.5 = 150 kcal
        assertEquals(150.0, entry.consumedNutrition().kcal, 1e-9)
    }

    @Test
    fun `diary recipe entry consumes by grams with final weight`() {
        val f = food(per100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0))
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 100.0), ingredient(f, 100.0)),
                finalWeight = FinalWeight(250.0),
                portions = 2,
            )
        val entry =
            DiaryEntry(
                id = DiaryEntryId("d1"),
                slot = MealSlot.DINNER,
                date = java.time.LocalDate.of(2026, 1, 1),
                food = null,
                recipe = recipe,
                amountGrams = 125.0,
                portionCount = null,
            )
        // per100g = 80 kcal => 125 g = 100 kcal
        assertEquals(100.0, entry.consumedNutrition().kcal, 1e-9)
    }

    @Test
    fun `diary recipe entry by grams without final weight falls back to raw weight`() {
        val f = food(per100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0))
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 300.0)),
                finalWeight = null,
                portions = 1,
            )
        val entry =
            DiaryEntry(
                id = DiaryEntryId("d1"),
                slot = MealSlot.DINNER,
                date = java.time.LocalDate.of(2026, 1, 1),
                food = null,
                recipe = recipe,
                amountGrams = 150.0,
                portionCount = null,
            )
        // total raw = 300 g => 300 kcal; 150 g = 150 kcal
        assertEquals(150.0, entry.consumedNutrition().kcal, 1e-9)
    }

    @Test
    fun `diary entry rejects both grams and portions for recipe`() {
        val f = food()
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 100.0)),
                finalWeight = null,
                portions = 1,
            )
        assertFailsWith<IllegalArgumentException> {
            DiaryEntry(
                id = DiaryEntryId("d1"),
                slot = MealSlot.DINNER,
                date = java.time.LocalDate.of(2026, 1, 1),
                food = null,
                recipe = recipe,
                amountGrams = 100.0,
                portionCount = 1.0,
            )
        }
    }

    @Test
    fun `nutritionPer100g rounding stays precise for odd ratios`() {
        val f = food(per100g = NutritionValues(123.0, 9.5, 4.25, 2.0, 1.0))
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 333.0)),
                finalWeight = FinalWeight(500.0),
                portions = 1,
            )
        // total = 3.33 * 123 = 409.59 kcal over 500 g => 81.918 kcal / 100 g
        val per100 = recipe.nutritionPer100g()!!
        assertEquals(81.918, per100.kcal, 1e-9)
        assertEquals(9.5 * 3.33 / 5.0, per100.proteinG, 1e-9)
    }

    @Test
    fun `recipe carries category`() {
        val f = food()
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 100.0)),
                finalWeight = null,
                portions = 1,
                category = "Frühstück",
            )
        assertEquals("Frühstück", recipe.category)
    }

    @Test
    fun `estimatedPer100g falls back to raw weight when no finalWeight`() {
        val f = food(per100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0))
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 200.0), ingredient(f, 100.0)),
                finalWeight = null,
                portions = 1,
            )
        // total = 300 kcal, totalRawWeight = 300 g => 100 kcal / 100 g
        val estimated = recipe.estimatedPer100g()
        assertEquals(100.0, estimated.kcal, 1e-9)
    }

    @Test
    fun `estimatedPer100g differs from nutritionPer100g when cooking changes weight`() {
        val f = food(per100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0))
        val recipe =
            Recipe(
                id = RecipeId("r1"),
                name = "Test",
                ingredients = listOf(ingredient(f, 200.0)),
                finalWeight = FinalWeight(150.0),
                portions = 1,
            )
        // total = 200 kcal, totalRawWeight = 200 g => estimated = 100 kcal/100g
        // finalWeight = 150 g => actual per100g = 133.33 kcal/100g
        assertEquals(100.0, recipe.estimatedPer100g().kcal, 1e-9)
        assertEquals(133.333333333, recipe.nutritionPer100g()!!.kcal, 1e-6)
    }

    @Test
    fun `ingredient with null nutritionPer100g returns ZERO`() {
        val f = food(per100g = null)
        val ing = ingredient(f, 200.0)
        assertEquals(NutritionValues.ZERO, ing.nutrition())
    }

    @Test
    fun `nutritionForGrams with null nutritionPer100g returns ZERO`() {
        val f = food(per100g = null)
        assertEquals(NutritionValues.ZERO, f.nutritionForGrams(150.0))
    }
}
