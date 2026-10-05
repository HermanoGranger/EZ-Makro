package com.example.ezmakro.core.presentation.diary

data class DiaryEntryDetailData(
    val id: String,
    val name: String,
    val detail: String,
    val kcal: String,
    val protein: String,
    val fat: String,
    val carbs: String,
    val fiber: String,
    val isRecipe: Boolean = false,
    val isRecipeOverridden: Boolean = false,
    val isSourceRecipeOutdated: Boolean = false,
    val recipeOverriddenLabel: String = "Tagesanpassung",
    val recipeChangedLabel: String = "Rezept geändert",
)
