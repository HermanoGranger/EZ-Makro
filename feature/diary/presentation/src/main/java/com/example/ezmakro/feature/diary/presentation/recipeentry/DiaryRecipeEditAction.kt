package com.example.ezmakro.feature.diary.presentation.recipeentry

sealed interface DiaryRecipeEditAction {
    data class OnIngredientRawWeightChange(val index: Int, val value: String) : DiaryRecipeEditAction
    data class OnIngredientServingCountChange(val index: Int, val value: String) : DiaryRecipeEditAction
    data class OnRemoveIngredient(val index: Int) : DiaryRecipeEditAction
    data class OnReplaceIngredient(val index: Int) : DiaryRecipeEditAction
    data object OnAddIngredientClick : DiaryRecipeEditAction
    data class OnIngredientClick(val index: Int) : DiaryRecipeEditAction
    data class OnAmountModeChange(val mode: DiaryAmountMode) : DiaryRecipeEditAction
    data class OnAmountChange(val value: String) : DiaryRecipeEditAction
    data class OnPortionsChange(val value: String) : DiaryRecipeEditAction
    data class OnFinalWeightChange(val value: String) : DiaryRecipeEditAction
    data object OnSaveClick : DiaryRecipeEditAction
    data class OnConfirmSave(val scope: com.example.ezmakro.core.domain.usecase.diary.DiaryRecipeEditScope) :
        DiaryRecipeEditAction
    data object OnDismissSaveScopeDialog : DiaryRecipeEditAction
    data object OnDismissPicker : DiaryRecipeEditAction
    data object OnBackClick : DiaryRecipeEditAction
}
