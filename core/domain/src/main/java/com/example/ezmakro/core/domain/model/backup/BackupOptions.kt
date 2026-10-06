package com.example.ezmakro.core.domain.model.backup

/**
 * User-selectable knobs of a single export run.
 *
 * The scope of everything else is fixed by A5: own foods, favorites, foods
 * referenced by recipes or diary entries, all recipes, diary entries, weight
 * logs and all settings are always exported.
 */
data class BackupOptions(
    /** Include the whole cached OpenFoodFacts stock, not only own/favorite/referenced foods (A5). */
    val includeCachedOpenFoodFactsItems: Boolean = false,
)
