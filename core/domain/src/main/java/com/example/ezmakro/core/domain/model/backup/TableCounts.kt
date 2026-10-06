package com.example.ezmakro.core.domain.model.backup

/**
 * Per-table counters of one backup run. The five tables of `formatVersion = 1`
 * are fixed, so the shape mirrors [BackupExportSummary] without settings.
 */
data class TableCounts(
    val foodItems: Int,
    val recipes: Int,
    val recipeIngredients: Int,
    val diaryEntries: Int,
    val weightLogs: Int,
)
