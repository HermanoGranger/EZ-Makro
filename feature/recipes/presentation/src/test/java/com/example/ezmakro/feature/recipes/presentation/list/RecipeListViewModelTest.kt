package com.example.ezmakro.feature.recipes.presentation.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.feature.recipes.presentation.fake.FakeFoodRepository
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
class RecipeListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observeAll maps recipes to ui list`() =
        runTest {
            val repo = FakeRecipeRepository()
            repo.emitRecipe(
                Recipe(
                    id = RecipeId("r1"),
                    name = "Pfannkuchen",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1"),
                                rawWeight = RawWeight(100.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = FinalWeight(200.0),
                    portions = 2,
                ),
            )
            val vm = RecipeListViewModel(repo)

            assertThat(
                vm.state.value
                    .recipes
                    .map { it.name },
            ).containsExactly("Pfannkuchen")
            assertThat(
                vm.state.value
                    .recipes
                    .first()
                    .per100gKcal,
            ).isEqualTo("26")
        }

    @Test
    fun `clicking recipe emits NavigateToDetail`() =
        runTest {
            val repo = FakeRecipeRepository()
            repo.emitRecipe(
                Recipe(
                    id = RecipeId("r1"),
                    name = "T",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1"),
                                rawWeight = RawWeight(100.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 1,
                ),
            )
            val vm = RecipeListViewModel(repo)

            vm.events.test {
                vm.onAction(RecipeListAction.OnRecipeClick("r1"))
                assertThat(awaitItem()).isEqualTo(RecipeListEvent.NavigateToDetail("r1"))
            }
        }

    @Test
    fun `recipe click in diary mode sets pendingAddRecipeId without emitting`() =
        runTest {
            val repo = FakeRecipeRepository()
            repo.emitRecipe(
                Recipe(
                    id = RecipeId("r1"),
                    name = "T",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1"),
                                rawWeight = RawWeight(100.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 1,
                ),
            )
            val vm =
                RecipeListViewModel(
                    repository = repo,
                    savedStateHandle = SavedStateHandle(mapOf("addToDiaryDate" to "2026-07-19")),
                )
            vm.events.test {
                vm.onAction(RecipeListAction.OnRecipeClick("r1"))
                expectNoEvents()
            }
            assertThat(vm.state.value.pendingAddRecipeId).isEqualTo("r1")
        }

    @Test
    fun `confirm add to diary emits AddToDiary with slot and portions`() =
        runTest {
            val repo = FakeRecipeRepository()
            repo.emitRecipe(
                Recipe(
                    id = RecipeId("r1"),
                    name = "T",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1"),
                                rawWeight = RawWeight(100.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 1,
                ),
            )
            val vm =
                RecipeListViewModel(
                    repository = repo,
                    savedStateHandle = SavedStateHandle(mapOf("addToDiaryDate" to "2026-07-19")),
                )
            vm.onAction(RecipeListAction.OnRecipeClick("r1"))
            vm.events.test {
                vm.onAction(RecipeListAction.OnConfirmAddToDiary(MealSlot.DINNER, 3.0))
                val event = awaitItem()
                assertThat(event).isEqualTo(
                    RecipeListEvent.AddToDiary(
                        recipeId = "r1",
                        date = "2026-07-19",
                        slot = MealSlot.DINNER,
                        portions = 3.0,
                    ),
                )
            }
            assertThat(vm.state.value.pendingAddRecipeId).isNull()
        }
}
