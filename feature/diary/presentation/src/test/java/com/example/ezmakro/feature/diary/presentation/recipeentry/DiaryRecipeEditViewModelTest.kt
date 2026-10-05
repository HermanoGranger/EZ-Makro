package com.example.ezmakro.feature.diary.presentation.recipeentry

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.usecase.diary.DiaryRecipeEditScope
import com.example.ezmakro.core.domain.usecase.diary.EditDiaryRecipeEntryUseCase
import com.example.ezmakro.core.domain.usecase.recipe.BuildRecipeFromDraftsUseCase
import com.example.ezmakro.feature.diary.presentation.fake.FakeDiaryRepository
import com.example.ezmakro.feature.diary.presentation.fake.FakeFoodRepository
import com.example.ezmakro.feature.diary.presentation.fake.FakeRecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

private const val KEY_PORTIONS = "diary_recipe_edit_portions"
private const val KEY_FINAL_WEIGHT = "diary_recipe_edit_final_weight"
private const val KEY_AMOUNT = "diary_recipe_edit_amount"

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryRecipeEditViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val date = LocalDate.of(2026, 7, 19)
    private val entryId = DiaryEntryId("entry-1")

    private lateinit var diaryRepo: FakeDiaryRepository
    private lateinit var recipeRepo: FakeRecipeRepository
    private lateinit var foodRepo: FakeFoodRepository

    private fun food(
        id: String,
        name: String,
        kcal: Double,
    ): FoodItem =
        FoodItem(
            id = FoodId(id),
            name = name,
            barcode = null,
            nutritionPer100g = NutritionValues(kcal, 10.0, 5.0, 2.0, 1.0),
            servingUnit = null,
            isUserCreated = true,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )

    private fun recipeWith(
        ingredients: List<RecipeIngredient>,
        portions: Int = 2,
        finalWeight: FinalWeight? = FinalWeight(200.0),
        lastModifiedAt: Long = 1000L,
    ): Recipe =
        Recipe(
            id = RecipeId("recipe-1"),
            name = "Bulgur Test",
            category = null,
            portions = portions,
            finalWeight = finalWeight,
            ingredients = ingredients,
            isFavorite = false,
            lastUsedAt = null,
            lastModifiedAt = lastModifiedAt,
        )

    private fun ingredient(
        foodItem: FoodItem,
        rawWeight: Double?,
    ): RecipeIngredient =
        RecipeIngredient(
            food = foodItem,
            recipeSnapshot = null,
            rawWeight = rawWeight?.let { RawWeight(it) },
            servingCount = null,
        )

    private fun entryWith(
        recipe: Recipe?,
        amountGrams: Double? = null,
        portionCount: Double? = 1.0,
    ): DiaryEntry =
        DiaryEntry(
            id = entryId,
            slot = MealSlot.LUNCH,
            date = date,
            food = null,
            recipe = recipe,
            amountGrams = amountGrams,
            portionCount = portionCount,
        )

    private val bulgur = food("f1", "Bulgur", 100.0)
    private val tomatenmark = food("f2", "Tomatenmark", 80.0)

    /** 100 g bulgur with 100 kcal/100 g -> 100 kcal total, 2 portions -> 50 kcal per portion. */
    private val baseRecipe = recipeWith(listOf(ingredient(bulgur, 100.0)))

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        diaryRepo = FakeDiaryRepository()
        recipeRepo = FakeRecipeRepository()
        foodRepo = FakeFoodRepository()
        foodRepo.emitFood(bulgur)
        foodRepo.emitFood(tomatenmark)
        recipeRepo.emitRecipe(baseRecipe)
        diaryRepo.emitEntry(entryWith(baseRecipe))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): DiaryRecipeEditViewModel =
        DiaryRecipeEditViewModel(
            entryId = entryId.value,
            savedStateHandle = savedStateHandle,
            diaryRepository = diaryRepo,
            recipeRepository = recipeRepo,
            foodRepository = foodRepo,
            buildRecipeFromDraftsUseCase = BuildRecipeFromDraftsUseCase(),
            editDiaryRecipeEntryUseCase = EditDiaryRecipeEntryUseCase(diaryRepo, recipeRepo),
        )

    @Test
    fun `seeds form state from the diary entry snapshot`() {
        val state = viewModel().state.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.entryId).isEqualTo(entryId.value)
        assertThat(state.recipeName).isEqualTo("Bulgur Test")
        assertThat(state.date).isEqualTo(date.toString())
        assertThat(state.sourceRecipeId).isEqualTo("recipe-1")
        assertThat(state.amountMode).isEqualTo(DiaryAmountMode.PORTIONS)
        assertThat(state.amountText).isEqualTo("1.0")
        assertThat(state.portions).isEqualTo("2")
        assertThat(state.finalWeight).isEqualTo("200.0")
        assertThat(state.ingredients).hasSize(1)
        assertThat(state.ingredients.first().foodName).isEqualTo("Bulgur")
        assertThat(state.ingredients.first().rawWeight).isEqualTo("100.0")
        assertThat(state.ingredientError).isNull()
    }

    @Test
    fun `seeds amount mode GRAMS when the entry logged grams`() {
        diaryRepo.emitEntry(entryWith(baseRecipe, amountGrams = 400.0, portionCount = null))

        val state = viewModel().state.value

        assertThat(state.amountMode).isEqualTo(DiaryAmountMode.GRAMS)
        assertThat(state.amountText).isEqualTo("400.0")
    }

    @Test
    fun `computes original and current consumed nutrition from the entry`() {
        val state = viewModel().state.value

        // 100 kcal total / 2 portions * 1 portion
        assertThat(state.totalNutrition).isEqualTo(NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0))
        assertThat(state.originalConsumedNutrition).isEqualTo(NutritionValues(50.0, 5.0, 2.5, 1.0, 0.5))
        assertThat(state.consumedNutrition).isEqualTo(NutritionValues(50.0, 5.0, 2.5, 1.0, 0.5))
    }

    @Test
    fun `raw weight change recomputes total and consumed nutrition live`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnIngredientRawWeightChange(0, "200"))

        val state = vm.state.value
        assertThat(state.ingredients.first().rawWeight).isEqualTo("200")
        assertThat(state.ingredientNutrition).hasSize(1)
        assertThat(state.totalNutrition).isEqualTo(NutritionValues(200.0, 20.0, 10.0, 4.0, 2.0))
        assertThat(state.consumedNutrition).isEqualTo(NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0))
        assertThat(state.ingredientError).isNull()
    }

    @Test
    fun `serving count change is applied to the draft`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnIngredientServingCountChange(0, "3"))

        assertThat(
            vm.state.value.ingredients
                .first()
                .servingCount,
        ).isEqualTo("3")
    }

    @Test
    fun `consumed nutrition in grams mode is derived from the measured final weight`() {
        val vm = viewModel()
        vm.onAction(DiaryRecipeEditAction.OnIngredientRawWeightChange(0, "200"))

        vm.onAction(DiaryRecipeEditAction.OnAmountModeChange(DiaryAmountMode.GRAMS))
        vm.onAction(DiaryRecipeEditAction.OnAmountChange("400"))

        // 200 kcal total / 200 g final weight = 100 kcal/100 g -> 400 g = 400 kcal
        assertThat(vm.state.value.consumedNutrition).isEqualTo(NutritionValues(400.0, 40.0, 20.0, 8.0, 4.0))
    }

    @Test
    fun `final weight change recalculates the per hundred gram basis`() {
        val vm = viewModel()
        vm.onAction(DiaryRecipeEditAction.OnIngredientRawWeightChange(0, "200"))
        vm.onAction(DiaryRecipeEditAction.OnAmountModeChange(DiaryAmountMode.GRAMS))
        vm.onAction(DiaryRecipeEditAction.OnAmountChange("400"))

        vm.onAction(DiaryRecipeEditAction.OnFinalWeightChange("400"))

        // 200 kcal total / 400 g final weight = 50 kcal/100 g -> 400 g = 200 kcal
        assertThat(vm.state.value.consumedNutrition).isEqualTo(NutritionValues(200.0, 20.0, 10.0, 4.0, 2.0))
    }

    @Test
    fun `portion change is persisted into the saved state handle`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)

        vm.onAction(DiaryRecipeEditAction.OnPortionsChange("4"))

        assertThat(vm.state.value.portions).isEqualTo("4")
        assertThat(handle.get<String>(KEY_PORTIONS)).isEqualTo("4")
    }

    @Test
    fun `restores portions final weight and amount from the saved state handle`() {
        val handle =
            SavedStateHandle(
                mapOf(
                    KEY_PORTIONS to "6",
                    KEY_FINAL_WEIGHT to "300",
                    KEY_AMOUNT to "2",
                ),
            )

        val state = viewModel(handle).state.value

        assertThat(state.portions).isEqualTo("6")
        assertThat(state.finalWeight).isEqualTo("300")
        assertThat(state.amountText).isEqualTo("2")
    }

    @Test
    fun `does not crash when the nutrition sources emit after the entry was seeded`() {
        diaryRepo.emitEntry(entryWith(baseRecipe))

        val state = viewModel().state.value

        assertThat(state.ingredients).hasSize(1)
        assertThat(state.totalNutrition.kcal).isEqualTo(100.0)
    }

    @Test
    fun `adding an ingredient appends a draft without a weight`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnAddIngredientClick)
        assertThat(vm.state.value.isAddPickerOpen).isTrue()
        assertThat(vm.state.value.replaceIngredientIndex).isNull()

        vm.onAction(DiaryRecipeEditAction.OnAddIngredientWithData("f2", "Tomatenmark", null))

        val state = vm.state.value
        assertThat(state.ingredients).hasSize(2)
        assertThat(state.ingredients.last().foodName).isEqualTo("Tomatenmark")
        assertThat(state.replaceIngredientIndex).isNull()
        // A draft without an amount is not buildable yet.
        assertThat(state.ingredientError).isNotNull()
    }

    @Test
    fun `adding an ingredient after replace swaps it at the same index`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnReplaceIngredient(0))
        assertThat(vm.state.value.isAddPickerOpen).isTrue()
        assertThat(vm.state.value.replaceIngredientIndex).isEqualTo(0)

        vm.onAction(DiaryRecipeEditAction.OnAddIngredientWithData("f2", "Tomatenmark", null))

        val state = vm.state.value
        assertThat(state.ingredients).hasSize(1)
        assertThat(state.ingredients.first().foodName).isEqualTo("Tomatenmark")
        assertThat(state.replaceIngredientIndex).isNull()
    }

    @Test
    fun `a new add after replace appends instead of replacing`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnReplaceIngredient(0))
        vm.onAction(DiaryRecipeEditAction.OnAddIngredientClick)
        vm.onAction(DiaryRecipeEditAction.OnAddIngredientWithData("f2", "Tomatenmark", null))

        assertThat(
            vm.state.value.ingredients
                .map { it.foodName },
        ).isEqualTo(listOf("Bulgur", "Tomatenmark"))
    }

    @Test
    fun `removing an ingredient drops it and recomputes`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnRemoveIngredient(0))

        val state = vm.state.value
        assertThat(state.ingredients).isEmpty()
        assertThat(state.totalNutrition).isEqualTo(NutritionValues.ZERO)
    }

    @Test
    fun `ingredient click toggles the expanded index`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnIngredientClick(0))
        assertThat(vm.state.value.expandedIngredientIndex).isEqualTo(0)

        vm.onAction(DiaryRecipeEditAction.OnIngredientClick(0))
        assertThat(vm.state.value.expandedIngredientIndex).isNull()
    }

    @Test
    fun `unknown food surfaces an ingredient error and clears the total`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnAddIngredientWithData("does-not-exist", "Phantom", null))

        val state = vm.state.value
        assertThat(state.ingredientError).isNotNull()
        assertThat(state.totalNutrition).isEqualTo(NutritionValues.ZERO)
    }

    @Test
    fun `save without a valid amount is rejected`() {
        val vm = viewModel()
        vm.onAction(DiaryRecipeEditAction.OnAmountChange(""))

        vm.onAction(DiaryRecipeEditAction.OnSaveClick)

        val state = vm.state.value
        assertThat(state.amountError).isNotNull()
        assertThat(state.isSaveScopeDialogOpen).isFalse()
    }

    @Test
    fun `save without ingredients is rejected`() {
        val vm = viewModel()
        vm.onAction(DiaryRecipeEditAction.OnRemoveIngredient(0))

        vm.onAction(DiaryRecipeEditAction.OnSaveClick)

        val state = vm.state.value
        assertThat(state.ingredientError).isNotNull()
        assertThat(state.isSaveScopeDialogOpen).isFalse()
    }

    @Test
    fun `valid save opens the scope dialog`() {
        val vm = viewModel()

        vm.onAction(DiaryRecipeEditAction.OnSaveClick)

        val state = vm.state.value
        assertThat(state.amountError).isNull()
        assertThat(state.isSaveScopeDialogOpen).isTrue()
    }

    @Test
    fun `dismissing the scope dialog closes it`() {
        val vm = viewModel()
        vm.onAction(DiaryRecipeEditAction.OnSaveClick)

        vm.onAction(DiaryRecipeEditAction.OnDismissSaveScopeDialog)

        assertThat(vm.state.value.isSaveScopeDialogOpen).isFalse()
    }

    @Test
    fun `confirm save with ENTRY_ONLY writes only the diary entry and marks it overridden`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.events.test {
                vm.onAction(DiaryRecipeEditAction.OnSaveClick)
                vm.onAction(DiaryRecipeEditAction.OnConfirmSave(DiaryRecipeEditScope.ENTRY_ONLY))

                assertThat(awaitItem()).isEqualTo(DiaryRecipeEditEvent.Saved)
            }

            assertThat(diaryRepo.savedCount()).isEqualTo(1)
            assertThat(
                diaryRepo.entries.value
                    .first()
                    .isRecipeOverridden,
            ).isTrue()
            assertThat(recipeRepo.saved).isEmpty()
        }

    @Test
    fun `confirm save with RECIPE_AND_ENTRY also writes the recipe`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.onAction(DiaryRecipeEditAction.OnIngredientRawWeightChange(0, "300"))

            vm.events.test {
                vm.onAction(DiaryRecipeEditAction.OnSaveClick)
                vm.onAction(DiaryRecipeEditAction.OnConfirmSave(DiaryRecipeEditScope.RECIPE_AND_ENTRY))

                assertThat(awaitItem()).isEqualTo(DiaryRecipeEditEvent.Saved)
            }

            assertThat(recipeRepo.saved).hasSize(1)
            val written = recipeRepo.saved.first()
            assertThat(written.ingredients.first().rawWeight).isEqualTo(RawWeight(300.0))
            assertThat(written.portions).isEqualTo(2)
            assertThat(
                diaryRepo.entries.value
                    .first()
                    .isRecipeOverridden,
            ).isFalse()
        }

    @Test
    fun `dismissing the picker closes it`() {
        val vm = viewModel()
        vm.onAction(DiaryRecipeEditAction.OnAddIngredientClick)

        vm.onAction(DiaryRecipeEditAction.OnDismissPicker)

        assertThat(vm.state.value.isAddPickerOpen).isFalse()
    }

    @Test
    fun `picking a recipe as ingredient is rejected with a message`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.events.test {
                vm.onAction(DiaryRecipeEditAction.OnPickerRecipeNotAllowed)

                val event = awaitItem()
                assertThat(event).isEqualTo(
                    DiaryRecipeEditEvent.ShowMessage(
                        com.example.ezmakro.core.presentation.uitext.UiText.StringResource(
                            com.example.ezmakro.feature.diary.presentation.R.string
                                .diary_recipe_edit_picker_recipe_not_allowed,
                        ),
                    ),
                )
            }
            assertThat(vm.state.value.ingredients).hasSize(1)
        }

    @Test
    fun `back click emits navigate back`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.events.test {
                vm.onAction(DiaryRecipeEditAction.OnBackClick)

                assertThat(awaitItem()).isEqualTo(DiaryRecipeEditEvent.NavigateBack)
            }
        }

    @Test
    fun `a changed source recipe is flagged as outdated`() {
        val vm = viewModel()

        recipeRepo.emitRecipe(baseRecipe.copy(lastModifiedAt = 9999L))

        val state = vm.state.value
        assertThat(state.canUpdateRecipe).isTrue()
        assertThat(state.isSourceRecipeOutdated).isTrue()
    }

    @Test
    fun `an unchanged source recipe is not flagged as outdated`() {
        val state = viewModel().state.value

        assertThat(state.canUpdateRecipe).isTrue()
        assertThat(state.isSourceRecipeOutdated).isFalse()
    }

    @Test
    fun `a deleted source recipe clears the update option`() {
        recipeRepo.emitRecipe(baseRecipe)
        val vm = viewModel()

        recipeRepo.clearRecipes()

        assertThat(vm.state.value.canUpdateRecipe).isFalse()
    }
}
