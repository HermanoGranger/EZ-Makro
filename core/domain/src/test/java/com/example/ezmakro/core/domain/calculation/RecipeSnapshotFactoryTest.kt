package com.example.ezmakro.core.domain.calculation

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import org.junit.jupiter.api.Test

private fun factoryFood(per100g: NutritionValues = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0)): FoodItem =
    FoodItem(
        id = FoodId("f1"),
        name = "Mehl",
        barcode = null,
        nutritionPer100g = per100g,
        servingUnit = null,
        isUserCreated = true,
        cachedStatus = CachedFoodItemStatus.Original,
        originalSnapshot = null,
    )

private fun factoryRecipe(
    rawGrams: Double = 200.0,
    finalWeight: FinalWeight? = null,
    portions: Int = 2,
    lastModifiedAt: Long = 1_234L,
): Recipe =
    Recipe(
        id = RecipeId("r1"),
        name = "Brot",
        ingredients =
            listOf(
                RecipeIngredient(
                    food = factoryFood(),
                    rawWeight = RawWeight(rawGrams),
                    servingCount = null,
                ),
            ),
        finalWeight = finalWeight,
        portions = portions,
        lastModifiedAt = lastModifiedAt,
    )

class RecipeSnapshotFactoryTest {
    @Test
    fun `snapshot uses final weight for per100g and grams per portion`() {
        // 200 g raw at 100 kcal / 100 g => 200 kcal total
        val recipe = factoryRecipe(rawGrams = 200.0, finalWeight = FinalWeight(250.0), portions = 2)

        val snapshot = createRecipeSnapshot(recipe)

        // 200 kcal over 250 g final weight => 80 kcal / 100 g (ADR-003)
        assertThat(snapshot.nutritionPer100g.kcal).isCloseTo(80.0, 1e-9)
        assertThat(snapshot.nutritionPer100g.proteinG).isCloseTo(8.0, 1e-9)
        assertThat(snapshot.gramsPerPortion).isCloseTo(125.0, 1e-9)
        assertThat(snapshot.recipeId).isEqualTo(RecipeId("r1"))
        assertThat(snapshot.recipeName).isEqualTo("Brot")
        assertThat(snapshot.portions).isEqualTo(2)
    }

    @Test
    fun `snapshot without final weight falls back to the raw total`() {
        val recipe = factoryRecipe(rawGrams = 200.0, finalWeight = null, portions = 4)

        val snapshot = createRecipeSnapshot(recipe)

        assertThat(snapshot.nutritionPer100g.kcal).isCloseTo(100.0, 1e-9)
        assertThat(snapshot.gramsPerPortion).isCloseTo(50.0, 1e-9)
    }

    @Test
    fun `snapshot with zero raw weight degrades to ZERO without NaN`() {
        val recipe = factoryRecipe(rawGrams = 0.0, finalWeight = null, portions = 2)

        val snapshot = createRecipeSnapshot(recipe)

        assertThat(snapshot.nutritionPer100g).isEqualTo(NutritionValues.ZERO)
        assertThat(snapshot.gramsPerPortion).isEqualTo(0.0)
    }

    @Test
    fun `snapshot passes originalLastModifiedAt through`() {
        val recipe = factoryRecipe(lastModifiedAt = 4_242L)

        val snapshot = createRecipeSnapshot(recipe)

        assertThat(snapshot.originalLastModifiedAt).isEqualTo(4_242L)
    }
}
