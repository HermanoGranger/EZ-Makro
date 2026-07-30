package com.example.ezmakro.core.database.dao

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.example.ezmakro.core.database.DatabaseTest
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.database.testutil.FoodItemFixtures
import com.example.ezmakro.core.database.testutil.RecipeFixtures
import com.example.ezmakro.core.database.testutil.upsertFood
import kotlinx.coroutines.test.runTest
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
            category = null,
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
    fun `upsertRecipeWithIngredients stores recipe and ingredients atomically`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f2"))
            dao.upsertRecipeWithIngredients(recipe(), listOf(ingredient(), ingredient(foodId = "f2", grams = 200.0)))

            assertThat(dao.getById("r1")?.name).isEqualTo("Pfannkuchen")
            assertThat(dao.getIngredients("r1")).hasSize(2)
        }

    @Test
    fun `upsertRecipeWithIngredients replaces existing ingredients`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f2"))
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f3"))
            dao.upsertRecipeWithIngredients(recipe(), listOf(ingredient()))
            dao.upsertRecipeWithIngredients(recipe(), listOf(ingredient(foodId = "f2"), ingredient(foodId = "f3")))

            val ids = dao.getIngredients("r1").map { it.foodItemId }
            assertThat(ids).containsExactlyInAnyOrder("f2", "f3")
        }

    @Test
    fun `deleteRecipe cascades to ingredients`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
            dao.upsertRecipeWithIngredients(recipe(), listOf(ingredient()))
            dao.deleteRecipe(recipe())

            assertThat(dao.getById("r1")).isNull()
            assertThat(dao.getIngredients("r1")).hasSize(0)
        }

    @Test
    fun `setFavorite toggles is_favorite flag`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe(isFavorite = false),
                listOf(RecipeFixtures.ingredient()),
            )

            dao.setFavorite("r1", true)
            assertThat(dao.getById("r1")?.isFavorite).isEqualTo(1)

            dao.setFavorite("r1", false)
            assertThat(dao.getById("r1")?.isFavorite).isEqualTo(0)
        }

    @Test
    fun `observeFavorites emits only favorited recipes ordered by name`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe("r1", name = "B", isFavorite = true),
                listOf(RecipeFixtures.ingredient()),
            )
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe("r2", name = "A", isFavorite = true),
                listOf(RecipeFixtures.ingredient(foodId = "f1", recipeId = "r2")),
            )
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe("r3", name = "C", isFavorite = false),
                listOf(RecipeFixtures.ingredient(foodId = "f1", recipeId = "r3")),
            )

            dao.observeFavorites().test {
                val favorites = awaitItem()
                assertThat(favorites.map { it.id }).containsExactlyInAnyOrder("r1", "r2")
                assertThat(favorites[0].name).isEqualTo("A")
                assertThat(favorites[1].name).isEqualTo("B")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markUsed sets last_used_at timestamp`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe(),
                listOf(RecipeFixtures.ingredient()),
            )

            assertThat(dao.getById("r1")?.lastUsedAt).isNull()

            dao.markUsed("r1", 1000L)
            assertThat(dao.getById("r1")?.lastUsedAt).isEqualTo(1000L)

            dao.markUsed("r1", 2000L)
            assertThat(dao.getById("r1")?.lastUsedAt).isEqualTo(2000L)
        }

    @Test
    fun `observeRecent emits recipes with last_used_at ordered descending`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "f1"))
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe("r1", lastUsedAt = 3000L),
                listOf(RecipeFixtures.ingredient()),
            )
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe("r2", lastUsedAt = 1000L),
                listOf(RecipeFixtures.ingredient(foodId = "f1", recipeId = "r2")),
            )
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe("r3", lastUsedAt = null),
                listOf(RecipeFixtures.ingredient(foodId = "f1", recipeId = "r3")),
            )
            dao.upsertRecipeWithIngredients(
                RecipeFixtures.recipe("r4", lastUsedAt = 2000L),
                listOf(RecipeFixtures.ingredient(foodId = "f1", recipeId = "r4")),
            )

            dao.observeRecent(3).test {
                val recent = awaitItem()
                assertThat(recent).hasSize(3)
                assertThat(recent[0].id).isEqualTo("r1") // 3000
                assertThat(recent[1].id).isEqualTo("r4") // 2000
                assertThat(recent[2].id).isEqualTo("r2") // 1000
                cancelAndIgnoreRemainingEvents()
            }
        }
}
