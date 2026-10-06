package com.example.ezmakro.core.domain.model.backup

/**
 * How an imported backup is merged into the local database (A6).
 *
 * [MERGE] (default) only adds rows that are missing — existing rows are never
 * overwritten, which makes a repeated import of the same document idempotent
 * (A7). [REPLACE] clears the five backup tables first and then writes the
 * archive as-is.
 */
enum class ImportMode {
    MERGE,
    REPLACE,
}
