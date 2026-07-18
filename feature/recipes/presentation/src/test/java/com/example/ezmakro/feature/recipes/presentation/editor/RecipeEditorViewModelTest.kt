package com.example.ezmakro.feature.recipes.presentation.editor

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.FinalWeight
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
class RecipeEditorViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        recipeId: String? = null,
        isQuickMeal: Boolean = false,
        foodRepo: FakeFoodRepository = FakeFoodRepository(),
        recipeRepo: FakeRecipeRepository = FakeRecipeRepository(),
    ): RecipeEditorViewModel =
        RecipeEditorViewModel(
            recipeId = recipeId,
            isQuickMeal = isQuickMeal,
            recipeRepository = recipeRepo,
            foodRepository = foodRepo,
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
        )

    @Test
    fun `save emits Saved event and persists recipe`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
            assertThat(vm.state.value.availableFoods.size).isEqualTo(1)
            vm.onAction(RecipeEditorAction.OnNameChange("Müsli"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            assertThat(vm.state.value.ingredients.size).isEqualTo(1)
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved.size).isEqualTo(1)
            assertThat(recipeRepo.saved.first().name)
                .isEqualTo("Müsli")
            assertThat(recipeRepo.saved.first().ingredients)
                .isEqualTo(
                    listOf(
                        RecipeIngredient(
                            food = FakeFoodRepository.food("f1", "Apple"),
                            rawWeight = RawWeight(100.0),
                            servingCount = null,
                        ),
                    ),
                )
        }

    @Test
    fun `save rejects blank name`() =
        runTest {
            val vm = viewModel()
            vm.onAction(RecipeEditorAction.OnNameChange("   "))
            vm.onAction(RecipeEditorAction.OnSaveClick)
            assertThat(vm.state.value.nameError != null).isTrue()
            assertThat(vm.state.value.isSaving)
                .isEqualTo(false)
        }

    @Test
    fun `save rejects recipe without ingredients`() =
        runTest {
            val vm = viewModel()
            vm.onAction(RecipeEditorAction.OnNameChange("Allein"))
            vm.onAction(RecipeEditorAction.OnSaveClick)
            assertThat(vm.state.value.ingredientError != null).isTrue()
        }

    @Test
    fun `quick meal hides meal prep and scale in persisted recipe`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            val vm = viewModel(isQuickMeal = true, foodRepo = foodRepo, recipeRepo = recipeRepo)

            vm.onAction(RecipeEditorAction.OnNameChange("Schnell"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "50"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved.size).isEqualTo(1)
            assertThat(recipeRepo.saved.first().isMealPrep)
                .isEqualTo(false)
            assertThat(recipeRepo.saved.first().scale)
                .isEqualTo(1.0)
        }

    @Test
    fun `existing recipe is preloaded into state`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(
                Recipe(
                    id = RecipeId("r1"),
                    name = "Brot",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1", "Apple"),
                                rawWeight = RawWeight(200.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 3,
                    category = "Backen",
                ),
            )
            val vm = viewModel(recipeId = "r1", foodRepo = foodRepo, recipeRepo = recipeRepo)

            assertThat(vm.state.value.name)
                .isEqualTo("Brot")
            assertThat(vm.state.value.ingredients.size).isEqualTo(1)
            assertThat(
                vm.state.value.ingredients
                    .first()
                    .rawWeight,
            ).isEqualTo("200.0")
        }

    @Test
    fun `portion mode persists recipe without final weight`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
            vm.onAction(RecipeEditorAction.OnNameChange("Chili"))
            vm.onAction(RecipeEditorAction.OnCalculationModeChange(RecipeCalculationMode.Portion))
            vm.onAction(RecipeEditorAction.OnPortionsChange("4"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved.size).isEqualTo(1)
            assertThat(recipeRepo.saved.first().finalWeight).isEqualTo(null)
            assertThat(recipeRepo.saved.first().portions).isEqualTo(4)
        }

    @Test
    fun `weight mode persists recipe with final weight`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
            vm.onAction(RecipeEditorAction.OnNameChange("Chili"))
            vm.onAction(RecipeEditorAction.OnCalculationModeChange(RecipeCalculationMode.Weight))
            vm.onAction(RecipeEditorAction.OnFinalWeightChange("1800"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved.size).isEqualTo(1)
            assertThat(recipeRepo.saved.first().finalWeight).isEqualTo(FinalWeight(1800.0))
        }

    @Test
    fun `switching to portion mode clears final weight`() =
        runTest {
            val vm = viewModel()
            vm.onAction(RecipeEditorAction.OnCalculationModeChange(RecipeCalculationMode.Weight))
            vm.onAction(RecipeEditorAction.OnFinalWeightChange("500"))
            assertThat(vm.state.value.finalWeight).isEqualTo("500")
            assertThat(vm.state.value.calculationMode).isEqualTo(RecipeCalculationMode.Weight)

            vm.onAction(RecipeEditorAction.OnCalculationModeChange(RecipeCalculationMode.Portion))
            assertThat(vm.state.value.finalWeight).isEqualTo("")
            assertThat(vm.state.value.calculationMode).isEqualTo(RecipeCalculationMode.Portion)
        }

    @Test
    fun `typing a final weight switches to weight mode`() =
        runTest {
            val vm = viewModel()
            assertThat(vm.state.value.calculationMode).isEqualTo(RecipeCalculationMode.Portion)
            vm.onAction(RecipeEditorAction.OnFinalWeightChange("1800"))
            assertThat(vm.state.value.calculationMode).isEqualTo(RecipeCalculationMode.Weight)
        }

    @Test
    fun `weight mode without final weight is rejected`() =
        runTest {
            val vm = viewModel()
            vm.onAction(RecipeEditorAction.OnNameChange("Chili"))
            vm.onAction(RecipeEditorAction.OnCalculationModeChange(RecipeCalculationMode.Weight))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            vm.onAction(RecipeEditorAction.OnSaveClick)
            assertThat(vm.state.value.numberError != null).isTrue()
        }
}
