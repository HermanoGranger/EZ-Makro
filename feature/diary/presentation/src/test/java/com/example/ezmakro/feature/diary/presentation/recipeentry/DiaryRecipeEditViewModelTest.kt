package com.example.ezmakro.feature.diary.presentation.recipeentry

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.domain.model.RecipeSnapshot
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.FinalWeight
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryRecipeEditViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val date = LocalDate.of(2026, 7, 19)
    private lateinit var diaryRepo: FakeDiaryRepository
    private lateinit var recipeRepo: FakeRecipeRepository
    private lateinit var foodRepo: FakeFoodRepository

    private fun food(id: String, kcal: Double = 100.0): FoodItem =
        FoodItem(
            id = FoodId(id),
            name = "Food$id",
            barcode = null,
            nutritionPer100g = NutritionValues(kcal, 10.0, 5.0, 2.0, 1.0),
            servingUnit = null,
            isUserCreated = true,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )

    private fun simpleRecipe(id: String): Recipe {
        val f = food("f1", kcal = 100.0)
        val ingredient = RecipeIngredient(food = f, recipeSnapshot = null, rawWeight = RawWeight(100.0), servingCount = null)
        return Recipe(
            id = RecipeId(id),
            name = "R$id",
            category = null,
            portions = 2,
            finalWeight = FinalWeight(200.0),
            ingredients = listOf(ingredient),
            isFavorite = false,
            lastUsedAt = null,
            lastModifiedAt = 1000L,
        )
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        diaryRepo = FakeDiaryRepository()
        recipeRepo = FakeRecipeRepository()
        foodRepo = FakeFoodRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(entryId: String, savedStateHandle: SavedStateHandle = SavedStateHandle()): DiaryRecipeEditViewModel =
        DiaryRecipeEditViewModel(
            entryId = entryId,
            savedStateHandle = savedStateHandle,
            diaryRepository = diaryRepo,
            recipeRepository = recipeRepo,
            foodRepository = foodRepo,
            buildRecipeFromDraftsUseCase = BuildRecipeFromDraftsUseCase(),
            editDiaryRecipeEntryUseCase = EditDiaryRecipeEntryUseCase(diaryRepo, recipeRepo),
        )
}
