package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Recipe(
    val id: RecipeId,
    val name: String,
    val ingredients: List<RecipeIngredient>,
    val finalWeight: FinalWeight?,
    val portions: Int,
    val scale: Double = 1.0,
    val category: String? = null,
    val isMealPrep: Boolean = false,
    val lastModifiedAt: Long = 0L,
) {
    init {
        require(name.isNotBlank()) { "Recipe name must not be blank" }
        require(portions > 0) { "portions must be positive, was $portions" }
        require(scale > 0.0) { "scale must be positive, was $scale" }
        require(ingredients.isNotEmpty()) { "Recipe must have at least one ingredient" }
    }
}
