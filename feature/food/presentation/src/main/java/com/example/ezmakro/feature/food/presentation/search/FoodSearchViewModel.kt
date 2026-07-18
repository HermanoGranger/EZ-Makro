package com.example.ezmakro.feature.food.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.repository.FoodRepository
import com.example.ezmakro.core.domain.result.onFailure
import com.example.ezmakro.core.domain.result.onSuccess
import com.example.ezmakro.core.presentation.uitext.toUiText
import com.example.ezmakro.feature.food.presentation.model.toFoodUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FoodSearchViewModel(
    private val repository: FoodRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FoodSearchState())
    val state = _state.asStateFlow()

    private val _events = Channel<FoodSearchEvent>()
    val events = _events.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        observeFavorites()
        observeRecent()
        observeLocalResults()
    }

    fun onAction(action: FoodSearchAction) {
        when (action) {
            is FoodSearchAction.OnQueryChange -> {
                queryFlow.value = action.query
                _state.update { it.copy(query = action.query, onlineError = null) }
            }

            FoodSearchAction.OnSearchOnlineClick -> searchOnline()

            is FoodSearchAction.OnFoodClick ->
                viewModelScope.launch { _events.send(FoodSearchEvent.NavigateToDetail(action.foodId)) }

            is FoodSearchAction.OnToggleFavorite ->
                viewModelScope.launch { repository.toggleFavorite(FoodId(action.foodId)) }

            FoodSearchAction.OnScanBarcodeClick ->
                viewModelScope.launch { _events.send(FoodSearchEvent.NavigateToScanner) }

            FoodSearchAction.OnCreateOwnClick ->
                viewModelScope.launch { _events.send(FoodSearchEvent.NavigateToCreate(barcode = null)) }
        }
    }

    private fun observeFavorites() {
        repository
            .observeFavorites()
            .onEach { foods -> _state.update { it.copy(favorites = foods.map { f -> f.toFoodUi() }) } }
            .launchIn(viewModelScope)
    }

    private fun observeRecent() {
        repository
            .observeRecent()
            .onEach { foods -> _state.update { it.copy(recent = foods.map { f -> f.toFoodUi() }) } }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLocalResults() {
        queryFlow
            .flatMapLatest { query -> repository.searchLocal(query) }
            .onEach { foods -> _state.update { it.copy(localResults = foods.map { f -> f.toFoodUi() }) } }
            .launchIn(viewModelScope)
    }

    private fun searchOnline() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSearchingOnline = true, onlineError = null) }
            repository
                .searchRemote(query)
                .onSuccess { foods ->
                    _state.update {
                        it.copy(
                            isSearchingOnline = false,
                            onlineResults = foods.map { f -> f.toFoodUi() },
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(isSearchingOnline = false, onlineError = error.toUiText())
                    }
                }
        }
    }
}
