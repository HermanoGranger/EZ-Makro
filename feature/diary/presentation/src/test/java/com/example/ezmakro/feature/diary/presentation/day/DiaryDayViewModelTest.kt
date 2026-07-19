package com.example.ezmakro.feature.diary.presentation.day

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.usecase.diary.CopyYesterdayUseCase
import com.example.ezmakro.core.domain.usecase.diary.DeleteDiaryEntryUseCase
import com.example.ezmakro.feature.diary.presentation.fake.FakeDiaryRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryDayViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val date = LocalDate.of(2026, 7, 19)
    private lateinit var repository: FakeDiaryRepository
    private lateinit var viewModel: DiaryDayViewModel

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

    private fun entry(
        id: String,
        slot: MealSlot,
        foodId: String,
        amountGrams: Double,
    ): DiaryEntry =
        DiaryEntry(
            id = DiaryEntryId(id),
            slot = slot,
            date = date,
            food = food(foodId),
            recipe = null,
            amountGrams = amountGrams,
            portionCount = null,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDiaryRepository()
        viewModel =
            DiaryDayViewModel(
                repository = repository,
                copyYesterdayUseCase = CopyYesterdayUseCase(repository),
                deleteEntryUseCase = DeleteDiaryEntryUseCase(repository),
                savedStateHandle = SavedStateHandle(mapOf(DiaryDayViewModel.ARG_DATE to date.toString())),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observeByDate groups entries by slot and computes day total`() =
        runTest {
            repository.emitEntry(entry("a", MealSlot.BREAKFAST, "f1", 100.0))
            repository.emitEntry(entry("b", MealSlot.LUNCH, "f2", 100.0))

            val state = viewModel.state.value
            assertThat(state.breakfast.map { it.id }).containsExactly("a")
            assertThat(state.lunch.map { it.id }).containsExactly("b")
            assertThat(state.dinner).isEqualTo(emptyList())
            // 100g + 100g of food at 100 kcal/100g => 200 kcal total.
            assertThat(state.dayTotal?.kcal).isEqualTo("200")
        }

    @Test
    fun `OnAddFoodClick emits NavigateToAddFood with current date`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(DiaryDayAction.OnAddFoodClick)
                val event = awaitItem()
                assertThat(event).isEqualTo(DiaryDayEvent.NavigateToAddFood(date.toString()))
            }
        }

    @Test
    fun `OnDeleteEntry emits ConfirmDelete without deleting`() =
        runTest {
            repository.emitEntry(entry("a", MealSlot.BREAKFAST, "f1", 100.0))

            viewModel.events.test {
                viewModel.onAction(DiaryDayAction.OnDeleteEntry("a", MealSlot.BREAKFAST))
                val event = awaitItem()
                assertThat(event).isEqualTo(DiaryDayEvent.ConfirmDelete("a", MealSlot.BREAKFAST))
            }
            assertThat(repository.deletedIds()).containsExactly()
        }

    @Test
    fun `OnConfirmDelete deletes the entry`() =
        runTest {
            repository.emitEntry(entry("a", MealSlot.BREAKFAST, "f1", 100.0))

            viewModel.onAction(DiaryDayAction.OnConfirmDelete("a", MealSlot.BREAKFAST))

            assertThat(repository.deletedIds().map { it.value }).containsExactly("a")
        }

    @Test
    fun `OnHistoryClick emits NavigateToHistory`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(DiaryDayAction.OnHistoryClick)
                assertThat(awaitItem()).isEqualTo(DiaryDayEvent.NavigateToHistory)
            }
        }
}
