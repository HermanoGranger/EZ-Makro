package com.example.ezmakro.feature.recipes.presentation.editor

import com.example.ezmakro.core.presentation.uitext.UiText

data class IngredientDraft(
    val foodId: String,
    val foodName: String,
    val servingLabel: String?,
    val rawWeight: String = "",
    val servingCount: String = "",
    val recipeId: String? = null,
    val originalLastModifiedAt: Long? = null,
    val snapshotChanged: Boolean = false,
)

data class RecipeEditorState(
    val isEditingExisting: Boolean,
    val recipeId: String? = null,
    val name: String = "",
    val category: String = "",
    val portions: String = "1",
    val finalWeight: String = "",
    val ingredients: List<IngredientDraft> = emptyList(),
    val availableFoods: List<FoodPickerItem> = emptyList(),
    val availableRecipes: List<RecipePickerItem> = emptyList(),
    val expandedIngredientIndex: Int? = null,
    val nameError: UiText? = null,
    val numberError: UiText? = null,
    val ingredientError: UiText? = null,
    val saveError: UiText? = null,
    val isFavorite: Boolean = false,
    val isSaving: Boolean = false,
)

data class FoodPickerItem(
    val id: String,
    val name: String,
    val servingLabel: String?,
)

data class RecipePickerItem(
    val id: String,
    val name: String,
)
