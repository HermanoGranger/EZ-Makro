package com.example.ezmakro.feature.settings.presentation.settings.backup

import com.example.ezmakro.core.domain.model.backup.BackupPreview
import com.example.ezmakro.core.domain.model.backup.ImportMode
import com.example.ezmakro.core.domain.model.backup.ImportReport

/**
 * State of the backup screen. The stock counts are a one-shot read from
 * [com.example.ezmakro.core.domain.repository.BackupRepository.counts] that is
 * refreshed after every successful import — a frozen snapshot would keep
 * showing the pre-import numbers for the lifetime of the back stack entry
 * (stale-snapshot bug class, see docs/Bugfix_Plan_Stale_Snapshots.md).
 */
data class BackupState(
    val foodItemCount: Int = 0,
    val recipeCount: Int = 0,
    val diaryEntryCount: Int = 0,
    val weightLogCount: Int = 0,
    val includeCachedItems: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val preview: BackupPreview? = null,
    val importReport: ImportReport? = null,
    val pendingMode: ImportMode = ImportMode.MERGE,
)
