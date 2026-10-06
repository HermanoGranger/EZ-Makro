package com.example.ezmakro.core.domain.model.backup

import java.time.LocalDate

/**
 * Dry-run result of reading a backup file (A11): everything the preview dialog
 * needs to show before the user picks a mode — counts, the exported-at date,
 * the covered date span and all non-fatal problems.
 *
 * Running a preview never writes to the database.
 */
data class BackupPreview(
    val formatVersion: Int,
    val appVersionName: String,
    val exportedAtEpochMillis: Long,
    val foodItemCount: Int,
    val recipeCount: Int,
    val diaryEntryCount: Int,
    val weightLogCount: Int,
    val earliestDate: LocalDate?,
    val latestDate: LocalDate?,
    val warnings: List<BackupWarning>,
)
