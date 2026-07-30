package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Recipe(
    val id: RecipeId,
    val name: String,
    val ingredients: List<RecipeIngredient>,
    val finalWeight: FinalWeight?,
    val portions: Int,
    val category: String? = null,
    val lastModifiedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
) {
    init {
        require(name.isNotBlank()) { "Recipe name must not be blank" }
        require(portions > 0) { "portions must be positive, was $portions" }
        require(ingredients.isNotEmpty()) { "Recipe must have at least one ingredient" }
        lastUsedAt?.let {
            require(it >= 0L) { "lastUsedAt must be a non-negative epoch-milli timestamp, was $it" }
        }
    }
}
