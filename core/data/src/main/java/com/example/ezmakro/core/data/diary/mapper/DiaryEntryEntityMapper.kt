package com.example.ezmakro.core.data.diary.mapper

import com.example.ezmakro.core.database.converter.JsonConverters
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.MealSlot
import java.time.LocalDate

fun DiaryEntryEntity.toDiaryEntry(): DiaryEntry {
    val food = foodSnapshot?.let { JsonConverters.decodeFoodItem(it) }
    val recipe = recipeSnapshot?.let { JsonConverters.decodeRecipe(it) }
    return DiaryEntry(
        id = DiaryEntryId(id),
        slot = MealSlot.valueOf(slot),
        date = LocalDate.parse(date),
        food = food,
        recipe = recipe,
        amountGrams = amountGrams,
        portionCount = portionCount,
    )
}

fun DiaryEntry.toEntity(): DiaryEntryEntity =
    DiaryEntryEntity(
        id = id.value,
        slot = slot.name,
        date = date.toString(),
        foodItemId = food?.takeUnless { it.isTransient }?.id?.value,
        recipeId = recipe?.id?.value,
        amountGrams = amountGrams,
        portionCount = portionCount,
        foodSnapshot = food?.let { JsonConverters.encodeFoodItem(it) },
        recipeSnapshot = recipe?.let { JsonConverters.encodeRecipe(it) },
    )
