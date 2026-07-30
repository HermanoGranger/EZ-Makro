package com.example.ezmakro.feature.recipes.presentation.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.RecipeId
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
 *
 * When opened from the diary ([addToDiaryDate] != null) selecting a recipe sets
 * [RecipeListState.pendingAddRecipeId], which the UI uses to show the
 * slot/portions sheet.
 */
class RecipeListViewModel(
    private val repository: RecipeRepository,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val addToDiaryDate: String? = savedStateHandle.get<String>("addToDiaryDate")?.takeIf { it.isNotEmpty() }
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
            is RecipeListAction.OnRecipeClick -> {
                if (addToDiaryDate != null) {
                    _state.update { it.copy(pendingAddRecipeId = action.recipeId) }
                } else {
                    viewModelScope.launch { _events.send(RecipeListEvent.NavigateToDetail(action.recipeId)) }
                }
            }

            is RecipeListAction.OnCreateClick ->
                viewModelScope.launch { _events.send(RecipeListEvent.NavigateToEditor(recipeId = null)) }

            is RecipeListAction.OnToggleFavorite ->
                viewModelScope.launch { repository.toggleFavorite(RecipeId(action.recipeId)) }

            RecipeListAction.OnDismissAddToDiary ->
                _state.update { it.copy(pendingAddRecipeId = null) }

            is RecipeListAction.OnConfirmAddToDiary -> {
                val recipeId = _state.value.pendingAddRecipeId ?: return
                val date = addToDiaryDate ?: return
                _state.update { it.copy(pendingAddRecipeId = null) }
                viewModelScope.launch {
                    _events.send(
                        RecipeListEvent.AddToDiary(
                            recipeId = recipeId,
                            date = date,
                            slot = action.slot,
                            amount = action.amount,
                            isGrams = action.isGrams,
                        ),
                    )
                }
            }
        }
    }
}
