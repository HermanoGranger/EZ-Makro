package com.example.ezmakro.feature.dashboard.presentation

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
import com.example.ezmakro.core.domain.model.NutritionGoal
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.presentation.date.CurrentDateProvider
import com.example.ezmakro.feature.dashboard.presentation.fake.FakeDiaryRepository
import com.example.ezmakro.feature.dashboard.presentation.fake.FakeGoalRepository
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
class DashboardViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val date = LocalDate.now()
    private val goal = NutritionGoal(kcal = 2000.0, proteinG = 150.0, fatG = 56.0, carbsG = 225.0, fiberG = 30.0)
    private lateinit var diary: FakeDiaryRepository
    private lateinit var goals: FakeGoalRepository
    private lateinit var currentDateProvider: CurrentDateProvider
    private lateinit var viewModel: DashboardViewModel

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
        foodId: String,
        amountGrams: Double,
    ): DiaryEntry =
        DiaryEntry(
            id = DiaryEntryId(id),
            slot = MealSlot.BREAKFAST,
            date = date,
            food = food(foodId),
            recipe = null,
            amountGrams = amountGrams,
            portionCount = null,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        diary = FakeDiaryRepository()
        goals = FakeGoalRepository(goal)
        currentDateProvider = CurrentDateProvider()
        viewModel =
            DashboardViewModel(
                diaryRepository = diary,
                goalRepository = goals,
                currentDateProvider = currentDateProvider,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `computes remaining kcal from consumed and goal`() =
        runTest {
            diary.emitEntry(entry("a", "f1", 100.0)) // 100 kcal
            diary.emitEntry(entry("b", "f2", 200.0)) // 200 kcal

            val ui = viewModel.state.value.dashboard
            assertThat(ui?.consumedKcal).isEqualTo("300")
            assertThat(ui?.goalKcal).isEqualTo("2000")
            assertThat(ui?.remainingKcal).isEqualTo("+1700")
            assertThat(ui?.isOverGoal ?: false).isFalse()
        }

    @Test
    fun `marks over goal when consumed exceeds target`() =
        runTest {
            diary.emitEntry(entry("a", "f1", 2500.0)) // 2500 kcal vs 2000 goal

            val ui = viewModel.state.value.dashboard
            assertThat(ui?.remainingKcal).isEqualTo("-500")
            assertThat(ui?.isOverGoal ?: false).isTrue()
        }

    @Test
    fun `empty day shows full goal remaining and not over`() =
        runTest {
            val ui = viewModel.state.value.dashboard
            assertThat(ui?.consumedKcal).isEqualTo("0")
            assertThat(ui?.remainingKcal).isEqualTo("+2000")
            assertThat(ui?.isOverGoal ?: false).isFalse()
        }

    @Test
    fun `OnOpenDiaryClick emits NavigateToDiary with today`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(DashboardAction.OnOpenDiaryClick)
                assertThat(awaitItem())
                    .isEqualTo(DashboardEvent.NavigateToDiary(date.toString()))
            }
        }

    @Test
    fun `OnConfigureGoalsClick emits NavigateToGoals`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(DashboardAction.OnConfigureGoalsClick)
                assertThat(awaitItem()).isEqualTo(DashboardEvent.NavigateToGoals)
            }
        }

    @Test
    fun `OnPreviousDay changes dashboard date to yesterday`() =
        runTest {
            viewModel.onAction(DashboardAction.OnPreviousDay)
            assertThat(viewModel.state.value.date).isEqualTo(date.minusDays(1))
        }

    @Test
    fun `OnNextDay changes dashboard date to tomorrow`() =
        runTest {
            viewModel.onAction(DashboardAction.OnNextDay)
            assertThat(viewModel.state.value.date).isEqualTo(date.plusDays(1))
        }

    @Test
    fun `OnPreviousDay loads entries for previous day`() =
        runTest {
            val yesterday = date.minusDays(1)
            diary.emitEntry(
                DiaryEntry(
                    id = DiaryEntryId("y1"),
                    slot = MealSlot.BREAKFAST,
                    date = yesterday,
                    food = food("f1"),
                    recipe = null,
                    amountGrams = 100.0,
                    portionCount = null,
                ),
            )

            viewModel.onAction(DashboardAction.OnPreviousDay)

            val state = viewModel.state.value
            assertThat(state.date).isEqualTo(yesterday)
            assertThat(state.dashboard?.consumedKcal).isEqualTo("100")
        }
}
