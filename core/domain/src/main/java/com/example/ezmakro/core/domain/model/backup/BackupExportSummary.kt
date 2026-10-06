package com.example.ezmakro.core.domain.model.backup

/**
 * Row counts of a backup run — both for what was just written (export) and for
 * what the app currently holds ([BackupRepository.counts], WP4 settings screen).
 */
data class BackupExportSummary(
    val foodItemCount: Int,
    val recipeCount: Int,
    val diaryEntryCount: Int,
    val weightLogCount: Int,
    val settingCount: Int,
)
