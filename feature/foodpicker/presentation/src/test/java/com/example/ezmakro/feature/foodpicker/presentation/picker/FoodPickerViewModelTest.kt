package com.example.ezmakro.feature.foodpicker.presentation.picker

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodPickerViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeFoodRepository: FakeFoodRepository
    private lateinit var fakeRecipeRepository: FakeRecipeRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeFoodRepository = FakeFoodRepository()
        fakeRecipeRepository = FakeRecipeRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        target: FoodPickerTarget = FoodPickerTarget.DIARY,
        targetDate: String? = null,
        foodRepository: FoodRepository = fakeFoodRepository,
        recipeRepository: RecipeRepository = fakeRecipeRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): FoodPickerViewModel =
        FoodPickerViewModel(
            target = target,
            targetDate = targetDate,
            foodRepository = foodRepository,
            recipeRepository = recipeRepository,
            savedStateHandle = savedStateHandle,
        )

    @Test
    fun `observeFavoriteFoods loads favorites into state`() =
        runTest {
            fakeFoodRepository.emitFoods(listOf(testFood("f1", isFavorite = true)))

            val vm = createViewModel()

            val favorites = vm.state.value.favoriteFoods
            assertThat(favorites).hasSize(1)
            assertThat(favorites.first().id).isEqualTo("f1")
        }

    @Test
    fun `observeFavoriteRecipes loads recipe favorites into state`() =
        runTest {
            fakeRecipeRepository.emitRecipes(listOf(testRecipe("r1", isFavorite = true)))

            val vm = createViewModel()

            val favorites = vm.state.value.favoriteRecipes
            assertThat(favorites).hasSize(1)
            assertThat(favorites.first().id).isEqualTo("r1")
        }

    @Test
    fun `observeRecentRecipes loads recent recipes into state`() =
        runTest {
            fakeRecipeRepository.emitRecipes(
                listOf(testRecipe("r1", lastUsedAt = 100L)),
            )

            val vm = createViewModel()

            val recent = vm.state.value.recentRecipes
            assertThat(recent).hasSize(1)
            assertThat(recent.first().id).isEqualTo("r1")
        }

    @Test
    fun `toggleFavorite on food calls foodRepository`() =
        runTest {
            fakeFoodRepository.emitFoods(listOf(testFood("f1")))
            val vm = createViewModel()

            vm.onAction(FoodPickerAction.OnToggleFavorite("f1"))

            assertThat(fakeFoodRepository.lastToggledFavorite).isEqualTo(FoodId("f1"))
        }

    @Test
    fun `toggleFavorite on recipe calls recipeRepository`() =
        runTest {
            fakeRecipeRepository.emitRecipes(listOf(testRecipe("r1")))
            val vm = createViewModel()

            vm.onAction(FoodPickerAction.OnToggleFavorite("r1"))

            assertThat(fakeRecipeRepository.lastToggledFavorite).isEqualTo(RecipeId("r1"))
        }

    @Test
    fun `foodClick marks food as used`() =
        runTest {
            fakeFoodRepository.emitFoods(listOf(testFood("f1", lastUsedAt = 100L)))
            val vm = createViewModel()

            vm.onAction(FoodPickerAction.OnFoodClick("f1"))

            assertThat(fakeFoodRepository.lastMarkedUsed).isEqualTo(FoodId("f1"))
        }

    @Test
    fun `recipeClick marks recipe as used`() =
        runTest {
            fakeRecipeRepository.emitRecipes(listOf(testRecipe("r1")))
            val vm = createViewModel()

            vm.onAction(FoodPickerAction.OnRecipeClick("r1"))

            assertThat(fakeRecipeRepository.lastMarkedUsed).isEqualTo(RecipeId("r1"))
        }

    @Test
    fun `localRecipeSearch filters by query`() =
        runTest {
            fakeRecipeRepository.emitRecipes(
                listOf(
                    testRecipe("r1", name = "Pfannkuchen"),
                    testRecipe("r2", name = "Salat"),
                ),
            )
            val vm = createViewModel()

            vm.onAction(FoodPickerAction.OnSearchQueryChange("Pfann"))

            val results = vm.state.value.localRecipeResults
            assertThat(results).hasSize(1)
            assertThat(results.first().id).isEqualTo("r1")
        }

    @Test
    fun `searchOnline fills onlineFoodItems with raw items`() =
        runTest {
            fakeFoodRepository.searchResult = Result.Success(listOf(testFood("f1")))
            val vm = createViewModel()

            vm.onAction(FoodPickerAction.OnSearchQueryChange("milch"))
            vm.onAction(FoodPickerAction.OnSearchOnlineClick)

            assertThat(vm.state.value.onlineSearchResults).hasSize(1)
            val onlineItems = vm.state.value.onlineFoodItems
            assertThat(onlineItems).hasSize(1)
            assertThat(onlineItems.first().id).isEqualTo(FoodId("f1"))
        }

    @Test
    fun `confirmAddToDiary for online item caches remote item before AddToDiary event`() =
        runTest {
            fakeFoodRepository.searchResult = Result.Success(listOf(testFood("f1")))
            val vm = createViewModel(targetDate = "2026-07-19")

            vm.onAction(FoodPickerAction.OnSearchQueryChange("milch"))
            vm.onAction(FoodPickerAction.OnSearchOnlineClick)
            vm.onAction(FoodPickerAction.OnFoodClick("f1"))

            vm.events.test {
                vm.onAction(
                    FoodPickerAction.OnConfirmAddToDiary(
                        slot = MealSlot.BREAKFAST,
                        amount = 100.0,
                        isGrams = true,
                    ),
                )
                assertThat(fakeFoodRepository.lastCachedItem?.id).isEqualTo(FoodId("f1"))
                assertThat(awaitItem()).isEqualTo(
                    FoodPickerEvent.AddToDiary(
                        foodId = "f1",
                        isRecipe = false,
                        date = "2026-07-19",
                        slot = MealSlot.BREAKFAST,
                        amount = 100.0,
                        isGrams = true,
                    ),
                )
            }
        }

    @Test
    fun `confirmAddToDiary for local item does not cache remote item`() =
        runTest {
            fakeFoodRepository.emitFoods(listOf(testFood("f1")))
            val vm = createViewModel(targetDate = "2026-07-19")

            vm.onAction(FoodPickerAction.OnFoodClick("f1"))

            vm.events.test {
                vm.onAction(
                    FoodPickerAction.OnConfirmAddToDiary(
                        slot = MealSlot.LUNCH,
                        amount = 50.0,
                        isGrams = true,
                    ),
                )
                assertThat(fakeFoodRepository.lastCachedItem).isNull()
                assertThat(awaitItem()).isEqualTo(
                    FoodPickerEvent.AddToDiary(
                        foodId = "f1",
                        isRecipe = false,
                        date = "2026-07-19",
                        slot = MealSlot.LUNCH,
                        amount = 50.0,
                        isGrams = true,
                    ),
                )
            }
        }

    @Test
    fun `confirmAddToDiary cache failure resets selection and sets selectionError without event`() =
        runTest {
            fakeFoodRepository.searchResult = Result.Success(listOf(testFood("f1")))
            fakeFoodRepository.shouldFailCacheRemoteItem = true
            val vm = createViewModel(targetDate = "2026-07-19")

            vm.onAction(FoodPickerAction.OnSearchQueryChange("milch"))
            vm.onAction(FoodPickerAction.OnSearchOnlineClick)
            vm.onAction(FoodPickerAction.OnFoodClick("f1"))
            assertThat(vm.state.value.selectedItemReady).isTrue()

            vm.events.test {
                vm.onAction(
                    FoodPickerAction.OnConfirmAddToDiary(
                        slot = MealSlot.BREAKFAST,
                        amount = 100.0,
                        isGrams = true,
                    ),
                )
                assertThat(vm.state.value.selectedItemReady).isFalse()
                assertThat(vm.state.value.selectedItemId).isNull()
                assertThat(vm.state.value.selectionError).isNotNull()
                expectNoEvents()
            }
        }

    @Test
    fun `recipe ingredient click on online item caches before AddAsIngredient`() =
        runTest {
            fakeFoodRepository.searchResult = Result.Success(listOf(testFood("f1")))
            val vm = createViewModel(target = FoodPickerTarget.RECIPE_INGREDIENT)

            vm.onAction(FoodPickerAction.OnSearchQueryChange("milch"))
            vm.onAction(FoodPickerAction.OnSearchOnlineClick)

            vm.events.test {
                vm.onAction(FoodPickerAction.OnFoodClick("f1"))
                assertThat(fakeFoodRepository.lastCachedItem?.id).isEqualTo(FoodId("f1"))
                assertThat(awaitItem()).isEqualTo(
                    FoodPickerEvent.AddAsIngredient(
                        itemId = "f1",
                        itemName = "Food f1",
                        servingLabel = null,
                        isRecipe = false,
                    ),
                )
            }
        }

    @Test
    fun `recipe ingredient click on local item does not cache remote item`() =
        runTest {
            fakeFoodRepository.emitFoods(listOf(testFood("f1")))
            val vm = createViewModel(target = FoodPickerTarget.RECIPE_INGREDIENT)

            vm.events.test {
                vm.onAction(FoodPickerAction.OnFoodClick("f1"))
                assertThat(fakeFoodRepository.lastCachedItem).isNull()
                assertThat(fakeFoodRepository.lastMarkedUsed).isEqualTo(FoodId("f1"))
                assertThat(awaitItem()).isEqualTo(
                    FoodPickerEvent.AddAsIngredient(
                        itemId = "f1",
                        itemName = "Food f1",
                        servingLabel = null,
                        isRecipe = false,
                    ),
                )
            }
        }

    private fun testFood(
        id: String,
        name: String = "Food $id",
        isFavorite: Boolean = false,
        lastUsedAt: Long? = null,
    ): FoodItem =
        FoodItem(
            id = FoodId(id),
            name = name,
            barcode = null,
            nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 20.0, 2.0),
            servingUnit = null,
            isUserCreated = true,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
            isFavorite = isFavorite,
            lastUsedAt = lastUsedAt,
        )

    private fun testRecipe(
        id: String,
        name: String = "Recipe $id",
        isFavorite: Boolean = false,
        lastUsedAt: Long? = null,
    ): Recipe =
        Recipe(
            id = RecipeId(id),
            name = name,
            ingredients =
                listOf(
                    RecipeIngredient(
                        food = testFood("ing-$id"),
                        rawWeight = RawWeight(100.0),
                        servingCount = null,
                    ),
                ),
            finalWeight = FinalWeight(200.0),
            portions = 1,
            isFavorite = isFavorite,
            lastUsedAt = lastUsedAt,
        )
}

