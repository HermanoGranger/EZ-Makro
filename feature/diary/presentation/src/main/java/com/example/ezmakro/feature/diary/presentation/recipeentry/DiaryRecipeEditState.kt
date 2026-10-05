package com.example.ezmakro.feature.diary.presentation.recipeentry

import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.presentation.uitext.UiText

enum class DiaryAmountMode {
    PORTIONS,
    GRAMS,
}

data class DiaryRecipeEditState(
    val isLoading: Boolean = true,
    val entryId: String = "",
    val recipeName: String = "",
    val date: String = "",
    val sourceRecipeId: String? = null,
    val canUpdateRecipe: Boolean = false,
    val isSourceRecipeOutdated: Boolean = false,
    val isRecipeOverridden: Boolean = false,
    val amountMode: DiaryAmountMode = DiaryAmountMode.PORTIONS,
    val amountText: String = "",
    val portions: String = "",
    val finalWeight: String = "",
    val ingredients: List<RecipeIngredientDraft> = emptyList(),
    val ingredientNutrition: List<NutritionValues> = emptyList(),
    val totalNutrition: NutritionValues = NutritionValues.ZERO,
    val originalConsumedNutrition: NutritionValues = NutritionValues.ZERO,
    val consumedNutrition: NutritionValues = NutritionValues.ZERO,
    val expandedIngredientIndex: Int? = null,
    val isAddPickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    val amountError: UiText? = null,
    val numberError: UiText? = null,
    val ingredientError: UiText? = null,
    val saveError: UiText? = null,
    val isSaveScopeDialogOpen: Boolean = false,
)
