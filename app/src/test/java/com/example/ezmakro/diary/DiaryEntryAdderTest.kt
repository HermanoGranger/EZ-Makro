package com.example.ezmakro.diary

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.usecase.diary.AddFoodToDiaryUseCase
import com.example.ezmakro.core.domain.usecase.diary.AddRecipeToDiaryUseCase
import com.example.ezmakro.diary.fake.FakeDiaryRepository
import com.example.ezmakro.diary.fake.FakeFoodRepository
import com.example.ezmakro.diary.fake.FakeRecipeRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DiaryEntryAdderTest {
    private val date = LocalDate.of(2026, 7, 19)
    private val slot = MealSlot.LUNCH

    private val recipe =
        Recipe(
            id = RecipeId("r1"),
            name = "Bowl",
            ingredients =
                listOf(
                    RecipeIngredient(
                        food = FakeFoodRepository.food("f1"),
                        recipeSnapshot = null,
                        rawWeight = RawWeight(100.0),
                        servingCount = null,
                    ),
                ),
            finalWeight = FinalWeight(250.0),
            portions = 2,
        )

    private fun adder(
        foods: FakeFoodRepository = FakeFoodRepository(),
        recipes: FakeRecipeRepository = FakeRecipeRepository(),
        diary: FakeDiaryRepository = FakeDiaryRepository(),
    ) = DiaryEntryAdder(
        foodRepository = foods,
        recipeRepository = recipes,
        addFoodToDiary = AddFoodToDiaryUseCase(diary),
        addRecipeToDiary = AddRecipeToDiaryUseCase(diary),
    ) to diary

    @Test
    fun `addFood writes the entry and reports Added`() =
        runBlocking {
            val foods = FakeFoodRepository()
            foods.emitFood(FakeFoodRepository.food("f1"))
            val (adder, diary) = adder(foods = foods)

            val outcome = adder.addFood("f1", date, slot, amount = 150.0, isGrams = true)

            assertThat(outcome).isEqualTo(DiaryEntryAdder.AddToDiaryOutcome.Added)
            assertThat(diary.saved).hasSize(1)
            assertThat(diary.saved.single().amountGrams).isEqualTo(150.0)
            assertThat(diary.saved.single().portionCount).isNull()
        }

    @Test
    fun `addFood surfaces a failed write as Failed`() =
        runBlocking {
            val foods = FakeFoodRepository()
            foods.emitFood(FakeFoodRepository.food("f1"))
            val (adder, diary) = adder(foods = foods)
            diary.saveResult = Result.Error(DataError.Local.UNKNOWN)

            val outcome = adder.addFood("f1", date, slot, amount = 100.0, isGrams = true)

            assertThat(outcome).isEqualTo(DiaryEntryAdder.AddToDiaryOutcome.Failed)
            assertThat(diary.saved).isEmpty()
        }

    @Test
    fun `addFood resolves a session-only item and persists it`() =
        runBlocking {
            val foods = FakeFoodRepository()
            foods.sessionItem = FakeFoodRepository.food("online", name = "Online")
            val (adder, diary) = adder(foods = foods)

            val outcome = adder.addFood("online", date, slot, amount = 100.0, isGrams = true)

            assertThat(outcome).isEqualTo(DiaryEntryAdder.AddToDiaryOutcome.Added)
            assertThat(foods.cachedRemote).hasSize(1)
            assertThat(diary.saved).hasSize(1)
        }

    @Test
    fun `addFood reports NotFound when the item cannot be resolved`() =
        runBlocking {
            val (adder, diary) = adder()

            val outcome = adder.addFood("missing", date, slot, amount = 100.0, isGrams = true)

            assertThat(outcome).isEqualTo(DiaryEntryAdder.AddToDiaryOutcome.NotFound)
            assertThat(diary.saved).isEmpty()
        }

    @Test
    fun `addRecipe surfaces a failed write as Failed`() =
        runBlocking {
            val recipes = FakeRecipeRepository()
            recipes.emitRecipe(recipe)
            val (adder, diary) = adder(recipes = recipes)
            diary.saveResult = Result.Error(DataError.Local.UNKNOWN)

            val outcome = adder.addRecipe("r1", date, slot, amount = 1.0, isGrams = false)

            assertThat(outcome).isEqualTo(DiaryEntryAdder.AddToDiaryOutcome.Failed)
            assertThat(diary.saved).isEmpty()
        }

    @Test
    fun `addRecipe reports NotFound when the recipe cannot be resolved`() =
        runBlocking {
            val (adder, _) = adder()

            val outcome = adder.addRecipe("missing", date, slot, amount = 1.0, isGrams = false)

            assertThat(outcome).isEqualTo(DiaryEntryAdder.AddToDiaryOutcome.NotFound)
        }

    @Test
    fun `addFromPicker dispatches to the recipe branch`() =
        runBlocking {
            val foods = FakeFoodRepository()
            val recipes = FakeRecipeRepository()
            recipes.emitRecipe(recipe)
            val (adder, diary) = adder(foods = foods, recipes = recipes)

            val outcome =
                adder.addFromPicker(
                    itemId = "r1",
                    isRecipe = true,
                    date = date,
                    slot = slot,
                    amount = 2.0,
                    isGrams = false,
                )

            assertThat(outcome).isEqualTo(DiaryEntryAdder.AddToDiaryOutcome.Added)
            assertThat(diary.saved.single().recipe).isNotNull()
            assertThat(diary.saved.single().food).isNull()
        }
}