private class FakeFoodRepository : FoodRepository {
    private val foodItems = MutableStateFlow<List<FoodItem>>(emptyList())

    var lastToggledFavorite: FoodId? = null
    var lastMarkedUsed: FoodId? = null
    var lastCachedItem: FoodItem? = null
    var searchResult: Result<List<FoodItem>, DataError.Network> = Result.Success(emptyList())
    var shouldFailCacheRemoteItem: Boolean = false

    fun emitFoods(foods: List<FoodItem>) {
        foodItems.value = foods
    }

    override fun observeAll(): Flow<List<FoodItem>> = foodItems

    override fun observeFavorites(): Flow<List<FoodItem>> = foodItems.map { items -> items.filter { it.isFavorite } }

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> =
        foodItems.map { items ->
            items
                .filter { it.lastUsedAt != null }
                .sortedByDescending { it.lastUsedAt }
                .take(limit)
        }

    override fun searchLocal(query: String): Flow<List<FoodItem>> =
        foodItems.map { items -> items.filter { it.name.contains(query, ignoreCase = true) } }

    override suspend fun getById(id: FoodId): Result<FoodItem?, DataError.Local> =
        Result.Success(foodItems.value.firstOrNull { it.id == id })

    override suspend fun searchRemote(query: String): Result<List<FoodItem>, DataError.Network> = searchResult

