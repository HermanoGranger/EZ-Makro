package com.example.ezmakro.core.domain.model

import java.util.UUID

/**
 * Untyped form state of one recipe ingredient: amounts are still `String`
 * (ADR-003) because they come straight from text fields. Turning drafts into
 * typed [RecipeIngredient]s is the job of
 * `BuildRecipeFromDraftsUseCase`.
 */
data class RecipeIngredientDraft(
    val id: String = UUID.randomUUID().toString(),
    val foodId: String,
    val foodName: String,
    val servingLabel: String?,
    val rawWeight: String = "",
    val servingCount: String = "",
    val recipeId: String? = null,
    val originalLastModifiedAt: Long? = null,
    val snapshotChanged: Boolean = false,
    val recipeSnapshot: RecipeSnapshot? = null,
)

/**
 * Flags drafts whose sub-recipe is new or changed since the snapshot was taken.
 * A draft without a [RecipeIngredientDraft.recipeId] or without a recorded
 * [RecipeIngredientDraft.originalLastModifiedAt] never carries the hint; a
 * sub-recipe that disappeared counts as changed.
 */
fun List<RecipeIngredientDraft>.markChangedRecipeSnapshots(recipes: List<Recipe>): List<RecipeIngredientDraft> =
    map { draft ->
        if (draft.recipeId == null || draft.originalLastModifiedAt == null) return@map draft
        val original = recipes.firstOrNull { it.id.value == draft.recipeId }
        val changed = original == null || original.lastModifiedAt != draft.originalLastModifiedAt
        draft.copy(snapshotChanged = changed)
    }
