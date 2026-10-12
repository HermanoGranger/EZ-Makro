package com.example.ezmakro.feature.recipes.presentation.detail

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.feature.recipes.presentation.fake.FakeRecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeRecipeRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeRecipeRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun recipe(id: String): Recipe =
        Recipe(
            id = RecipeId(id),
            name = "Recipe $id",
            ingredients =
                listOf(
                    RecipeIngredient(
                        food =
                            FoodItem(
                                id = FoodId("f1"),
                                name = "Food",
                                barcode = null,
                                nutritionPer100g = NutritionValues(100.0, 1.0, 1.0, 1.0, 1.0),
                                servingUnit = null,
                                isUserCreated = true,
                                cachedStatus = CachedFoodItemStatus.Original,
                                originalSnapshot = null,
                            ),
                        rawWeight = RawWeight(100.0),
                        servingCount = null,
                    ),
                ),
            finalWeight = null,
            portions = 1,
        )

    @Test
    fun `duplicate success navigates back and sets no error`() =
        runTest {
            repository.emitRecipe(recipe("r1"))
            val viewModel = RecipeDetailViewModel("r1", repository)

            viewModel.events.test {
                viewModel.onAction(RecipeDetailAction.OnDuplicateClick)
                assertThat(awaitItem()).isEqualTo(RecipeDetailEvent.NavigateBack)
            }
            assertThat(viewModel.state.value.duplicateError).isNull()
        }

    @Test
    fun `duplicate failure surfaces an error and does not navigate back`() =
        runTest {
            val viewModel = RecipeDetailViewModel("missing", repository)

            viewModel.events.test {
                viewModel.onAction(RecipeDetailAction.OnDuplicateClick)
                expectNoEvents()
            }
            assertThat(viewModel.state.value.duplicateError).isNotNull()
        }
}