    override suspend fun lookupByBarcode(barcode: String): Result<FoodItem?, DataError> = Result.Success(null)

    override suspend fun saveUserEdit(foodItem: FoodItem): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun refetchFromOff(id: FoodId): Result<FoodItem?, DataError> = Result.Success(null)

    override suspend fun toggleFavorite(id: FoodId): EmptyResult<DataError.Local> {
        lastToggledFavorite = id
        foodItems.value =
            foodItems.value.map { item ->
                if (item.id == id) item.copy(isFavorite = !item.isFavorite) else item
            }
        return Result.Success(Unit)
    }

    override suspend fun markUsed(id: FoodId): EmptyResult<DataError.Local> {
        lastMarkedUsed = id
        foodItems.value =
            foodItems.value.map { item ->
                if (item.id == id) item.copy(lastUsedAt = System.currentTimeMillis()) else item
            }
        return Result.Success(Unit)
    }

    override suspend fun cacheRemoteItem(item: FoodItem): EmptyResult<DataError.Local> {
        lastCachedItem = item
        if (shouldFailCacheRemoteItem) return Result.Error(DataError.Local.UNKNOWN)
        foodItems.value = foodItems.value.filter { it.id != item.id } + item
        return Result.Success(Unit)
    }
}

private class FakeRecipeRepository : RecipeRepository {
    private val recipeItems = MutableStateFlow<List<Recipe>>(emptyList())

