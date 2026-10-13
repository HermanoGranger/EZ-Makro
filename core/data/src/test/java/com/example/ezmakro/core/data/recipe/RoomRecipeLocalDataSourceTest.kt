package com.example.ezmakro.core.data.recipe

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.example.ezmakro.core.data.food.RoomFoodLocalDataSource
import com.example.ezmakro.core.database.EZDatabase
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression guard for the food-delete warning: [RoomRecipeLocalDataSource.getRecipeNamesUsingFood]
 * must work when invoked from the main thread (as the ViewModel does), so the
 * DAO query is dispatched off the main thread. The DB is intentionally built
 * WITHOUT `allowMainThreadQueries`, so a main-thread DAO access would throw and
 * be swallowed into a [Result.Error].
 */
@RunWith(RobolectricTestRunner::class)
class RoomRecipeLocalDataSourceTest {
    private lateinit var db: EZDatabase
    private lateinit var source: RoomRecipeLocalDataSource

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EZDatabase::class.java,
                ).build()
        source =
            RoomRecipeLocalDataSource(
                dao = db.recipeDao(),
                foodLocalDataSource = RoomFoodLocalDataSource(db.foodItemDao()),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getRecipeNamesUsingFood works when called from the main thread`() =
        runBlocking {
            withContext(Dispatchers.IO) {
                db.foodItemDao().upsertAll(listOf(foodEntity("f1", "Mehl")))
                db.recipeDao().upsertRecipeWithIngredients(
                    recipeEntity("r1", "Pfannkuchen"),
                    listOf(ingredientEntity("r1", "f1")),
                )
            }

            val result = source.getRecipeNamesUsingFood(FoodId("f1"))

            assertThat(result).isEqualTo(Result.Success(listOf("Pfannkuchen")))
        }

    private fun foodEntity(
        id: String,
        name: String,
    ) = FoodItemEntity(
        id = id,
        name = name,
        barcode = null,
        nutriKcal = null,
        nutriProteinG = null,
        nutriFatG = null,
        nutriCarbsG = null,
        nutriFiberG = null,
        servingLabel = null,
        servingGramsEquivalent = null,
        isUserCreated = false,
        cachedStatus = "ORIGINAL",
        origName = name,
        origBarcode = null,
        origNutriKcal = null,
        origNutriProteinG = null,
        origNutriFatG = null,
        origNutriCarbsG = null,
        origNutriFiberG = null,
        origServingLabel = null,
        origServingGramsEquivalent = null,
        origIsUserCreated = false,
    )

    private fun recipeEntity(
        id: String,
        name: String,
    ) = RecipeEntity(
        id = id,
        name = name,
        finalWeightGrams = null,
        portions = 1,
        category = null,
    )

    private fun ingredientEntity(
        recipeId: String,
        foodItemId: String,
    ) = RecipeIngredientEntity(
        recipeId = recipeId,
        foodItemId = foodItemId,
        rawWeightGrams = 100.0,
        servingCount = null,
    )
}
