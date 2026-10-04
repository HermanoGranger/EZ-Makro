package com.example.ezmakro.feature.recipes.presentation.editor

import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.presentation.uitext.UiText

data class RecipeEditorState(
    val isEditingExisting: Boolean,
    val recipeId: String? = null,
    val name: String = "",
    val category: String = "",
    val portions: String = "1",
    val finalWeight: String = "",
    val ingredients: List<RecipeIngredientDraft> = emptyList(),
    val ingredientNutrition: List<NutritionValues> = emptyList(),
    val totalNutrition: NutritionValues = NutritionValues.ZERO,
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
