package com.example.ezmakro.core.database.dao

/**
 * Insert/skip counters for one table of a bulk insert.
 *
 * Room reports `-1` as the rowId for a row that was skipped by
 * `OnConflictStrategy.IGNORE`. [preSkipped] covers rows that were never even
 * attempted — ingredient lines of recipes that already existed (A7/A19).
 */
data class TableInsertCounts(
    val inserted: Int,
    val skipped: Int,
) {
    companion object {
        fun from(
            rowIds: List<Long>,
            preSkipped: Int = 0,
        ): TableInsertCounts =
            TableInsertCounts(
                inserted = rowIds.count { it >= 0L },
                skipped = rowIds.count { it == -1L } + preSkipped,
            )
    }
}

/** Per-table insert/skip counters of a single [BackupDao.insertAll] or [BackupDao.replaceAll] run. */
data class BackupInsertReport(
    val foodItems: TableInsertCounts,
    val recipes: TableInsertCounts,
    val recipeIngredients: TableInsertCounts,
    val diaryEntries: TableInsertCounts,
    val weightLogs: TableInsertCounts,
)
