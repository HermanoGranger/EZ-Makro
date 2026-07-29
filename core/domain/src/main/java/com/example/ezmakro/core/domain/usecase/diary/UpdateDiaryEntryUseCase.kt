package com.example.ezmakro.core.domain.usecase.diary

import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result

class UpdateDiaryEntryUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(
        entryId: DiaryEntryId,
        amountGrams: Double?,
        portionCount: Double?,
    ): EmptyResult<DataError.Local> {
        val result = repository.getById(entryId)
        val entry =
            when (result) {
                is Result.Success -> result.data ?: return Result.Error(DataError.Local.NOT_FOUND)
                is Result.Error -> return Result.Error(result.error)
            }
        val updated = entry.copy(amountGrams = amountGrams, portionCount = portionCount)
        return repository.save(updated)
    }
}
