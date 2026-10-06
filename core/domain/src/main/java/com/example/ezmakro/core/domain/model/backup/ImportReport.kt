package com.example.ezmakro.core.domain.model.backup

/**
 * Result of one completed import run.
 *
 * Three counters are deliberately kept apart (A18) so the report dialog can
 * show them separately:
 * - [inserted] rows that were actually written,
 * - [skippedDuplicates] rows the `IGNORE` conflict strategy dropped because
 *   they already exist (MERGE idempotency, A7),
 * - the rows in [skippedRows] that failed validation plus the references in
 *   [droppedReferences] that pointed at an unknown food/recipe.
 *
 * [settingsRestored] counts the settings written after the database commit
 * (A11: DataStore is not part of the Room transaction).
 */
data class ImportReport(
    val mode: ImportMode,
    val inserted: TableCounts,
    val skippedDuplicates: TableCounts,
    val skippedRows: List<SkippedRow>,
    val droppedReferences: Int,
    val settingsRestored: Int,
    val warnings: List<BackupWarning>,
)
