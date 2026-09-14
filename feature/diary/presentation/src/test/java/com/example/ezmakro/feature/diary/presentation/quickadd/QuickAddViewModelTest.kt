package com.example.ezmakro.feature.diary.presentation.quickadd

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.usecase.diary.QuickAddUseCase
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
class QuickAddViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeDiaryRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDiaryRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle): QuickAddViewModel =
        QuickAddViewModel(
            quickAdd = QuickAddUseCase(repository),
            savedStateHandle = savedStateHandle,
        )

    @Test
    fun `prefills slot from nav argument`() =
        runTest {
            val vm = createViewModel(savedStateHandle("slot" to "DINNER", "date" to "2026-08-03"))

            assertThat(vm.state.value.slot).isEqualTo(MealSlot.DINNER)
        }

    @Test
    fun `saves entry on the date from nav argument instead of today`() =
        runTest {
            val vm = createViewModel(savedStateHandle("date" to "2026-08-03", "slot" to "DINNER"))
            vm.onAction(QuickAddAction.OnNameChange("Snack"))
            vm.onAction(QuickAddAction.OnAmountChange("50"))

            vm.events.test {
                vm.onAction(QuickAddAction.OnSaveClick)

                awaitItem()
                assertThat(repository.entries.value).hasSize(1)
                val entry = repository.entries.value.single()
                assertThat(entry.date).isEqualTo(LocalDate.of(2026, 8, 3))
                assertThat(entry.slot).isEqualTo(MealSlot.DINNER)
                assertThat(entry.amountGrams).isEqualTo(50.0)
            }
        }

    private fun savedStateHandle(vararg pairs: Pair<String, String>): SavedStateHandle = SavedStateHandle(mapOf(*pairs))
}
