package com.example.ezmakro.feature.recipes.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.repository.RecipeRepository
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
 * Lists all recipes (name-sorted from the repository). Navigation to the editor
 * or detail is emitted as [RecipeListEvent]s; data mutations happen in the
 * editor/detail view models.
 */
class RecipeListViewModel(
    private val repository: RecipeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecipeListState(isLoading = true))
    val state = _state.asStateFlow()

    private val _events = Channel<RecipeListEvent>()
    val events = _events.receiveAsFlow()

    init {
        repository
            .observeAll()
            .onEach { recipes ->
                _state.update { it.copy(recipes = recipes.map { r -> r.toRecipeUi() }, isLoading = false) }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: RecipeListAction) {
        when (action) {
            is RecipeListAction.OnRecipeClick ->
                viewModelScope.launch { _events.send(RecipeListEvent.NavigateToDetail(action.recipeId)) }

            is RecipeListAction.OnCreateClick ->
                viewModelScope.launch { _events.send(RecipeListEvent.NavigateToEditor(recipeId = null)) }

            is RecipeListAction.OnCreateQuickMealClick ->
                viewModelScope.launch { _events.send(RecipeListEvent.NavigateToEditor(recipeId = null)) }
        }
    }
}
