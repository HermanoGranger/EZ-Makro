package com.example.ezmakro.feature.recipes.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.calculation.nutrition
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.presentation.uitext.toUiText
import com.example.ezmakro.core.presentation.util.formatValue
import com.example.ezmakro.feature.recipes.presentation.model.toRecipeUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecipeDetailViewModel(
    private val recipeId: String,
    private val repository: RecipeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecipeDetailState())
    val state = _state.asStateFlow()

    private val _events = Channel<RecipeDetailEvent>()
    val events = _events.receiveAsFlow()

    private var latestRecipe: Recipe? = null

    init {
        repository
            .observeById(RecipeId(recipeId))
            .onEach { recipe ->
                latestRecipe = recipe
                _state.update {
                    it.copy(
                        recipe = recipe?.toRecipeUi(),
                        ingredients =
                            recipe
                                ?.ingredients
                                ?.mapIndexed { index, ing ->
                                    val nutrition = ing.nutrition()
                                    val rawWeight = ing.rawWeight
                                    val food = ing.food
                                    val servingCount = ing.servingCount
                                    val servingLabel = food?.servingUnit?.label
                                    val amountDisplay =
                                        when {
                                            rawWeight != null -> "${rawWeight.grams.formatValue()} g"
                                            servingCount != null && servingLabel != null ->
                                                "${servingCount.formatValue()} × $servingLabel"
                                            servingCount != null && ing.recipeSnapshot != null ->
                                                "${servingCount.formatValue()} Portionen"
                                            else -> ""
                                        }
                                    IngredientDetailUi(
                                        name = ing.food?.name ?: ing.recipeSnapshot?.recipeName.orEmpty(),
                                        amountDisplay = amountDisplay,
                                        kcal = nutrition.kcal.formatValue(),
                                        proteinG = nutrition.proteinG.formatValue(),
                                        fatG = nutrition.fatG.formatValue(),
                                        carbsG = nutrition.carbsG.formatValue(),
                                        fiberG = nutrition.fiberG.formatValue(),
                                    )
                                }.orEmpty(),
                        isLoading = false,
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: RecipeDetailAction) {
        when (action) {
            RecipeDetailAction.OnEditClick ->
                viewModelScope.launch { _events.send(RecipeDetailEvent.NavigateToEditor(recipeId)) }

            RecipeDetailAction.OnDuplicateClick ->
                viewModelScope.launch {
                    repository
                        .duplicate(RecipeId(recipeId))
                        .onSuccess { _events.send(RecipeDetailEvent.NavigateBack) }
                        .onFailure { error -> _state.update { it.copy(duplicateError = error.toUiText()) } }
                }

            RecipeDetailAction.OnDeleteClick ->
                viewModelScope.launch {
                    repository
                        .delete(RecipeId(recipeId))
                        .onSuccess { _events.send(RecipeDetailEvent.NavigateBack) }
                        .onFailure { error -> _state.update { it.copy(deleteError = error.toUiText()) } }
                }

            RecipeDetailAction.OnToggleFavorite ->
                viewModelScope.launch { repository.toggleFavorite(RecipeId(recipeId)) }

            RecipeDetailAction.OnBackClick ->
                viewModelScope.launch { _events.send(RecipeDetailEvent.NavigateBack) }

            is RecipeDetailAction.OnIngredientClick ->
                _state.update {
                    it.copy(
                        expandedIngredientIndex =
                            if (it.expandedIngredientIndex == action.index) null else action.index,
                    )
                }
        }
    }
}
