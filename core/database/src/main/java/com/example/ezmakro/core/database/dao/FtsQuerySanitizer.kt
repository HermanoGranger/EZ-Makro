package com.example.ezmakro.core.database.dao

/**
 * Sanitizes free-text user input before it is handed to the FTS4 `MATCH`
 * clause. FTS metacharacters (`"`, `*`, `(`, `)`, `+`, `-`, `:`) would either
 * throw a `SQLiteException` or silently change the query semantics, so they are
 * stripped before the caller appends its prefix-wildcard `*`.
 */
object FtsQuerySanitizer {
    fun sanitize(raw: String): String = raw.filterNot { it in FTS_METACHARACTERS }

    private const val FTS_METACHARACTERS = "\"*()+-:"
}
