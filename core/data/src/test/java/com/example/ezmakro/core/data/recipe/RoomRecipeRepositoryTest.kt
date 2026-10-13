package com.example.ezmakro.core.data.recipe

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
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
import com.example.ezmakro.core.domain.model.ServingUnit
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
    val ingredientsMap = MutableStateFlow<Map<String, MutableList<RecipeIngredientEntity>>>(emptyMap())

    override fun upsertRecipe(recipe: RecipeEntity) {
        recipes.value = recipes.value + (recipe.id to recipe)
    }

    override fun upsertIngredients(ings: List<RecipeIngredientEntity>) {
        ings.forEach { e ->
            val list = ingredientsMap.value[e.recipeId]?.toMutableList() ?: mutableListOf()
            list.removeIf { it.foodItemId == e.foodItemId }
            list.add(e)
            ingredientsMap.value = ingredientsMap.value + (e.recipeId to list)
        }
    }

    override suspend fun deleteRecipe(recipe: RecipeEntity) {
        recipes.value = recipes.value - recipe.id
        ingredientsMap.value = ingredientsMap.value - recipe.id
    }

    override suspend fun getById(id: String): RecipeEntity? = recipes.value[id]

    override fun observeAll(): Flow<List<RecipeEntity>> = recipes.map { it.values.toList() }

    override suspend fun getIngredients(recipeId: String): List<RecipeIngredientEntity> =
        ingredientsMap.value[recipeId] ?: emptyList()

    override suspend fun upsertRecipeWithIngredients(
        recipe: RecipeEntity,
        ings: List<RecipeIngredientEntity>,
    ) {
        upsertRecipe(recipe)
        ingredientsMap.value = ingredientsMap.value - recipe.id
        upsertIngredients(ings)
    }

    override fun deleteIngredientsFor(recipeId: String) {
        ingredientsMap.value = ingredientsMap.value - recipeId
    }

    override suspend fun getRecipeNamesUsingFood(foodItemId: String): List<String> =
        ingredientsMap.value.entries
            .filter { (_, ings) -> ings.any { it.foodItemId == foodItemId } }
            .mapNotNull { (recipeId, _) -> recipes.value[recipeId]?.name }
            .distinct()
            .sorted()

    override fun observeFavorites(): Flow<List<RecipeEntity>> =
        recipes.map { entities ->
            entities.values
                .filter { it.isFavorite == 1 }
                .sortedBy { it.name }
        }

    override fun observeRecent(limit: Int): Flow<List<RecipeEntity>> =
        recipes.map { entities ->
            entities.values
                .filter { it.lastUsedAt != null }
                .sortedByDescending { it.lastUsedAt }
                .take(limit)
        }

    override suspend fun setFavorite(
        id: String,
        favorite: Boolean,
    ) {
        val updated = recipes.value.toMutableMap()
        updated[id] = updated[id]?.copy(isFavorite = if (favorite) 1 else 0) ?: return
        recipes.value = updated
    }

    override suspend fun markUsed(
        id: String,
        usedAt: Long,
    ) {
        val updated = recipes.value.toMutableMap()
        updated[id] = updated[id]?.copy(lastUsedAt = usedAt) ?: return
        recipes.value = updated
    }
}

private class FakeFoodLocal : FoodLocalDataSource {
    val store = MutableStateFlow<Map<String, FoodItem>>(emptyMap())

    override fun observeAll(): Flow<List<FoodItem>> = store.map { it.values.toList() }

    override fun observeFavorites(): Flow<List<FoodItem>> = store.map { it.values.filter { f -> f.isFavorite } }

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> =
        store.map { m -> m.values.filter { it.lastUsedAt != null }.take(limit) }

    override suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local> = Result.Success(store.value[id.value])