    var lastToggledFavorite: RecipeId? = null
    var lastMarkedUsed: RecipeId? = null

    fun emitRecipes(recipes: List<Recipe>) {
        recipeItems.value = recipes
    }

    override fun observeAll(): Flow<List<Recipe>> = recipeItems

    override fun observeById(id: RecipeId): Flow<Recipe?> =
        recipeItems.map { recipes -> recipes.firstOrNull { it.id == id } }

    override fun observeFavorites(): Flow<List<Recipe>> =
        recipeItems.map { recipes -> recipes.filter { it.isFavorite } }

    override fun observeRecent(limit: Int): Flow<List<Recipe>> =
        recipeItems.map { recipes ->
            recipes
                .filter { it.lastUsedAt != null }
                .sortedByDescending { it.lastUsedAt }
                .take(limit)
        }

    override suspend fun getById(id: RecipeId): Result<Recipe?, DataError.Local> =
        Result.Success(recipeItems.value.firstOrNull { it.id == id })

    override suspend fun save(recipe: Recipe): EmptyResult<DataError.Local> {
        recipeItems.value = recipeItems.value + recipe
        return Result.Success(Unit)
    }

    override suspend fun delete(id: RecipeId): EmptyResult<DataError.Local> {
        recipeItems.value = recipeItems.value.filter { it.id != id }
        return Result.Success(Unit)
    }

    override suspend fun toggleFavorite(id: RecipeId): EmptyResult<DataError.Local> {
        lastToggledFavorite = id
        recipeItems.value =
            recipeItems.value.map { recipe ->
                if (recipe.id == id) recipe.copy(isFavorite = !recipe.isFavorite) else recipe
            }
        return Result.Success(Unit)
    }

    override suspend fun markUsed(id: RecipeId): EmptyResult<DataError.Local> {
        lastMarkedUsed = id
        recipeItems.value =
            recipeItems.value.map { recipe ->
                if (recipe.id == id) recipe.copy(lastUsedAt = System.currentTimeMillis()) else recipe
            }
        return Result.Success(Unit)
    }

    override suspend fun duplicate(id: RecipeId): Result<RecipeId, DataError.Local> =
        Result.Error(DataError.Local.NOT_FOUND)
}
