package com.example.ezmakro.feature.diary.presentation.day

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.usecase.diary.CopyYesterdayUseCase
import com.example.ezmakro.core.domain.usecase.diary.DeleteDiaryEntryUseCase
import com.example.ezmakro.core.domain.usecase.diary.UpdateDiaryEntryUseCase
import com.example.ezmakro.core.presentation.date.CurrentDateProvider
import com.example.ezmakro.feature.diary.presentation.fake.FakeDiaryRepository
import com.example.ezmakro.feature.diary.presentation.fake.FakeGoalRepository
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
    private val goal = NutritionGoal(kcal = 2000.0, proteinG = 150.0, fatG = 56.0, carbsG = 225.0, fiberG = 30.0)
    private lateinit var repository: FakeDiaryRepository
    private lateinit var goals: FakeGoalRepository
    private lateinit var currentDateProvider: CurrentDateProvider
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
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDiaryRepository()
        goals = FakeGoalRepository(goal)
        currentDateProvider = CurrentDateProvider()
        currentDateProvider.setDate(date)
        viewModel =
            DiaryDayViewModel(
                repository = repository,
                goalRepository = goals,
                copyYesterdayUseCase = CopyYesterdayUseCase(repository),
                deleteEntryUseCase = DeleteDiaryEntryUseCase(repository),
                updateEntryUseCase = UpdateDiaryEntryUseCase(repository),
                currentDateProvider = currentDateProvider,
                savedStateHandle = SavedStateHandle(mapOf(DiaryDayViewModel.ARG_DATE to date.toString())),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observeByDate groups entries by slot and computes summary`() =
        runTest {
            repository.emitEntry(entry("a", MealSlot.BREAKFAST, "f1", 100.0))
            repository.emitEntry(entry("b", MealSlot.LUNCH, "f2", 100.0))

            val state = viewModel.state.value
            assertThat(state.breakfast.map { it.id }).containsExactly("a")
            assertThat(state.lunch.map { it.id }).containsExactly("b")
            assertThat(state.dinner).isEqualTo(emptyList())
            assertThat(state.summary?.consumedKcal).isEqualTo("200")
            assertThat(state.summary?.goalKcal).isEqualTo("2000")
            assertThat(state.summary?.remainingKcal).isEqualTo("+1800")
            assertThat(state.summary?.isOverGoal ?: false).isFalse()
        }

    @Test
    fun `marks over goal when consumed exceeds target`() =
        runTest {
            repository.emitEntry(entry("a", MealSlot.BREAKFAST, "f1", 2500.0))

            val state = viewModel.state.value
            assertThat(state.summary?.remainingKcal).isEqualTo("-500")
            assertThat(state.summary?.isOverGoal ?: false).isTrue()
        }

    @Test
    fun `empty day shows full goal remaining and not over`() =
        runTest {
            val state = viewModel.state.value
            assertThat(state.summary?.consumedKcal).isEqualTo("0")
            assertThat(state.summary?.remainingKcal).isEqualTo("+2000")
            assertThat(state.summary?.isOverGoal ?: false).isFalse()
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
    fun `OnToggleSlot removes then re-adds a slot around the default-expanded set`() =
        runTest {
            val allSlots = MealSlot.entries.toSet()
            assertThat(viewModel.state.value.expandedSlots).isEqualTo(allSlots)

            viewModel.onAction(DiaryDayAction.OnToggleSlot(MealSlot.BREAKFAST))
            assertThat(viewModel.state.value.expandedSlots).isEqualTo(allSlots - MealSlot.BREAKFAST)

            viewModel.onAction(DiaryDayAction.OnToggleSlot(MealSlot.BREAKFAST))
            assertThat(viewModel.state.value.expandedSlots).isEqualTo(allSlots)
        }

    @Test
    fun `OnToggleSlot can collapse multiple slots`() =
        runTest {
            viewModel.onAction(DiaryDayAction.OnToggleSlot(MealSlot.BREAKFAST))
            viewModel.onAction(DiaryDayAction.OnToggleSlot(MealSlot.LUNCH))

            assertThat(viewModel.state.value.expandedSlots)
                .isEqualTo(
                    MealSlot.entries.toSet() - MealSlot.BREAKFAST - MealSlot.LUNCH,
                )
        }

    @Test
    fun `OnCopyYesterdayClick copies entries from yesterday into today`() =
        runTest {
            val yesterday = date.minusDays(1)
            repository.emitEntry(entry("y1", MealSlot.BREAKFAST, "f1", 100.0).copy(date = yesterday))

            viewModel.onAction(DiaryDayAction.OnCopyYesterdayClick)

            assertThat(repository.savedCount()).isEqualTo(1)
            assertThat(repository.entriesForDate(date)).hasSize(1)
            assertThat(viewModel.state.value.copyYesterdayMessage).isNotNull()
        }

    @Test
    fun `OnSaveEdit updates the entry amount`() =
        runTest {
            repository.emitEntry(entry("a", MealSlot.BREAKFAST, "f1", 100.0))

            viewModel.onAction(DiaryDayAction.OnSaveEdit("a", amountGrams = 200.0, portionCount = null))

            val entry = repository.getById(DiaryEntryId("a"))
            val data = (entry as com.example.ezmakro.core.domain.result.Result.Success).data
            assertThat(data).isNotNull()
            assertThat(data!!.amountGrams).isEqualTo(200.0)
        }

    @Test
    fun `OnSaveEdit with portionCount updates the entry`() =
        runTest {
            val foodWithServing =
                FoodItem(
                    id = FoodId("f1"),
                    name = "Food f1",
                    barcode = null,
                    nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
                    servingUnit = ServingUnit("1 Portion", 120.0),
                    isUserCreated = true,
                    cachedStatus = CachedFoodItemStatus.Original,
                    originalSnapshot = null,
                )
            val entryWithPortions =
                DiaryEntry(
                    id = DiaryEntryId("a"),
                    slot = MealSlot.BREAKFAST,
                    date = date,
                    food = foodWithServing,
                    recipe = null,
                    amountGrams = null,
                    portionCount = 1.0,
                )
            repository.emitEntry(entryWithPortions)

            viewModel.onAction(DiaryDayAction.OnSaveEdit("a", amountGrams = null, portionCount = 2.0))

            val entry = repository.getById(DiaryEntryId("a"))
            val data = (entry as com.example.ezmakro.core.domain.result.Result.Success).data
            assertThat(data).isNotNull()
            assertThat(data!!.portionCount).isEqualTo(2.0)
        }

    @Test
    fun `OnPreviousDay changes date to yesterday`() =
        runTest {
            viewModel.onAction(DiaryDayAction.OnPreviousDay)
            assertThat(viewModel.state.value.date).isEqualTo(date.minusDays(1))
        }

    @Test
    fun `OnNextDay changes date to tomorrow`() =
        runTest {
            viewModel.onAction(DiaryDayAction.OnNextDay)
            assertThat(viewModel.state.value.date).isEqualTo(date.plusDays(1))
        }

    @Test
    fun `OnGoToDate changes to given date`() =
        runTest {
            val target = LocalDate.of(2026, 8, 1)
            viewModel.onAction(DiaryDayAction.OnGoToDate(target))
            assertThat(viewModel.state.value.date).isEqualTo(target)
        }

    @Test
    fun `expandedSlots reset to all slots on date change`() =
        runTest {
            viewModel.onAction(DiaryDayAction.OnToggleSlot(MealSlot.BREAKFAST))
            assertThat(viewModel.state.value.expandedSlots)
                .isNotEqualTo(MealSlot.entries.toSet())

            viewModel.onAction(DiaryDayAction.OnPreviousDay)

            assertThat(viewModel.state.value.expandedSlots)
                .isEqualTo(MealSlot.entries.toSet())
        }

    @Test
    fun `state date updates on previous day and reloads entries`() =
        runTest {
            val yesterday = date.minusDays(1)
            repository.emitEntry(entry("y1", MealSlot.BREAKFAST, "f1", 100.0))
            repository.emitEntry(
                entry("t1", MealSlot.LUNCH, "f2", 200.0).copy(date = yesterday),
            )

            viewModel.onAction(DiaryDayAction.OnPreviousDay)

            assertThat(viewModel.state.value.date).isEqualTo(yesterday)
            assertThat(
                viewModel.state.value.lunch
                    .map { it.id },
            ).containsExactly("t1")
            assertThat(viewModel.state.value.breakfast).isEmpty()
            assertThat(
                viewModel.state.value.summary
                    ?.consumedKcal,
            ).isEqualTo("200")
        }
}