    override fun observeById(id: FoodId): Flow<FoodItem?> = store.map { it[id.value] }

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
            assertThat(dao.ingredientsMap.value["r1"]?.size).isEqualTo(2)
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
    fun `observeFavorites returns only favorited recipes`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))

            repo.save(recipe("r1").copy(isFavorite = false))
            repo.save(recipe("r2").copy(isFavorite = true))

            val favorites = repo.observeFavorites().first()
            assertThat(favorites).hasSize(1)
            assertThat(favorites.first().id.value).isEqualTo("r2")
        }

    @Test
    fun `observeRecent returns recipes ordered by lastUsedAt descending`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))

            repo.save(recipe("r1").copy(lastUsedAt = 100))
            repo.save(recipe("r2").copy(lastUsedAt = 200))
            repo.save(recipe("r3").copy(lastUsedAt = 300))

            val recent = repo.observeRecent(2).first()
            assertThat(recent).hasSize(2)
            assertThat(recent[0].id.value).isEqualTo("r3")
            assertThat(recent[1].id.value).isEqualTo("r2")
        }

    @Test
    fun `toggleFavorite toggles the isFavorite flag`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))

            repo.save(recipe("r1"))

            assertThat(dao.recipes.value["r1"]?.isFavorite).isEqualTo(0)

            repo.toggleFavorite(RecipeId("r1"))
            assertThat(dao.recipes.value["r1"]?.isFavorite).isEqualTo(1)

            repo.toggleFavorite(RecipeId("r1"))
            assertThat(dao.recipes.value["r1"]?.isFavorite).isEqualTo(0)
        }

    @Test
    fun `markUsed sets the lastUsedAt timestamp`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))

            repo.save(recipe("r1"))
            assertThat(dao.recipes.value["r1"]?.lastUsedAt).isNull()

            repo.markUsed(RecipeId("r1"))
            assertThat(dao.recipes.value["r1"]?.lastUsedAt).isNotNull()
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
            assertThat(dao.ingredientsMap.value[copyId.value]?.size).isEqualTo(2)
        }

    @Test
    fun `duplicate errors when source is missing`() =
        runTest {
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(FakeRecipeDao(), FakeFoodLocal()))

            val result = repo.duplicate(RecipeId("missing"))

            assertThat(result).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
        }

    @Test
    fun `duplicate assigns a fresh lastModifiedAt and does not copy lastUsedAt`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))
            repo.save(recipe().copy(lastModifiedAt = 111L, lastUsedAt = 222L))

            val result = repo.duplicate(RecipeId("r1"))
            val copyId = (result as Result.Success).data
            val copy = dao.recipes.value[copyId.value]

            assertThat(copy).isNotNull()
            assertThat(copy!!.lastModifiedAt > 0L).isEqualTo(true)
            assertThat(copy.lastModifiedAt >= 111L).isEqualTo(true)
            assertThat(copy.lastUsedAt).isNull()
            assertThat(copy.name).isEqualTo("Pfannkuchen (Kopie)")
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
            assertThat(dao.ingredientsMap.value["r1"]).isNull()
        }

    @Test
    fun `ingredient with missing food is skipped instead of dropping the whole recipe`() =
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
            assertThat(resolved).hasSize(1)
            val ingredient = resolved.first().ingredients.single()
            assertThat(ingredient.food?.name).isEqualTo("Food f1")
        }

    @Test
    fun `ingredient whose serving unit was removed degrades instead of breaking hydration`() =
        runTest {
            val foodLocal = FakeFoodLocal().apply { store.value = mapOf("f1" to food("f1"), "f2" to food("f2")) }
            val dao = FakeRecipeDao()
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))
            repo.save(
                Recipe(
                    id = RecipeId("r1"),
                    name = "Eipfanne",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = food("f1").copy(servingUnit = ServingUnit("1 Ei", 50.0)),
                                rawWeight = null,
                                servingCount = 2.0,
                            ),
                            RecipeIngredient(food = food("f2"), rawWeight = RawWeight(100.0), servingCount = null),
                        ),
                    finalWeight = null,
                    portions = 1,
                ),
            )

            // The food's serving unit is removed afterwards (saveUserEdit / resetToOriginal).
            // The portion-only ingredient must be skipped, the recipe must still hydrate.
            foodLocal.store.value = mapOf("f1" to food("f1"), "f2" to food("f2"))

            val resolved = repo.observeAll().first()
            assertThat(resolved).hasSize(1)
            val ingredient = resolved.first().ingredients.single()
            assertThat(ingredient.food?.name).isEqualTo("Food f2")
        }

    @Test
    fun `findRecipeNamesUsingFood returns distinct sorted names sharing the food`() =
        runTest {
            val dao = FakeRecipeDao()
            val foodLocal = FakeFoodLocal()
            val repo = RoomRecipeRepository(RoomRecipeLocalDataSource(dao, foodLocal))
            repo.save(recipe("r1").copy(name = "Pfannkuchen"))
            repo.save(
                Recipe(
                    id = RecipeId("r2"),
                    name = "Omelett",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = food("f2"),
                                rawWeight = RawWeight(50.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 1,
                ),
            )

            val usingF2 = (repo.findRecipeNamesUsingFood(FoodId("f2")) as Result.Success).data
            val usingF1 = (repo.findRecipeNamesUsingFood(FoodId("f1")) as Result.Success).data
            val unused = (repo.findRecipeNamesUsingFood(FoodId("f9")) as Result.Success).data

            assertThat(usingF2).isEqualTo(listOf("Omelett", "Pfannkuchen"))
            assertThat(usingF1).isEqualTo(listOf("Pfannkuchen"))
            assertThat(unused).isEqualTo(emptyList())
        }
}
