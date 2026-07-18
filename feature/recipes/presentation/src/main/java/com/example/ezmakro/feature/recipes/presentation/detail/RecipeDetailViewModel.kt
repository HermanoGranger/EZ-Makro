package com.example.ezmakro.feature.recipes.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.presentation.uitext.toUiText
import com.example.ezmakro.feature.recipes.presentation.model.toRecipeUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shows a single recipe's computed nutrition (total / per-portion / per-100 g,
 * the latter only when a final weight was measured per ADR-003) and offers
 * edit, duplicate and delete actions.
 */
class RecipeDetailViewModel(
    private val recipeId: String,
    private val repository: RecipeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecipeDetailState())
    val state = _state.asStateFlow()

    private val _events = Channel<RecipeDetailEvent>()
    val events = _events.receiveAsFlow()

    init {
        repository
            .observeById(RecipeId(recipeId))
            .onEach { recipe ->
                _state.update { it.copy(recipe = recipe?.toRecipeUi(), isLoading = false) }
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
                }

            RecipeDetailAction.OnDeleteClick ->
                viewModelScope.launch {
                    repository
                        .delete(RecipeId(recipeId))
                        .onSuccess { _events.send(RecipeDetailEvent.NavigateBack) }
                        .onFailure { error -> _state.update { it.copy(deleteError = error.toUiText()) } }
                }

            RecipeDetailAction.OnBackClick ->
                viewModelScope.launch { _events.send(RecipeDetailEvent.NavigateBack) }
        }
    }
}
