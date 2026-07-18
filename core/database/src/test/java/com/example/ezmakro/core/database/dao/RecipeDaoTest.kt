package com.example.ezmakro.core.database.dao

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.example.ezmakro.core.database.DatabaseTest
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.database.testutil.FoodItemFixtures
import com.example.ezmakro.core.database.testutil.upsertFood
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeDaoTest : DatabaseTest() {
    private val dao get() = db.recipeDao()

    private fun recipe(id: String = "r1") =
        RecipeEntity(
            id = id,
            name = "Pfannkuchen",
            finalWeightGrams = null,
            portions = 2,
            scale = 1.0,
            category = null,
            isMealPrep = false,
        )

    private fun ingredient(
        recipeId: String = "r1",
        foodId: String = "f1",
        grams: Double = 100.0,
    ) = RecipeIngredientEntity(
        recipeId = recipeId,
        foodItemId = foodId,
        rawWeightGrams = grams,
        servingCount = null,
    )

    @Test
    fun `upsertRecipeWithIngredients stores recipe and ingredients atomically`() {
        db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
        db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f2"))
        dao.upsertRecipeWithIngredients(recipe(), listOf(ingredient(), ingredient(foodId = "f2", grams = 200.0)))

        assertThat(dao.getById("r1")?.name).isEqualTo("Pfannkuchen")
        assertThat(dao.getIngredients("r1")).hasSize(2)
    }

    @Test
    fun `upsertRecipeWithIngredients replaces existing ingredients`() {
        db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
        db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f2"))
        db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f3"))
        dao.upsertRecipeWithIngredients(recipe(), listOf(ingredient()))
        dao.upsertRecipeWithIngredients(recipe(), listOf(ingredient(foodId = "f2"), ingredient(foodId = "f3")))

        val ids = dao.getIngredients("r1").map { it.foodItemId }
        assertThat(ids).containsExactlyInAnyOrder("f2", "f3")
    }

    @Test
    fun `deleteRecipe cascades to ingredients`() {
        db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
        dao.upsertRecipeWithIngredients(recipe(), listOf(ingredient()))
        dao.deleteRecipe(recipe())

        assertThat(dao.getById("r1")).isNull()
        assertThat(dao.getIngredients("r1")).hasSize(0)
    }
}
