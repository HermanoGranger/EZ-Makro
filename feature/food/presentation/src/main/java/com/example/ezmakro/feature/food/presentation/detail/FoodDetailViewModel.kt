package com.example.ezmakro.feature.food.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.presentation.uitext.toUiText
import com.example.ezmakro.feature.food.presentation.model.toFoodUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FoodDetailViewModel(
    private val foodId: String,
    private val repository: FoodRepository,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FoodDetailState())
    val state = _state.asStateFlow()

    private val _events = Channel<FoodDetailEvent>()
    val events = _events.receiveAsFlow()

    init {
        observeFood()
    }

    fun onAction(action: FoodDetailAction) {
        when (action) {
            FoodDetailAction.OnBackClick ->
                viewModelScope.launch { _events.send(FoodDetailEvent.NavigateBack) }

            FoodDetailAction.OnEditClick ->
                viewModelScope.launch { _events.send(FoodDetailEvent.NavigateToEdit(foodId)) }

            FoodDetailAction.OnToggleFavorite ->
                viewModelScope.launch { repository.toggleFavorite(FoodId(foodId)) }

            FoodDetailAction.OnResetToOriginalClick -> resetToOriginal()

            FoodDetailAction.OnRefetchClick ->
                _state.update { it.copy(showRefetchConfirm = true) }

            FoodDetailAction.OnRefetchDismiss ->
                _state.update { it.copy(showRefetchConfirm = false) }

            FoodDetailAction.OnRefetchConfirm -> refetch()

            FoodDetailAction.OnDeleteClick -> loadRecipeUsage()

            FoodDetailAction.OnDeleteConfirm -> delete()

            FoodDetailAction.OnDeleteDismiss ->
                _state.update { it.copy(showDeleteConfirm = false) }
        }
    }

    private fun observeFood() {
        repository
            .observeAll()
            .onEach { foods ->
                val match = foods.firstOrNull { it.id.value == foodId }
                val food = match ?: repository.getSessionItem(FoodId(foodId))
                _state.update {
                    it.copy(
                        food = food?.toFoodUi(),
                        isLoading = false,
                        isPersisted = match != null,
                    )
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Resolve which recipes use this food so the confirmation dialog can warn
     * about the consequence of deleting it. A failed lookup degrades to no
     * warning rather than blocking the delete.
     */
    private fun loadRecipeUsage() {
        viewModelScope.launch {
            val names =
                when (val result = recipeRepository.findRecipeNamesUsingFood(FoodId(foodId))) {
                    is Result.Success -> result.data
                    is Result.Error -> emptyList()
                }
            _state.update { it.copy(showDeleteConfirm = true, usedInRecipes = names) }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            _state.update { it.copy(showDeleteConfirm = false, isDeleting = true) }
            repository
                .delete(FoodId(foodId))
                .onSuccess { _events.send(FoodDetailEvent.NavigateBack) }
                .onFailure { error ->
                    _state.update { it.copy(isDeleting = false) }
                    _events.send(FoodDetailEvent.ShowMessage(error.toUiText()))
                }
        }
    }

    private fun resetToOriginal() {
        viewModelScope.launch {
            repository
                .resetToOriginal(FoodId(foodId))
                .onFailure { error -> _events.send(FoodDetailEvent.ShowMessage(error.toUiText())) }
        }
    }

    private fun refetch() {
        viewModelScope.launch {
            _state.update { it.copy(showRefetchConfirm = false, isRefetching = true) }
            repository
                .refetchFromOff(FoodId(foodId))
                .onSuccess { _state.update { it.copy(isRefetching = false) } }
                .onFailure { error ->
                    _state.update { it.copy(isRefetching = false) }
                    _events.send(FoodDetailEvent.ShowMessage(error.toUiText()))
                }
        }
    }
}
