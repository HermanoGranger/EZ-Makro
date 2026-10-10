package com.example.ezmakro.feature.recipes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.RecipeSnapshot
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.usecase.recipe.BuildRecipeFromDraftsUseCase
import com.example.ezmakro.feature.recipes.presentation.fake.FakeFoodRepository
import com.example.ezmakro.feature.recipes.presentation.fake.FakeRecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
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
        foodRepo: FakeFoodRepository = FakeFoodRepository(),
        recipeRepo: FakeRecipeRepository = FakeRecipeRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): RecipeEditorViewModel =
        RecipeEditorViewModel(
            recipeId = recipeId,
            recipeRepository = recipeRepo,
            foodRepository = foodRepo,
            buildRecipeFromDraftsUseCase = BuildRecipeFromDraftsUseCase(),
            savedStateHandle = savedStateHandle,
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
            ).isEqualTo("200")
        }

    @Test
    fun `portions are persisted in recipe`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
            vm.onAction(RecipeEditorAction.OnNameChange("Chili"))
            vm.onAction(RecipeEditorAction.OnPortionsChange("4"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved.size).isEqualTo(1)
            assertThat(recipeRepo.saved.first().finalWeight).isEqualTo(null)
            assertThat(recipeRepo.saved.first().portions).isEqualTo(4)
        }

    @Test
    fun `final weight is persisted when provided`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
            vm.onAction(RecipeEditorAction.OnNameChange("Chili"))
            vm.onAction(RecipeEditorAction.OnFinalWeightChange("1800"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved.size).isEqualTo(1)
            assertThat(recipeRepo.saved.first().finalWeight).isEqualTo(FinalWeight(1800.0))
        }

    @Test
    fun `ingredient toggle expands and collapses`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple"), FakeFoodRepository.food("f2", "Banane")))
            val vm = viewModel(foodRepo = foodRepo)
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f2"))

            assertThat(vm.state.value.expandedIngredientIndex).isNull()

            vm.onAction(RecipeEditorAction.OnIngredientClick(0))
            assertThat(vm.state.value.expandedIngredientIndex).isEqualTo(0)

            vm.onAction(RecipeEditorAction.OnIngredientClick(0))
            assertThat(vm.state.value.expandedIngredientIndex).isNull()
        }

    @Test
    fun `removing ingredient clears expanded index`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple"), FakeFoodRepository.food("f2", "Banane")))
            val vm = viewModel(foodRepo = foodRepo)
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f2"))
            vm.onAction(RecipeEditorAction.OnIngredientClick(0))
            assertThat(vm.state.value.expandedIngredientIndex).isEqualTo(0)

            vm.onAction(RecipeEditorAction.OnRemoveIngredientClick(0))
            assertThat(vm.state.value.expandedIngredientIndex).isNull()
        }

    @Test
    fun `both rawWeight and servingCount can be set on ingredient`() =
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
            vm.onAction(RecipeEditorAction.OnIngredientServingCountChange(0, "2"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved.size).isEqualTo(1)
            val savedRecipe = recipeRepo.saved[0]
            val savedIng = savedRecipe.ingredients[0]
            assertThat(savedIng.rawWeight).isEqualTo(RawWeight(100.0))
            assertThat(savedIng.servingCount).isEqualTo(2.0)
            assertThat(savedIng.effectiveRawWeight()).isEqualTo(RawWeight(100.0))
        }

    @Test
    fun `online ingredient not in availableFoods is added with provided name`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val vm = viewModel(foodRepo = foodRepo)
            vm.onAction(
                RecipeEditorAction.OnAddIngredientClick(
                    foodId = "online-off-id",
                    foodName = "Alpro Hafer",
                    servingLabel = "200 ml",
                ),
            )
            assertThat(vm.state.value.ingredients.size).isEqualTo(1)
            val online = vm.state.value.ingredients[0]
            assertThat(online.foodId).isEqualTo("online-off-id")
            assertThat(online.foodName).isEqualTo("Alpro Hafer")
            assertThat(online.servingLabel).isEqualTo("200 ml")
        }

    @Test
    fun `save rejects ingredient with zero raw weight`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
            vm.onAction(RecipeEditorAction.OnNameChange("Müsli"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "0"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(vm.state.value.ingredientError != null).isTrue()
            assertThat(recipeRepo.saved).isEmpty()
        }

    @Test
    fun `save rejects ingredient with zero serving count`() =
        runTest {
            val egg =
                FakeFoodRepository.food("f1", "Ei").copy(
                    servingUnit = ServingUnit("1 Ei", 50.0),
                )
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(egg))
            val recipeRepo = FakeRecipeRepository()
            val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
            vm.onAction(RecipeEditorAction.OnNameChange("Eierpfanne"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientServingCountChange(0, "0"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(vm.state.value.ingredientError != null).isTrue()
            assertThat(recipeRepo.saved).isEmpty()
        }

    @Test
    fun `two different online ingredients add both with correct names`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(emptyList())
            val vm = viewModel(foodRepo = foodRepo)
            vm.onAction(
                RecipeEditorAction.OnAddIngredientClick(
                    foodId = "online-1",
                    foodName = "Alpro Hafer",
                    servingLabel = "200 ml",
                ),
            )
            vm.onAction(
                RecipeEditorAction.OnAddIngredientClick(
                    foodId = "online-2",
                    foodName = "Oatly Barista",
                    servingLabel = "250 ml",
                ),
            )
            assertThat(vm.state.value.ingredients.size).isEqualTo(2)
            val first = vm.state.value.ingredients[0]
            val second = vm.state.value.ingredients[1]
            assertThat(first.foodName).isEqualTo("Alpro Hafer")
            assertThat(second.foodName).isEqualTo("Oatly Barista")
        }

    private fun appleFood(): FoodItem =
        FakeFoodRepository.food("f1", "Apple").copy(
            nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 20.0, 2.0),
        )

    private fun bananaFood(): FoodItem =
        FakeFoodRepository.food("f2", "Banane").copy(
            nutritionPer100g = NutritionValues(200.0, 5.0, 1.0, 40.0, 3.0),
        )

    @Test
    fun `ingredientNutrition is computed for 100g ingredient`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(appleFood()))
            val vm = viewModel(foodRepo = foodRepo)
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))

            assertThat(vm.state.value.ingredientNutrition.size).isEqualTo(1)
            assertThat(vm.state.value.ingredientNutrition[0])
                .isEqualTo(NutritionValues(100.0, 10.0, 5.0, 20.0, 2.0))
        }

    @Test
    fun `ingredientNutrition is zero when no amount is set`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(appleFood()))
            val vm = viewModel(foodRepo = foodRepo)
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))

            assertThat(vm.state.value.ingredientNutrition)
                .isEqualTo(listOf(NutritionValues.ZERO))
            assertThat(vm.state.value.totalNutrition).isEqualTo(NutritionValues.ZERO)
        }

    @Test
    fun `totalNutrition sums multiple ingredients`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(appleFood(), bananaFood()))
            val vm = viewModel(foodRepo = foodRepo)
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f2"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(1, "50"))

            assertThat(vm.state.value.totalNutrition)
                .isEqualTo(NutritionValues(200.0, 12.5, 5.5, 40.0, 3.5))
        }

    @Test
    fun `ingredientNutrition collapses indices after removal`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(appleFood(), bananaFood()))
            val vm = viewModel(foodRepo = foodRepo)
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f2"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(1, "50"))

            vm.onAction(RecipeEditorAction.OnRemoveIngredientClick(0))

            assertThat(vm.state.value.ingredients.size).isEqualTo(1)
            assertThat(vm.state.value.ingredientNutrition.size).isEqualTo(1)
            assertThat(vm.state.value.ingredientNutrition[0])
                .isEqualTo(NutritionValues(100.0, 2.5, 0.5, 20.0, 1.5))
        }

    @Test
    fun `ingredient added via picker survives a recipes table emission`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Mehl"), FakeFoodRepository.food("f2", "Banane")))
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(brotRecipe())
            val vm = viewModel(recipeId = "r1", foodRepo = foodRepo, recipeRepo = recipeRepo)
            assertThat(vm.state.value.ingredients.size).isEqualTo(1)

            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f2"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(1, "120"))
            assertThat(vm.state.value.ingredients.size).isEqualTo(2)

            recipeRepo.markUsed(RecipeId("r1"))

            assertThat(vm.state.value.ingredients.size).isEqualTo(2)
            val added = vm.state.value.ingredients[1]
            assertThat(added.foodName).isEqualTo("Banane")
            assertThat(added.rawWeight).isEqualTo("120")
        }

    @Test
    fun `typed portions are not reset by a recipes table emission`() =
        runTest {
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(brotRecipe())
            val vm = viewModel(recipeId = "r1", recipeRepo = recipeRepo)
            assertThat(vm.state.value.portions).isEqualTo("3")

            vm.onAction(RecipeEditorAction.OnPortionsChange("6"))
            recipeRepo.markUsed(RecipeId("r1"))

            assertThat(vm.state.value.portions).isEqualTo("6")
        }

    private fun brotRecipe(): Recipe =
        Recipe(
            id = RecipeId("r1"),
            name = "Brot",
            ingredients =
                listOf(
                    RecipeIngredient(
                        food = FakeFoodRepository.food("f1", "Mehl"),
                        rawWeight = RawWeight(200.0),
                        servingCount = null,
                    ),
                ),
            finalWeight = FinalWeight(300.0),
            portions = 3,
            category = "Backen",
        )

    private fun snapshotOf(
        id: String = "r2",
        name: String = "Unter",
        lastModifiedAt: Long = 1_234L,
    ): RecipeSnapshot =
        RecipeSnapshot(
            recipeId = RecipeId(id),
            recipeName = name,
            nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 20.0, 2.0),
            originalLastModifiedAt = lastModifiedAt,
            portions = 2,
            gramsPerPortion = 100.0,
        )

    @Test
    fun `saving parent with deleted sub-recipe ingredient uses the snapshot`() =
        runTest {
            val snapshot = snapshotOf()
            val subRecipe =
                Recipe(
                    id = RecipeId("r2"),
                    name = "Unter",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1", "Hafer"),
                                rawWeight = RawWeight(50.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 2,
                    lastModifiedAt = snapshot.originalLastModifiedAt!!,
                )
            val parent =
                Recipe(
                    id = RecipeId("r1"),
                    name = "Parent",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = null,
                                recipeSnapshot = snapshot,
                                rawWeight = RawWeight(150.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 1,
                )
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(subRecipe)
            recipeRepo.emitRecipe(parent)
            val vm = viewModel(recipeId = "r1", recipeRepo = recipeRepo)

            assertThat(vm.state.value.ingredients.size).isEqualTo(1)
            val draft = vm.state.value.ingredients[0]
            assertThat(draft.recipeId).isEqualTo("r2")
            assertThat(draft.rawWeight).isEqualTo("150")

            recipeRepo.delete(RecipeId("r2"))

            assertThat(
                vm.state.value.ingredients[0]
                    .snapshotChanged,
            ).isTrue()
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved.size).isEqualTo(1)
            val savedIngredient = recipeRepo.saved[0].ingredients[0]
            assertThat(savedIngredient.recipeSnapshot).isEqualTo(snapshot)
            assertThat(savedIngredient.rawWeight).isEqualTo(RawWeight(150.0))
        }

    @Test
    fun `saving parent with deleted sub-recipe and no snapshot shows error`() =
        runTest {
            val subRecipe =
                Recipe(
                    id = RecipeId("r2"),
                    name = "Unter",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1", "Hafer"),
                                rawWeight = RawWeight(50.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 2,
                )
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(subRecipe)
            val vm = viewModel(recipeId = null, recipeRepo = recipeRepo)
            vm.onAction(RecipeEditorAction.OnNameChange("Neu"))
            vm.onAction(RecipeEditorAction.OnAddRecipeIngredientClick("r2"))
            vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))

            recipeRepo.delete(RecipeId("r2"))
            vm.onAction(RecipeEditorAction.OnSaveClick)

            assertThat(recipeRepo.saved).isEmpty()
            assertThat(vm.state.value.ingredientError != null).isTrue()
        }

    @Test
    fun `snapshot ingredient gets generic serving label`() =
        runTest {
            val parent =
                Recipe(
                    id = RecipeId("r1"),
                    name = "Parent",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = null,
                                recipeSnapshot = snapshotOf(),
                                rawWeight = RawWeight(150.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 1,
                )
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(parent)
            val vm = viewModel(recipeId = "r1", recipeRepo = recipeRepo)

            val draft = vm.state.value.ingredients[0]
            assertThat(draft.recipeId).isEqualTo("r2")
            assertThat(draft.servingLabel).isEqualTo("Portionen")
        }

    @Test
    fun `freshly added recipe draft gets generic serving label`() =
        runTest {
            val subRecipe =
                Recipe(
                    id = RecipeId("r2"),
                    name = "Unter",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1", "Hafer"),
                                rawWeight = RawWeight(50.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 2,
                )
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(subRecipe)
            val vm = viewModel(recipeId = null, recipeRepo = recipeRepo)

            vm.onAction(RecipeEditorAction.OnAddRecipeIngredientClick("r2", rawWeight = "100"))

            val draft = vm.state.value.ingredients[0]
            assertThat(draft.recipeId).isEqualTo("r2")
            assertThat(draft.servingLabel).isEqualTo("Portionen")
        }

    @Test
    fun `ingredient drafts have unique and stable ids`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(
                Recipe(
                    id = RecipeId("r2"),
                    name = "Unter",
                    ingredients =
                        listOf(
                            RecipeIngredient(
                                food = FakeFoodRepository.food("f1", "Apple"),
                                rawWeight = RawWeight(50.0),
                                servingCount = null,
                            ),
                        ),
                    finalWeight = null,
                    portions = 2,
                ),
            )
            val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
            vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            vm.onAction(RecipeEditorAction.OnAddRecipeIngredientClick("r2"))

            assertThat(vm.state.value.ingredients.size).isEqualTo(2)
            val firstId =
                vm.state.value.ingredients[0]
                    .id
            val secondId =
                vm.state.value.ingredients[1]
                    .id
            assertThat(firstId).isNotEqualTo(secondId)

            vm.onAction(RecipeEditorAction.OnRemoveIngredientClick(0))

            assertThat(vm.state.value.ingredients.size).isEqualTo(1)
            assertThat(
                vm.state.value.ingredients[0]
                    .id,
            ).isEqualTo(secondId)
        }

    @Test
    fun `save reflects name edits made before executeSave runs`() =
        runTest {
            val standardDispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(standardDispatcher)
            try {
                val foodRepo = FakeFoodRepository()
                foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
                val recipeRepo = FakeRecipeRepository()
                val vm = viewModel(foodRepo = foodRepo, recipeRepo = recipeRepo)
                testScheduler.runCurrent()

                vm.onAction(RecipeEditorAction.OnNameChange("Müsli"))
                vm.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
                vm.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))

                vm.onAction(RecipeEditorAction.OnSaveClick)
                CoroutineScope(standardDispatcher).launch {
                    vm.onAction(RecipeEditorAction.OnNameChange("Müsli Neu"))
                }
                testScheduler.runCurrent()

                assertThat(recipeRepo.saved.size).isEqualTo(1)
                assertThat(recipeRepo.saved.first().name).isEqualTo("Müsli Neu")
            } finally {
                Dispatchers.setMain(testDispatcher)
            }
        }

    @Test
    fun `process death restores ingredients with quantities and stable ids`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val handle = SavedStateHandle()
            val first = viewModel(foodRepo = foodRepo, savedStateHandle = handle)
            first.onAction(RecipeEditorAction.OnNameChange("Müsli"))
            first.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            first.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "100"))
            first.onAction(RecipeEditorAction.OnIngredientServingCountChange(0, "2"))

            val restored = viewModel(foodRepo = foodRepo, savedStateHandle = handle)

            assertThat(restored.state.value.name).isEqualTo("Müsli")
            assertThat(restored.state.value.ingredients.size).isEqualTo(1)
            val draft = restored.state.value.ingredients[0]
            assertThat(draft.foodId).isEqualTo("f1")
            assertThat(draft.foodName).isEqualTo("Apple")
            assertThat(draft.rawWeight).isEqualTo("100")
            assertThat(draft.servingCount).isEqualTo("2")
            assertThat(draft.id).isEqualTo(
                first.state.value.ingredients[0]
                    .id,
            )
        }

    @Test
    fun `adding and removing ingredients after restore keeps unique stable ids`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(
                listOf(
                    FakeFoodRepository.food("f1", "Apple"),
                    FakeFoodRepository.food("f2", "Banane"),
                ),
            )
            val handle = SavedStateHandle()
            val first = viewModel(foodRepo = foodRepo, savedStateHandle = handle)
            first.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            first.onAction(RecipeEditorAction.OnIngredientRawWeightChange(0, "50"))

            val restored = viewModel(foodRepo = foodRepo, savedStateHandle = handle)
            val restoredId =
                restored.state.value.ingredients[0]
                    .id
            restored.onAction(RecipeEditorAction.OnAddIngredientClick("f2"))
            restored.onAction(RecipeEditorAction.OnIngredientRawWeightChange(1, "120"))

            assertThat(restored.state.value.ingredients.size).isEqualTo(2)
            val ids =
                restored.state.value.ingredients
                    .map { it.id }
            assertThat(ids.distinct().size).isEqualTo(2)
            assertThat(ids[0]).isEqualTo(restoredId)

            restored.onAction(RecipeEditorAction.OnRemoveIngredientClick(0))
            assertThat(restored.state.value.ingredients.size).isEqualTo(1)
            assertThat(
                restored.state.value.ingredients[0]
                    .id,
            ).isEqualTo(ids[1])
        }

    @Test
    fun `restored ingredients are not overwritten by reseeding existing recipe`() =
        runTest {
            val foodRepo = FakeFoodRepository()
            foodRepo.emitFoods(listOf(FakeFoodRepository.food("f1", "Apple")))
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(brotRecipe())
            val handle = SavedStateHandle()
            val first =
                viewModel(
                    recipeId = "r1",
                    foodRepo = foodRepo,
                    recipeRepo = recipeRepo,
                    savedStateHandle = handle,
                )
            first.onAction(RecipeEditorAction.OnAddIngredientClick("f1"))
            first.onAction(RecipeEditorAction.OnIngredientRawWeightChange(1, "120"))

            val restored =
                viewModel(
                    recipeId = "r1",
                    foodRepo = foodRepo,
                    recipeRepo = recipeRepo,
                    savedStateHandle = handle,
                )

            assertThat(restored.state.value.ingredients.size).isEqualTo(2)
            assertThat(
                restored.state.value.ingredients[0]
                    .foodName,
            ).isEqualTo("Mehl")
            assertThat(
                restored.state.value.ingredients[0]
                    .rawWeight,
            ).isEqualTo("200")
            assertThat(
                restored.state.value.ingredients[1]
                    .foodName,
            ).isEqualTo("Apple")
            assertThat(
                restored.state.value.ingredients[1]
                    .rawWeight,
            ).isEqualTo("120")
        }

    @Test
    fun `restored favorite survives process death when editing existing recipe`() =
        runTest {
            val recipeRepo = FakeRecipeRepository()
            recipeRepo.emitRecipe(brotRecipe())
            val handle = SavedStateHandle()
            val first = viewModel(recipeId = "r1", recipeRepo = recipeRepo, savedStateHandle = handle)
            first.onAction(RecipeEditorAction.OnToggleFavorite)

            val restored = viewModel(recipeId = "r1", recipeRepo = recipeRepo, savedStateHandle = handle)

            assertThat(restored.state.value.isFavorite).isTrue()
        }
}
