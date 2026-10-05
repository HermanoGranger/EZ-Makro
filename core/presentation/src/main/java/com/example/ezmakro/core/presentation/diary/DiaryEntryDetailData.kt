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
)
