package com.example.ezmakro.feature.dashboard.presentation.goals

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.example.ezmakro.core.domain.model.DefaultNutritionGoal
import com.example.ezmakro.core.domain.model.NutritionGoal
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

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val goal = NutritionGoal(kcal = 2000.0, proteinG = 150.0, fatG = 56.0, carbsG = 225.0, fiberG = 30.0)
    private lateinit var repository: FakeGoalRepository
    private lateinit var viewModel: GoalsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGoalRepository(goal)
        viewModel = GoalsViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pre-fills form from persisted goal`() =
        runTest {
            val state = viewModel.state.value
            assertThat(state.kcal).isEqualTo("2000")
            assertThat(state.protein).isEqualTo("150")
            assertThat(state.fat).isEqualTo("56")
            assertThat(state.carbs).isEqualTo("225")
            assertThat(state.fiber).isEqualTo("30")
        }

    @Test
    fun `OnSaveClick persists parsed goal and navigates back`() =
        runTest {
            viewModel.onAction(GoalsAction.OnKcalChange("1800"))
            viewModel.onAction(GoalsAction.OnProteinChange("120"))
            viewModel.onAction(GoalsAction.OnFatChange("60"))
            viewModel.onAction(GoalsAction.OnCarbsChange("200"))
            viewModel.onAction(GoalsAction.OnFiberChange("25"))

            viewModel.events.test {
                viewModel.onAction(GoalsAction.OnSaveClick)
                assertThat(awaitItem()).isEqualTo(GoalsEvent.NavigateBack)
            }

            val saved = repository.savedGoals.single()
            assertThat(saved).isEqualTo(NutritionGoal(1800.0, 120.0, 60.0, 200.0, 25.0))
        }

    @Test
    fun `OnSaveClick with negative number emits error and does not save`() =
        runTest {
            viewModel.onAction(GoalsAction.OnKcalChange("-5"))
            viewModel.onAction(GoalsAction.OnSaveClick)

            assertThat(viewModel.state.value.numberError).isEqualTo(
                com.example.ezmakro.core.presentation.uitext.UiText
                    .StringResource(com.example.ezmakro.feature.dashboard.presentation.R.string.goals_error_invalid),
            )
            assertThat(repository.savedGoals).isEmpty()
        }

    @Test
    fun `OnSaveClick with blank number treats as zero`() =
        runTest {
            viewModel.onAction(GoalsAction.OnKcalChange(""))
            viewModel.onAction(GoalsAction.OnSaveClick)
            val saved = repository.savedGoals.single()
            assertThat(saved.kcal).isEqualTo(0.0)
        }

    @Test
    fun `OnResetToDefaultClick persists default and navigates back`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(GoalsAction.OnResetToDefaultClick)
                assertThat(awaitItem()).isEqualTo(GoalsEvent.NavigateBack)
            }
            assertThat(repository.savedGoals).contains(DefaultNutritionGoal)
        }

    @Test
    fun `OnBackClick navigates back without saving`() =
        runTest {
            viewModel.events.test {
                viewModel.onAction(GoalsAction.OnBackClick)
                assertThat(awaitItem()).isEqualTo(GoalsEvent.NavigateBack)
            }
            assertThat(repository.savedGoals).isEmpty()
        }
}
