package com.example.ezmakro.core.domain.model.backup

/**
 * One backup row that was rejected during validation (A11): it is left out of
 * the import and reported with a concrete reason instead of aborting the run.
 *
 * [table] uses the logical table name (`food_items`, `recipes`,
 * `diary_entries`, `weight_logs`), [id] the natural key of the row.
 */
data class SkippedRow(
    val table: String,
    val id: String,
    val reason: String,
)
