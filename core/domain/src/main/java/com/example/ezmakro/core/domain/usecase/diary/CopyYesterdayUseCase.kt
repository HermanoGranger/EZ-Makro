package com.example.ezmakro.core.domain.usecase.diary

import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import java.time.LocalDate
import java.util.UUID

/**
 * Copy every diary entry from the day before [targetDate] into [targetDate],
 * including all four meal slots (breakfast, lunch, dinner, snacks). Each copied
 * entry gets a fresh id; the food/recipe snapshots are carried over verbatim so
 * the copied entries remain immutable historical records of the new day.
 */
class CopyYesterdayUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(targetDate: LocalDate): Result<Int, DataError.Local> {
        val sourceDate = targetDate.minusDays(1)
        val source =
            when (val result = repository.getHistory(LocalDateRange(sourceDate, sourceDate))) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.error)
            }
        var copied = 0
        source.forEach { original ->
            val copy =
                DiaryEntry(
                    id = DiaryEntryId(UUID.randomUUID().toString()),
                    slot = original.slot,
                    date = targetDate,
                    food = original.food,
                    recipe = original.recipe,
                    amountGrams = original.amountGrams,
                    portionCount = original.portionCount,
                )
            when (val saved = repository.save(copy)) {
                is Result.Success -> copied++
                is Result.Error -> return Result.Error(saved.error)
            }
        }
        return Result.Success(copied)
    }
}
