package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.database.entity.RecipeEntity
import com.example.ezmakro.core.database.entity.RecipeIngredientEntity
import com.example.ezmakro.core.database.entity.WeightLogEntity

/**
 * The five tables of a backup in their persisted form, grouped so one call to
 * `BackupDao.insertAll` / `BackupDao.replaceAll` can write them in a single
 * transaction.
 *
 * Lives in `:core:data` next to [BackupLocalDataSource]: `:core:domain` must
 * not know Room entities, so the domain boundary stays at `BackupRepository`.
 */
data class BackupRows(
    val foods: List<FoodItemEntity> = emptyList(),
    val recipes: List<RecipeEntity> = emptyList(),
    val ingredients: List<RecipeIngredientEntity> = emptyList(),
    val diaryEntries: List<DiaryEntryEntity> = emptyList(),
    val weightLogs: List<WeightLogEntity> = emptyList(),
) {
    companion object {
        /** No rows at all — used to wipe the tables in REPLACE mode. */
        val EMPTY = BackupRows()
    }
}
