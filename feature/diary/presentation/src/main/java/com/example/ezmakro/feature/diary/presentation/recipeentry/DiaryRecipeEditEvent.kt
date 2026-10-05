package com.example.ezmakro.feature.diary.presentation.recipeentry

import com.example.ezmakro.core.presentation.uitext.UiText

sealed interface DiaryRecipeEditEvent {
    data object Saved : DiaryRecipeEditEvent

    data object NavigateBack : DiaryRecipeEditEvent

    data class ShowMessage(
        val message: UiText,
    ) : DiaryRecipeEditEvent
}
