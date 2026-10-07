package com.example.ezmakro.core.data.backup

import com.example.ezmakro.core.data.diary.mapper.toDiaryEntry
import com.example.ezmakro.core.data.food.mapper.toFoodItem
import com.example.ezmakro.core.database.converter.JsonConverters
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.ServingUnit
import java.time.LocalDate

/**
 * Row validation of import stage 4 (§3.4) — **without any access to the target
 * database** (A19 / Review-Befund 🔴).
 *
 * Every function returns `null` when the row is valid and otherwise the
 * concrete reason that ends up in `ImportReport.skippedRows`. That is a
 * deliberate deviation from the sketch in the plan (`Boolean` / `FoodItem?`):
 * A11 mandates a reason for *every* skipped row, and one pass per row keeps
 * the happy path single-evaluation.
 *
 * Rejected rows never abort the run — the import continues with the rest
 * (A11).
 */
object BackupValidator {
    /**
     * Stage 4a: rebuild the domain [FoodItem] via the existing entity → domain
     * mapper, which triggers `name.isNotBlank()`, non-negative nutrition
     * values, the ADR-004 snapshot rule and `lastUsedAt >= 0`.
     */
    fun validateFood(item: BackupFoodItemDto): String? =
        catchReason("Lebensmittel verletzt eine Domain-Invariante") {
            item.toEntity().toFoodItem()
        }

    /**
     * Stage 4b: backup-internal recipe check that mirrors the ingredient rules
     * of the existing `RecipeEntityMapper.resolveIngredient` **without** ever
     * calling `RecipeEntity.toRecipe(..., foodLocalDataSource)` — that mapper is
     * bound to the target DB and would reject every recipe on an empty one
     * (REPLACE after a fresh start).
     *
     * @param validFoods foods validated by [validateFood] from this archive.
     * @param existingFoodServingUnits target-DB foods the archive may resolve
     * against — id → serving unit for MERGE, empty for REPLACE (A19). The
     * serving unit is read from here (W3) because the runtime mapper resolves
     * `servingCount` ingredients against exactly these rows after the import;
     * where a MERGE keeps a pre-existing local row, that row wins over the
     * archive's copy.
     */
    fun validateRecipe(
        recipe: BackupRecipeDto,
        validFoods: Map<String, FoodItem>,
        existingFoodServingUnits: Map<String, ServingUnit?>,
    ): String? {
        val structuralIssue =
            when {
                recipe.name.isBlank() -> "Rezeptname darf nicht leer sein"
                recipe.portions <= 0 -> "portions muss größer als 0 sein, war ${recipe.portions}"
                recipe.ingredients.isEmpty() -> "Rezept hat keine Zutaten"
                else -> null
            }
        return structuralIssue
            ?: recipe.ingredients.firstNotNullOfOrNull { ingredientIssue(it, validFoods, existingFoodServingUnits) }
    }

    /**
     * Stage 4c: rebuild the domain diary entry — triggers `MealSlot.valueOf`,
     * `LocalDate.parse`, snapshot decoding and the food/recipe XOR plus amount
     * invariants of `DiaryEntry`.
     */
    fun validateDiaryEntry(entry: BackupDiaryEntryDto): String? =
        catchReason("Tagebucheintrag verletzt eine Domain-Invariante") {
            entry.toEntity().toDiaryEntry()
        }

    /** Stage 4d: positive weight and a parseable ISO date. */
    fun validateWeightLog(log: BackupWeightLogDto): String? {
        if (log.kilograms <= 0.0) return "kilograms muss größer als 0 sein, war ${log.kilograms}"
        return catchReason("ungültiges Datum: ${log.date}") {
            LocalDate.parse(log.date)
        }
    }

    private fun ingredientIssue(
        ingredient: BackupIngredientDto,
        validFoods: Map<String, FoodItem>,
        existingFoodServingUnits: Map<String, ServingUnit?>,
    ): String? =
        sourceIssue(ingredient)
            ?: amountIssue(ingredient)
            ?: referenceIssue(ingredient, validFoods, existingFoodServingUnits)

    /** Exactly one of food reference or snapshot (mirrors `RecipeIngredient`). */
    private fun sourceIssue(ingredient: BackupIngredientDto): String? {
        val hasFoodReference = ingredient.foodItemId != null
        val hasSnapshot = ingredient.recipeSnapshotJson != null
        return if (hasFoodReference == hasSnapshot) {
            "Zutat braucht genau eine Quelle (Food-Referenz oder Snapshot)"
        } else {
            null
        }
    }

    /** At least one positive amount. */
    private fun amountIssue(ingredient: BackupIngredientDto): String? {
        val rawWeight = ingredient.rawWeightGrams
        val servingCount = ingredient.servingCount
        return when {
            rawWeight == null && servingCount == null -> "Zutat hat keine Menge"
            rawWeight != null && rawWeight <= 0.0 -> "rawWeightGrams muss größer als 0 sein, war $rawWeight"
            servingCount != null && servingCount <= 0.0 -> "servingCount muss größer als 0 sein, war $servingCount"
            else -> null
        }
    }

    /**
     * The reference must resolve, either against this archive or against the
     * target DB, and the serving-unit rule (W3) must hold for the food that
     * will actually back the ingredient **after** the import: MERGE keeps a
     * pre-existing local row on duplicates, so the target DB's serving unit
     * wins where it exists — the same row `resolveIngredient` reads later.
     */
    private fun referenceIssue(
        ingredient: BackupIngredientDto,
        validFoods: Map<String, FoodItem>,
        existingFoodServingUnits: Map<String, ServingUnit?>,
    ): String? {
        val foodId = ingredient.foodItemId ?: return snapshotIssue(ingredient)
        val inArchive = foodId in validFoods
        val inTargetDb = foodId in existingFoodServingUnits
        if (!inArchive && !inTargetDb) return "unbekannte Food-Referenz: $foodId"
        val servingUnit =
            if (inTargetDb) {
                existingFoodServingUnits.getValue(foodId)
            } else {
                validFoods.getValue(foodId).servingUnit
            }
        val servingUnitMissing =
            ingredient.servingCount != null && ingredient.rawWeightGrams == null && servingUnit == null
        return if (servingUnitMissing) "servingCount braucht eine ServingUnit des Lebensmittels" else null
    }

    private fun snapshotIssue(ingredient: BackupIngredientDto): String? =
        catchReason("Zutat-Snapshot ist nicht dekodierbar") {
            JsonConverters.decodeRecipeSnapshot(checkNotNull(ingredient.recipeSnapshotJson))
        }

    /**
     * Runs a validation block and turns any invariant violation into a reason.
     * The existing mappers signal via `require` (IllegalArgumentException),
     * date parsing via DateTimeException and snapshot decoding via a
     * serialization exception; anything unexpected is reported instead of
     * crashing the import — an invalid row must never reach the ViewModel as an
     * exception (A11).
     */
    @Suppress("TooGenericExceptionCaught")
    private fun catchReason(
        fallback: String,
        block: () -> Unit,
    ): String? =
        try {
            block()
            null
        } catch (e: Exception) {
            e.message?.takeIf { it.isNotBlank() } ?: fallback
        }
}
