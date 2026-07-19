package com.example.ezmakro.feature.food.presentation.search

import com.example.ezmakro.core.presentation.uitext.UiText
import com.example.ezmakro.feature.food.presentation.model.FoodUi

/**
 * State for the food picker. When [query] is blank the UI shows the favorites
 * (and later recent) sections; when the user types, local FTS results appear
 * and an explicit online search can be triggered (OFF rate limits, so it is
 * never automatic).
 *
 * When opened from the diary ([addToDiaryDate] != null) selecting a food sets
 * [pendingAddFoodId], which the UI uses to show the slot/amount sheet.
 */
data class FoodSearchState(
    val query: String = "",
    val favorites: List<FoodUi> = emptyList(),
    val recent: List<FoodUi> = emptyList(),
    val localResults: List<FoodUi> = emptyList(),
    val onlineResults: List<FoodUi> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val onlineError: UiText? = null,
    val pendingAddFoodId: String? = null,
) {
    val isQuerying: Boolean get() = query.isNotBlank()
}
