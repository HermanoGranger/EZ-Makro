package com.example.ezmakro.feature.diary.presentation.history

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.single
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.usecase.diary.GetHistoryUseCase
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
class HistoryViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.now()
    private lateinit var repository: FakeDiaryRepository

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
        date: LocalDate = today,
        slot: MealSlot = MealSlot.BREAKFAST,
        foodId: String = "f1",
        amountGrams: Double = 100.0,
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

    private fun viewModel(repository: FakeDiaryRepository): HistoryViewModel =
        HistoryViewModel(getHistory = GetHistoryUseCase(repository))

    private fun daysOf(vm: HistoryViewModel): List<HistoryDayUi> = vm.state.value.days

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDiaryRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `days are grouped per date newest first with summed kcal`() =
        runTest {
            repository.emitEntry(entry("d1", amountGrams = 100.0))
            repository.emitEntry(entry("d2", slot = MealSlot.LUNCH, amountGrams = 50.0))
            repository.emitEntry(entry("d3", date = today.minusDays(1), amountGrams = 200.0))

            val days = viewModel(repository).state.value.days
            val expectedDates = listOf(today.toString(), today.minusDays(1).toString())

            assertThat(days.map { it.date }).containsExactly(expectedDates[0], expectedDates[1])
            assertThat(days.first().kcal).isEqualTo("150")
            assertThat(days.first().entryCount).isEqualTo(2)
            assertThat(days[1].kcal).isEqualTo("200")
        }

    @Test
    fun `isLoading is false after the first emission`() =
        runTest {
            assertThat(viewModel(repository).state.value.isLoading).isFalse()
        }

    @Test
    fun `entries outside the history window are ignored`() =
        runTest {
            repository.emitEntry(entry("d1", amountGrams = 100.0))
            repository.emitEntry(entry("old", date = today.minusDays(40), amountGrams = 500.0))

            val days = daysOf(viewModel(repository))

            assertThat(days).hasSize(1)
            assertThat(days.single().date).isEqualTo(today.toString())
            assertThat(days.single().kcal).isEqualTo("100")
        }

    @Test
    fun `history reflects an entry added after the screen was opened`() =
        runTest {
            repository.emitEntry(entry("d1", amountGrams = 100.0))
            val vm = viewModel(repository)
            assertThat(daysOf(vm).single().entryCount).isEqualTo(1)

            repository.emitEntry(entry("d2", slot = MealSlot.LUNCH, amountGrams = 50.0))

            val day = daysOf(vm).single()
            assertThat(day.entryCount).isEqualTo(2)
            assertThat(day.kcal).isEqualTo("150")
        }

    @Test
    fun `history reflects a deleted entry`() =
        runTest {
            repository.emitEntry(entry("d1", amountGrams = 100.0))
            repository.emitEntry(entry("d2", slot = MealSlot.LUNCH, amountGrams = 50.0))
            val vm = viewModel(repository)
            assertThat(daysOf(vm).single().entryCount).isEqualTo(2)

            repository.delete(DiaryEntryId("d2"))

            val day = daysOf(vm).single()
            assertThat(day.entryCount).isEqualTo(1)
            assertThat(day.kcal).isEqualTo("100")
        }

    @Test
    fun `a new day shows up while the screen stays open`() =
        runTest {
            val vm = viewModel(repository)
            assertThat(daysOf(vm)).hasSize(0)

            repository.emitEntry(entry("d1", date = today.minusDays(1), amountGrams = 100.0))
            repository.emitEntry(entry("d2", amountGrams = 250.0))

            val dates = daysOf(vm).map { it.date }
            assertThat(dates).hasSize(2)
            assertThat(dates.first()).isEqualTo(today.toString())
            assertThat(dates.last()).isEqualTo(today.minusDays(1).toString())
        }
}
