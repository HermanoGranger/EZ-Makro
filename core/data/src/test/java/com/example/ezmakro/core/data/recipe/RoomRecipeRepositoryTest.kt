package com.example.ezmakro.core.data.recipe

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import com.example.ezmakro.core.database.dao.RecipeDao
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.domain.calculation.totalNutrition
import com.example.ezmakro.core.domain.datasource.FoodLocalDataSource
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

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

private class FakeRecipeDao : RecipeDao {
    val recipes = MutableStateFlow<Map<String, RecipeEntity>>(emptyMap())
    val ingredients = MutableStateFlow<Map<String, MutableList<RecipeIngredientEntity>>>(emptyMap())

    override fun upsertRecipe(recipe: RecipeEntity) {
        recipes.value = recipes.value + (recipe.id to recipe)
    }

    override fun upsertIngredients(ings: List<RecipeIngredientEntity>) {
        ings.forEach { e ->
            val list = ingredients.value[e.recipeId]?.toMutableList() ?: mutableListOf()
            list.removeIf { it.foodItemId == e.foodItemId }
            list.add(e)
            ingredients.value = ingredients.value + (e.recipeId to list)
        }
    }

    override suspend fun deleteRecipe(recipe: RecipeEntity) {
        recipes.value = recipes.value - recipe.id
        ingredients.value = ingredients.value - recipe.id
    }

    override suspend fun getById(id: String): RecipeEntity? = recipes.value[id]

    override fun observeAll(): Flow<List<RecipeEntity>> = recipes.map { it.values.toList() }

    override suspend fun getIngredients(recipeId: String): List<RecipeIngredientEntity> =
        ingredients.value[recipeId] ?: emptyList()

    override suspend fun upsertRecipeWithIngredients(
        recipe: RecipeEntity,
        ings: List<RecipeIngredientEntity>,
    ) {
        upsertRecipe(recipe)
        ingredients.value = ingredients.value - recipe.id
        upsertIngredients(ings)
    }

    override fun deleteIngredientsFor(recipeId: String) {
        ingredients.value = ingredients.value - recipeId
    }
}

private class FakeFoodLocal : FoodLocalDataSource {
    val store = MutableStateFlow<Map<String, FoodItem>>(emptyMap())

    override fun observeAll(): Flow<List<FoodItem>> = store.map { it.values.toList() }

    override fun observeFavorites(): Flow<List<FoodItem>> = store.map { it.values.filter { f -> f.isFavorite } }

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> =
        store.map { m -> m.values.filter { it.lastUsedAt != null }.take(limit) }

    override suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local> = Result.Success(store.value[id.value])

    override suspend fun getByBarcode(barcode: String): Result<FoodItem?, DataError.Local> =
        Result.Success(store.value.values.firstOrNull { it.barcode == barcode })

    override fun searchByName(query: String): Flow<List<FoodItem>> =
        store.map { m -> m.values.filter { it.name.contains(query, ignoreCase = true) } }

    override suspend fun upsert(foodItem: FoodItem): EmptyResult<DataError.Local> {
        store.value = store.value + (foodItem.id.value to foodItem)
        return Result.Success(Unit)
    }

    override suspend fun upsertAll(foodItems: List<FoodItem>): EmptyResult<DataError.Local> {
        foodItems.forEach { upsert(it) }
        return Result.Success(Unit)
    }

    override suspend fun deleteById(id: FoodId): EmptyResult<DataError.Local> {
        store.value = store.value - id.value
        return Result.Success(Unit)
    }

    override suspend fun markUserModified(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun markUpdateAvailable(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun setFavorite(
        id: FoodId,
        favorite: Boolean,
    ): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun markUsed(
        id: FoodId,
        usedAtEpochMillis: Long,
    ): EmptyResult<DataError.Local> = Result.Success(Unit)
}

class RoomRecipeRepositoryTest {
    private fun recipe(id: String = "r1"): Recipe =
        Recipe(
            id = RecipeId(id),
            name = "Pfannkuchen",
            ingredients =
                listOf(
                    RecipeIngredient(food = food("f1"), rawWeight = RawWeight(100.0), servingCount = null),
                    RecipeIngredient(food = food("f2"), rawWeight = RawWeight(200.0), servingCount = null),
                ),
            finalWeight = FinalWeight(500.0),
            portions = 2,
            category = "Frühstück",
            isMealPrep = true,
        )

    @Test
    fun `save persists recipe and ingredients atomically`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))

            repo.save(recipe())

            assertThat(dao.recipes.value["r1"]?.name).isEqualTo("Pfannkuchen")
            assertThat(dao.recipes.value["r1"]?.category).isEqualTo("Frühstück")
            assertThat(dao.recipes.value["r1"]?.isMealPrep).isEqualTo(true)
            assertThat(dao.ingredients.value["r1"]?.size).isEqualTo(2)
        }

    @Test
    fun `observeAll emits resolved recipes with computed nutrition`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))
            repo.save(recipe())

            val list = repo.observeAll().first()
            assertThat(list).hasSize(1)
            assertThat(list.first().ingredients).hasSize(2)
            assertThat(list.first().totalNutrition().kcal).isEqualTo(300.0)
        }

    @Test
    fun `duplicate creates a copy with Kopie suffix and fresh id`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))
            repo.save(recipe())

            val result = repo.duplicate(RecipeId("r1"))

            assertThat(result).isInstanceOf(Result.Success::class)
            val copyId = (result as Result.Success).data
            assertThat(copyId.value).isNotEqualTo("r1")
            assertThat(dao.recipes.value[copyId.value]?.name).isEqualTo("Pfannkuchen (Kopie)")
            assertThat(dao.ingredients.value[copyId.value]?.size).isEqualTo(2)
        }

    @Test
    fun `duplicate errors when source is missing`() =
        runTest {
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(FakeRecipeDao(), FakeFoodLocal()))

            val result = repo.duplicate(RecipeId("missing"))

            assertThat(result).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
        }

    @Test
    fun `delete removes recipe and ingredients`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))
            repo.save(recipe())

            repo.delete(RecipeId("r1"))

            assertThat(dao.recipes.value["r1"]).isNull()
            assertThat(dao.ingredients.value["r1"]).isNull()
        }

    @Test
    fun `ingredient with missing food is dropped on resolution`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))
            repo.save(
                Recipe(
                    id = RecipeId("r1"),
                    name = "T",
                    ingredients =
                        listOf(
                            RecipeIngredient(food = food("f1"), rawWeight = RawWeight(100.0), servingCount = null),
                            RecipeIngredient(food = food("MISSING"), rawWeight = RawWeight(100.0), servingCount = null),
                        ),
                    finalWeight = null,
                    portions = 1,
                ),
            )

            val resolved = repo.observeAll().first()
            // The recipe is dropped entirely because it would have <1 resolvable ingredient.
            assertThat(resolved).containsExactly()
        }
}
