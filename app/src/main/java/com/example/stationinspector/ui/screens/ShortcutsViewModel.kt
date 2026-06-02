package com.example.stationinspector.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationinspector.domain.model.Shortcut
import com.example.stationinspector.domain.repository.ShortcutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the user's shortcut chips (Home, Work, custom presets) and their CRUD.
 * Seeds the Home/Work defaults on first run.
 */
@HiltViewModel
class ShortcutsViewModel @Inject constructor(
    private val shortcutRepository: ShortcutRepository
) : ViewModel() {

    val shortcuts: StateFlow<List<ShortcutUiModel>> = shortcutRepository.observeShortcuts()
        .map { domainList -> domainList.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                shortcutRepository.clearOldBlankShortcuts()
                shortcutRepository.ensureDefaults()
            }
        }
    }

    fun updateShortcut(id: String, poi: PoiItem?, customName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            shortcutRepository.updateShortcut(
                id = id,
                location = poi?.toPoiLocation(),
                customName = customName,
                isNew = poi == null
            )
        }
    }

    fun createNewShortcut(poi: PoiItem?, customName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            shortcutRepository.insertPreset(poi?.toPoiLocation(), customName)
        }
    }

    fun deleteShortcut(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (id == Shortcut.ID_HOME || id == Shortcut.ID_WORK) {
                // Keep Home and Work entries — just clear their bound location.
                shortcutRepository.updateShortcut(id, location = null, customName = null, isNew = true)
            } else {
                shortcutRepository.deleteShortcut(id)
            }
        }
    }
}
