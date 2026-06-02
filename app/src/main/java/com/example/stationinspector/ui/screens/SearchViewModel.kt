package com.example.stationinspector.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationinspector.data.repository.MapyCzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

/**
 * Owns the location search box on the Map screen: the query and the debounced
 * geocoding results. Adding a result to the route is the RouteViewModel's job;
 * the screen wires the two together.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mapyCzRepository: MapyCzRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    fun clearSearch() { _searchQuery.value = "" }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchState: StateFlow<SearchUiState> = _searchQuery
        .debounce(300L)
        .transformLatest { query ->
            if (query.isBlank()) {
                emit(SearchUiState.Idle)
            } else {
                emit(SearchUiState.Loading)
                try {
                    val results = mapyCzRepository.searchLocation(query)
                    emit(SearchUiState.Success(results))
                } catch (e: Exception) {
                    emit(SearchUiState.Error(e.localizedMessage ?: "Unknown error"))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState.Idle
        )
}
