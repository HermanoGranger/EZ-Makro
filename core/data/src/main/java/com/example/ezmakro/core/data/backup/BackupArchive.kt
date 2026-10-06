package com.example.ezmakro.core.data.backup

import kotlinx.serialization.Serializable

/**
 * DTOs of the versioned EZ Makro backup file (`formatVersion = 1`, ADR-007).
 *
 * The archive is a **logical** format, deliberately decoupled from the Room
 * schema (A1/A2): the field names are the CamelCase pendant of the current
 * columns, but a later column rework never touches these DTOs — only
 * `formatVersion` decides whether an archive can be imported.
 *
 * Format rules enforced here:
 * - Every optional field has a default value so archives written by older app
 *   versions keep decoding (A12). The frame's required fields (`format`,
 *   `formatVersion`, `appVersion*`, `exportedAtEpochMillis`, `dbSchemaVersion`,
 *   `content`) stay default-free on purpose; `checksum` is optional with `""`
 *   (missing checksum is a warning, never an abort — A14).
 * - [BackupContentDto.settings] is a sorted **list**, not a `Map`, so the
 *   checksum over `content` is deterministic (A14).
 * - No `rowId` and no FTS section: surrogate keys carry no business meaning and
 *   `food_items_fts` is maintained by the Room triggers on import (A13).
 * - `cachedStatus` and `slot` stay plain [String]; an unknown value from a
 *   future app version survives decode and is mapped by the importer, not by
 *   the format.
 * - `foodSnapshot` / `recipeSnapshot` / `recipeSnapshotJson` are opaque strings
 *   (A8); this layer never parses them.
 */
@Serializable
data class BackupDocumentDto(
    val format: String,
    val formatVersion: Int,
    val appVersionName: String,
    val appVersionCode: Int,
    val exportedAtEpochMillis: Long,
    val dbSchemaVersion: Int,
    val checksum: String = "",
    val content: BackupContentDto,
)

@Serializable
data class BackupContentDto(
    val settings: List<BackupSettingDto> = emptyList(),
    val foodItems: List<BackupFoodItemDto> = emptyList(),
    val recipes: List<BackupRecipeDto> = emptyList(),
    val diaryEntries: List<BackupDiaryEntryDto> = emptyList(),
    val weightLogs: List<BackupWeightLogDto> = emptyList(),
)

@Serializable
data class BackupSettingDto(
    val key: String,
    val value: String,
)

@Serializable
data class BackupFoodItemDto(
    val id: String,
    val name: String,
    val barcode: String? = null,
    val nutriKcal: Double? = null,
    val nutriProteinG: Double? = null,
    val nutriFatG: Double? = null,
    val nutriCarbsG: Double? = null,
    val nutriFiberG: Double? = null,
    val servingLabel: String? = null,
    val servingGramsEquivalent: Double? = null,
    val isUserCreated: Boolean = false,
    val cachedStatus: String = "ORIGINAL",
    val origName: String? = null,
    val origBarcode: String? = null,
    val origNutriKcal: Double? = null,
    val origNutriProteinG: Double? = null,
    val origNutriFatG: Double? = null,
    val origNutriCarbsG: Double? = null,
    val origNutriFiberG: Double? = null,
    val origServingLabel: String? = null,
    val origServingGramsEquivalent: Double? = null,
    val origIsUserCreated: Boolean? = null,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
)

/**
 * One recipe plus its `recipe_ingredients` rows of **that** recipe id — a
 * deliberate normalization: the surrogate `row_id` of an ingredient row is not
 * fachlich and is therefore not part of the format (A13).
 */
@Serializable
data class BackupRecipeDto(
    val id: String,
    val name: String,
    val finalWeightGrams: Double? = null,
    val portions: Int = 1,
    val category: String? = null,
    val lastModifiedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val ingredients: List<BackupIngredientDto> = emptyList(),
)

@Serializable
data class BackupIngredientDto(
    val foodItemId: String? = null,
    val rawWeightGrams: Double? = null,
    val servingCount: Double? = null,
    val recipeSnapshotJson: String? = null,
)

@Serializable
data class BackupDiaryEntryDto(
    val id: String,
    val slot: String,
    val date: String,
    val foodItemId: String? = null,
    val recipeId: String? = null,
    val amountGrams: Double? = null,
    val portionCount: Double? = null,
    val recipeOverridden: Boolean = false,
    val foodSnapshot: String? = null,
    val recipeSnapshot: String? = null,
)

@Serializable
data class BackupWeightLogDto(
    val date: String,
    val kilograms: Double,
)
