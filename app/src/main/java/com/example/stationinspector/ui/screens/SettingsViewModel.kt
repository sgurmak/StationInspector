package com.example.stationinspector.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationinspector.domain.repository.StationRepository
import com.example.stationinspector.domain.usecase.ImportStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

/**
 * Owns the data-management operations triggered from the Settings screen:
 * CSV import and "clear all data". Exposes a busy flag and one-shot events so
 * the host (Work) screen can surface a loading overlay and snackbars.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val importStationsUseCase: ImportStationsUseCase,
    private val stationRepository: StationRepository
) : ViewModel() {

    private companion object {
        const val TAG = "SettingsViewModel"
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    /**
     * Imports stations from an already-opened CSV [InputStream]. Opening the
     * stream from a content `Uri` is the UI layer's responsibility, so this
     * ViewModel stays free of Android `Context`.
     */
    fun importStationsFromCsv(inputStream: InputStream) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                importStationsUseCase(inputStream)
                    .onFailure { e ->
                        Log.e(TAG, "CSV import failed", e)
                        _uiEvent.emit(
                            UiEvent.ShowSnackbar(
                                "Import failed: ${e.localizedMessage ?: "Unknown error"}"
                            )
                        )
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) { stationRepository.clearAllData() }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
