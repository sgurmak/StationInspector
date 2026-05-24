package com.example.stationinspector.ui.screens

import androidx.compose.runtime.Immutable
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationinspector.data.local.dao.PoiDao
import com.example.stationinspector.data.local.dao.ShortcutDao
import com.example.stationinspector.data.local.dao.StationDao
import com.example.stationinspector.data.local.entity.PoiEntity
import com.example.stationinspector.data.local.entity.ShortcutEntity
import com.example.stationinspector.data.repository.MapyCzRepository
import com.example.stationinspector.domain.model.Station
import com.example.stationinspector.domain.model.StationStatus
import com.example.stationinspector.domain.repository.RouteRepository
import com.example.stationinspector.domain.repository.StationRepository
import com.example.stationinspector.utils.PolylineUtils
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import com.example.stationinspector.data.local.AppDatabase
import androidx.room.withTransaction

// ── UI model ───────────────────────────────────────────────────────────────────

sealed interface RouteListItem {
    val stableId: String
    val id: String
    val name: String
    val latitude: Double
    val longitude: Double
    val isStation: Boolean
    val isHidden: Boolean
    val orderIndex: Int
}

data class StationItem(
    val station: StationWithCounts,
    override val isHidden: Boolean = false,
    override val orderIndex: Int = 0
) : RouteListItem {
    override val stableId: String get() = "station_${station.id}"
    override val id: String get() = station.id
    override val name: String get() = station.name
    override val latitude: Double get() = station.latitude
    override val longitude: Double get() = station.longitude
    override val isStation: Boolean = true
}

data class PoiItem(
    override val id: String,
    override val name: String,
    val city: String?,
    val address: String?,
    val region: String?,
    override val latitude: Double,
    override val longitude: Double,
    override val isHidden: Boolean = false,
    val uniqueId: String? = null,
    override val orderIndex: Int = 0
) : RouteListItem {
    override val stableId: String get() = "poi_${uniqueId ?: id}"
    override val isStation: Boolean = false
}

data class StationWithCounts(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** CLIENT_REPORT photos */
    val photoCount: Int,
    /** INTERNAL_DEFECT photos */
    val issueCount: Int
)

@Immutable
data class DailyRouteInfo(
    val totalDistanceKm: Double,
    val totalTimeMins: Int,
    val waypointCount: Int,
    val polylinePoints: List<GeoPoint>
) {
    val formattedDuration: String
        get() = if (totalTimeMins >= 60) "${totalTimeMins / 60}h ${totalTimeMins % 60}min" else "${totalTimeMins}min"
}

@Immutable
data class ShortcutUiModel(
    val id: String,
    val label: String,
    val customName: String?,
    val poiItem: PoiItem?,
    val isNew: Boolean,
    val isRoundTrip: Boolean,
    val entity: ShortcutEntity
)

sealed interface SearchUiState {
    data object Idle    : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<PoiItem>) : SearchUiState
    data class Error(val message: String)          : SearchUiState
}

// ── ViewModel ──────────────────────────────────────────────────────────────────

@HiltViewModel
class StationListViewModel @Inject constructor(
    private val stationRepository: StationRepository,
    private val routeRepository: RouteRepository,
    private val mapyCzRepository: MapyCzRepository,
    private val shortcutDao: ShortcutDao,
    private val poiDao: PoiDao,
    private val stationDao: StationDao,
    private val database: AppDatabase,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {
    private val gson = Gson()
    
    companion object {
        val HOME_ROUND_TRIP_ENABLED = booleanPreferencesKey("home_round_trip_enabled")
        private const val SHORTCUT_ID_HOME = "1"
        private const val SHORTCUT_ID_WORK = "2"
        private const val TAG = "StationListViewModel"
    }

    val isRoundTripEnabled: StateFlow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[HOME_ROUND_TRIP_ENABLED] ?: false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setRoundTripEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[HOME_ROUND_TRIP_ENABLED] = enabled
            }
            val date = _selectedDate.value
            if (date != null) {
                withContext(Dispatchers.IO) {
                    rebuildHomePointsAndIndices(date, enabled)
                }
            }
        }
    }
    
    val shortcuts: StateFlow<List<ShortcutUiModel>> = shortcutDao.getAllShortcuts()
        .map { entities ->
            entities.map { entity ->
                val poiItem = entity.poiItemJson?.let {
                    try {
                        gson.fromJson(it, PoiItem::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                ShortcutUiModel(
                    id = entity.id,
                    label = entity.label,
                    customName = entity.customName,
                    poiItem = poiItem,
                    isNew = entity.isNew,
                    isRoundTrip = entity.isRoundTrip,
                    entity = entity
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Selected date ──────────────────────────────────────────────────────────
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    // ── Available dates derived from the DB ────────────────────────────────────
    private val _availableDates = MutableStateFlow<List<LocalDate>>(emptyList())
    val availableDates: StateFlow<List<LocalDate>> = _availableDates.asStateFlow()

    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())

    // ── Route items — date-atomic flow ────────────────────────────────────────
    //
    // combine() is nested inside flatMapLatest so that when the date changes,
    // flatMapLatest atomically cancels the previous (stations + pois + hiddenIds)
    // subscription before starting a new one.  This prevents a stale-pois
    // double-emission that top-level combine() would produce on date switches.
    //
    // Pairing each emission with its date lets the Screen use the date as a
    // Compose key() that is always coherent with the emitted list contents.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _routeStateFlow: StateFlow<Pair<LocalDate?, List<RouteListItem>>> =
        _selectedDate
            .flatMapLatest { date ->
                if (date == null) {
                    flowOf(null to emptyList<RouteListItem>())
                } else {
                    combine(
                        stationRepository.getAllStationsWithSplitCounts(),
                        poiDao.getPoisForDate(date),
                        _hiddenIds
                    ) { allStations, pois, hiddenIds ->
                        val stationItems = allStations
                            .filter { it.inspectionDate == date }
                            .map { domain ->
                                StationItem(
                                    station = StationWithCounts(
                                        id         = domain.id.toString(),
                                        name       = domain.name,
                                        latitude   = domain.latitude,
                                        longitude  = domain.longitude,
                                        photoCount = domain.regularCount,
                                        issueCount = domain.issueCount
                                    ),
                                    orderIndex = domain.orderIndex,
                                    isHidden   = hiddenIds.contains(domain.id.toString())
                                )
                            }

                        val poiItems = pois.map { entity ->
                            PoiItem(
                                id       = entity.id,
                                uniqueId = entity.id,
                                name     = entity.name,
                                city     = entity.city,
                                address  = entity.address,
                                region   = entity.region,
                                latitude = entity.latitude,
                                longitude = entity.longitude,
                                orderIndex = entity.orderIndex,
                                isHidden   = hiddenIds.contains(entity.id)
                            )
                        }

                        date to (stationItems + poiItems).sortedBy { it.orderIndex }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null to emptyList())

    // Public projections of _routeStateFlow.  Both update in the same emission,
    // so stableRouteDate is always consistent with routeItems and safe to use
    // as a Compose key() in the list host.
    val routeItems: StateFlow<List<RouteListItem>> = _routeStateFlow
        .map { (_, items) -> items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stableRouteDate: StateFlow<LocalDate?> = _routeStateFlow
        .map { (date, _) -> date }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isExportButtonEnabled = MutableStateFlow(false)
    val isExportButtonEnabled: StateFlow<Boolean> = _isExportButtonEnabled.asStateFlow()

    private val _routeInfo = MutableStateFlow(DailyRouteInfo(0.0, 0, 0, emptyList()))
    val routeInfo: StateFlow<DailyRouteInfo> = _routeInfo.asStateFlow()

    // ── Search State ───────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    private val _editingPoi = MutableStateFlow<RouteListItem?>(null)
    val editingPoi: StateFlow<RouteListItem?> = _editingPoi.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    sealed interface UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun optimizeRoute() {
        val activeItems = routeItems.value.filter { !it.isHidden }
        val validItems = activeItems.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        
        if (validItems.size < 3) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowSnackbar("At least 3 valid locations required to optimize"))
            }
            return
        }

        if (_isOptimizing.value) return
        _isOptimizing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = routeRepository.optimizeAndFetchGeometry(validItems)
                result.onSuccess { data ->
                    val hiddenItems = routeItems.value.filter { it.isHidden }
                    val newOrder = data.reorderedItems + hiddenItems
                    val stationOrders = mutableListOf<Pair<Long, Int>>()
                    val poiOrders     = mutableListOf<Pair<String, Int>>()

                    newOrder.forEachIndexed { index, item ->
                        if (item is StationItem) {
                            stationOrders.add(item.station.id.toLong() to index)
                        } else if (item is PoiItem) {
                            poiOrders.add(item.id to index)
                        }
                    }

                    stationDao.updateStationOrders(stationOrders)
                    poiDao.updatePoiOrders(poiOrders)
                }.onFailure { e ->
                    // API/network rejection — expected failure path.
                    Log.w(TAG, "Route optimization returned a failure result", e)
                    _uiEvent.emit(UiEvent.ShowSnackbar(e.message ?: "Optimization failed"))
                }
            } catch (e: Exception) {
                // Unexpected DB or runtime failure after a successful API call.
                Log.e(TAG, "Unexpected error during route optimization", e)
                _uiEvent.emit(UiEvent.ShowSnackbar("Optimization failed unexpectedly"))
            } finally {
                // Always release the lock — even if an exception was thrown above.
                _isOptimizing.value = false
            }
        }
    }

    fun onInspectionConfirmed() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSnackbar("Inspection completed successfully"))
        }
    }

    fun startEditingPoi(poi: RouteListItem) {
        _editingPoi.value = poi
    }

    fun cancelEditingPoi() {
        _editingPoi.value = null
    }

    fun saveEditedPoi(poi: RouteListItem, lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            // Capture date before entering the background thread; bail if unset.
            val date = _selectedDate.value ?: run {
                Log.w(TAG, "saveEditedPoi called with no selected date — aborting write")
                return@launch
            }
            try {
                if (poi is StationItem) {
                    stationRepository.updateStationCoordinates(poi.id.toLong(), lat, lon)
                } else if (poi is PoiItem) {
                    val currentShortcuts = shortcutDao.getAllShortcuts().first()
                    currentShortcuts.forEach { shortcut ->
                        if (shortcut.poiItemJson != null) {
                            val parsed = gson.fromJson(shortcut.poiItemJson, PoiItem::class.java)
                            if (parsed.id == poi.id) {
                                val updatedJson = gson.toJson(parsed.copy(latitude = lat, longitude = lon))
                                shortcutDao.updateShortcut(shortcut.id, updatedJson, shortcut.customName, shortcut.isNew)
                            }
                        }
                    }
                    val entity = PoiEntity(
                        id             = poi.id,
                        name           = poi.name,
                        city           = poi.city,
                        address        = poi.address,
                        region         = poi.region,
                        latitude       = lat,
                        longitude      = lon,
                        inspectionDate = date,
                        orderIndex     = poi.orderIndex
                    )
                    poiDao.insertPoi(entity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save edited coordinates for '${poi.name}'", e)
            }
        }
        // Dialog is dismissed immediately; the DB write continues in the background.
        _editingPoi.value = null
    }

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
        
    fun addPoiToRoute(poi: PoiItem) {
        val date = _selectedDate.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            database.withTransaction {
                insertPoiAtCorrectOrderIndex(poi, date)
            }
        }
        _searchQuery.value = ""
    }

    fun addShortcutToRoute(shortcutId: String, poi: PoiItem) {
        val date = _selectedDate.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val finalPoi = when (shortcutId) {
                SHORTCUT_ID_HOME -> poi.copy(name = "Home")
                SHORTCUT_ID_WORK -> poi.copy(name = "Work")
                else             -> poi
            }

            if (shortcutId == SHORTCUT_ID_HOME) {
                rebuildHomePointsAndIndices(date, isRoundTripEnabled.value, finalPoi)
            } else {
                database.withTransaction {
                    insertPoiAtCorrectOrderIndex(finalPoi, date)
                }
            }
        }
        _searchQuery.value = ""
    }

    private suspend fun insertPoiAtCorrectOrderIndex(poi: PoiItem, date: LocalDate) {
        val currentItems = routeItems.value
        val lastItem = currentItems.lastOrNull()
        val isRoundTrip = isRoundTripEnabled.value
        if (isRoundTrip && lastItem != null && lastItem.name == "Home" && lastItem is PoiItem) {
            val targetOrderIndex = lastItem.orderIndex
            val newPoiEntity = PoiEntity(
                id = java.util.UUID.randomUUID().toString(),
                name = poi.name,
                city = poi.city,
                address = poi.address,
                region = poi.region,
                latitude = poi.latitude,
                longitude = poi.longitude,
                inspectionDate = date,
                orderIndex = targetOrderIndex
            )
            poiDao.insertPoi(newPoiEntity)
            poiDao.updatePoiOrder(lastItem.id, targetOrderIndex + 1)
        } else {
            val maxOrder = currentItems.maxOfOrNull { it.orderIndex } ?: -1
            val newPoiEntity = PoiEntity(
                id = java.util.UUID.randomUUID().toString(),
                name = poi.name,
                city = poi.city,
                address = poi.address,
                region = poi.region,
                latitude = poi.latitude,
                longitude = poi.longitude,
                inspectionDate = date,
                orderIndex = maxOrder + 1
            )
            poiDao.insertPoi(newPoiEntity)
        }
    }

    private suspend fun rebuildHomePointsAndIndices(
        date: LocalDate,
        isRoundTrip: Boolean,
        newHomePoi: PoiItem? = null,
        clearHome: Boolean = false
    ) {
        database.withTransaction {
            val stations = stationDao.getAllStationsSync().filter { it.inspectionDate == date }
            val pois = poiDao.getPoisForDateSync(date)
            
            val existingHome = if (clearHome) null else pois.firstOrNull { it.name == "Home" }
            val homePoi = newHomePoi ?: existingHome?.let {
                PoiItem(
                    id = it.id,
                    name = it.name,
                    city = it.city,
                    address = it.address,
                    region = it.region,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            }
            
            poiDao.deletePoisByNameAndDate("Home", date)
            
            if (homePoi != null) {
                val nonHomePois = pois.filter { it.name != "Home" }
                val nonHomeItems = (stations.map {
                    StationItem(
                        station = StationWithCounts(
                            id = it.id.toString(),
                            name = it.name,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            photoCount = 0,
                            issueCount = 0
                        ),
                        orderIndex = it.orderIndex
                    )
                } + nonHomePois.map {
                    PoiItem(
                        id = it.id,
                        name = it.name,
                        city = it.city,
                        address = it.address,
                        region = it.region,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        orderIndex = it.orderIndex
                    )
                }).sortedBy { it.orderIndex }
                
                val startEntity = PoiEntity(
                    id = homePoi.id.takeIf { it.isNotBlank() && it != "NEW" } ?: java.util.UUID.randomUUID().toString(),
                    name = "Home",
                    city = homePoi.city,
                    address = homePoi.address,
                    region = homePoi.region,
                    latitude = homePoi.latitude,
                    longitude = homePoi.longitude,
                    inspectionDate = date,
                    orderIndex = 0
                )
                poiDao.insertPoi(startEntity)
                
                nonHomeItems.forEachIndexed { idx, item ->
                    val newIdx = idx + 1
                    when (item) {
                        is StationItem -> stationDao.updateStationOrder(item.station.id.toLong(), newIdx)
                        is PoiItem -> poiDao.updatePoiOrder(item.id, newIdx)
                    }
                }
                
                if (isRoundTrip) {
                    val endEntity = PoiEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "Home",
                        city = homePoi.city,
                        address = homePoi.address,
                        region = homePoi.region,
                        latitude = homePoi.latitude,
                        longitude = homePoi.longitude,
                        inspectionDate = date,
                        orderIndex = nonHomeItems.size + 1
                    )
                    poiDao.insertPoi(endEntity)
                }
            } else {
                val remainingPois = pois.filter { it.name != "Home" }
                val remainingItems = (stations.map {
                    StationItem(
                        station = StationWithCounts(
                            id = it.id.toString(),
                            name = it.name,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            photoCount = 0,
                            issueCount = 0
                        ),
                        orderIndex = it.orderIndex
                    )
                } + remainingPois.map {
                    PoiItem(
                        id = it.id,
                        name = it.name,
                        city = it.city,
                        address = it.address,
                        region = it.region,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        orderIndex = it.orderIndex
                    )
                }).sortedBy { it.orderIndex }
                
                remainingItems.forEachIndexed { idx, item ->
                    when (item) {
                        is StationItem -> stationDao.updateStationOrder(item.station.id.toLong(), idx)
                        is PoiItem -> poiDao.updatePoiOrder(item.id, idx)
                    }
                }
            }
        }
    }

    /**
     * Persists the visual drag-and-drop order back to the database so that
     * the next Room emission reflects the user's reordering rather than
     * reverting to the previous order.
     */
    fun reorderItems(items: List<RouteListItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            database.withTransaction {
                val isRoundTrip = isRoundTripEnabled.value
                val hasHome = items.any { it.name == "Home" }
                
                val adjustedList = if (hasHome) {
                    val homePoints = items.filter { it.name == "Home" }
                    val startHome = homePoints.first()
                    val endHome = if (homePoints.size >= 2) homePoints.last() else null
                    val middleItems = items.filter { it.name != "Home" }
                    
                    val result = mutableListOf<RouteListItem>()
                    result.add(startHome)
                    result.addAll(middleItems)
                    if (isRoundTrip && endHome != null) {
                        result.add(endHome)
                    }
                    result
                } else {
                    items
                }

                val stationOrders = mutableListOf<Pair<Long, Int>>()
                val poiOrders     = mutableListOf<Pair<String, Int>>()
                
                adjustedList.forEachIndexed { index, item ->
                    when (item) {
                        is StationItem -> stationOrders.add(item.station.id.toLong() to index)
                        is PoiItem     -> poiOrders.add(item.id to index)
                    }
                }
                
                if (stationOrders.isNotEmpty()) stationDao.updateStationOrders(stationOrders)
                if (poiOrders.isNotEmpty())     poiDao.updatePoiOrders(poiOrders)
            }
        }
    }

    fun toggleHidePoi(id: String) {
        _hiddenIds.update { if (id in it) it - id else it + id }
    }

    fun deletePoi(id: String) {
        val date = _selectedDate.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pois = poiDao.getPoisForDateSync(date)
            val poiToDelete = pois.firstOrNull { it.id == id }
            if (poiToDelete != null && poiToDelete.name == "Home") {
                rebuildHomePointsAndIndices(date, false, null, clearHome = true)
            } else {
                poiDao.deletePoi(id)
                rebuildHomePointsAndIndices(date, isRoundTripEnabled.value)
            }
        }
    }

    fun updateShortcut(id: String, poi: PoiItem?, customName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val isNew = poi == null
            val json = poi?.let { gson.toJson(it) }
            shortcutDao.updateShortcut(id, json, customName, isNew)
        }
    }

    fun createNewShortcut(poi: PoiItem?, customName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = java.util.UUID.randomUUID().toString()
            val isNew = poi == null
            val json = poi?.let { gson.toJson(it) }
            shortcutDao.insertShortcut(ShortcutEntity(id, "Preset", customName, json, isNew))
        }
    }

    fun deleteShortcut(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (id == SHORTCUT_ID_HOME || id == SHORTCUT_ID_WORK) {
                // Keep Home and Work but clear them
                shortcutDao.updateShortcut(id, null, null, true)
            } else {
                shortcutDao.deleteShortcut(id)
            }
        }
    }

    // ── Scroll-position persistence ───────────────────────────────────────────
    // In-memory map keyed by date; survives tab switches and back-navigation.
    // Cleared only on ViewModel destruction (process death / low-memory kill).
    private val _scrollPositionByDate = mutableMapOf<LocalDate, Pair<Int, Int>>()

    /** Persist the scroll position for a specific date. */
    fun saveScrollPositionForDate(date: LocalDate, index: Int, offset: Int) {
        if (index > 0 || offset > 0) {
            _scrollPositionByDate[date] = index to offset
        } else {
            // Position 0 is the default — no need to store it explicitly.
            _scrollPositionByDate.remove(date)
        }
    }

    /** Returns the saved (index, scrollOffset) pair for [date], or (0, 0) if none. */
    fun getScrollPositionForDate(date: LocalDate?): Pair<Int, Int> =
        date?.let { _scrollPositionByDate[it] } ?: (0 to 0)

    // ── Loading / busy flag ────────────────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _mapExpandedByDate = MutableStateFlow<Map<LocalDate, Boolean>>(emptyMap())
    val mapExpandedByDate: StateFlow<Map<LocalDate, Boolean>> = _mapExpandedByDate.asStateFlow()

    fun toggleMapExpanded() {
        val date = _selectedDate.value ?: return
        val current = _mapExpandedByDate.value[date] ?: true
        _mapExpandedByDate.update { it + (date to !current) }
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                shortcutDao.clearOldBlankShortcuts()
                if (shortcutDao.getShortcutCount() == 0) {
                    shortcutDao.insertShortcuts(listOf(
                        ShortcutEntity(SHORTCUT_ID_HOME, "Home", null, null, true),
                        ShortcutEntity(SHORTCUT_ID_WORK, "Work", null, null, true)
                    ))
                }
                stationRepository.seedCoordinatesIfMissing()
            }
            // Uses the JOIN query so photo/issue counts stay in sync with
            // the photos table; Room re-emits on any change to either table.
            stationRepository.getAllStationsWithSplitCounts().collect { rows ->
                val dates = rows
                    .mapNotNull { it.inspectionDate }
                    .distinct()
                    .sorted()
                _availableDates.value = dates

                val current = _selectedDate.value
                val today = LocalDate.now()
                _selectedDate.value = when {
                    dates.isEmpty()                     -> today
                    current != null && current in dates -> current
                    today in dates                      -> today
                    else -> {
                        val closestPast = dates.filter { it < today }.maxOrNull()
                        val closestFuture = dates.filter { it > today }.minOrNull()
                        closestPast ?: closestFuture
                    }
                }
            }
        }
        
        viewModelScope.launch {
            routeItems.collectLatest { items ->
                calculateDailyRoute(items)
                _isExportButtonEnabled.value = items.filterIsInstance<StationItem>().sumOf { it.station.photoCount + it.station.issueCount } > 0
            }
        }
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

    // ── Clear all data ─────────────────────────────────────────────────────────
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

    // ── Internal helpers ───────────────────────────────────────────────────────

    private suspend fun calculateDailyRoute(dailyItems: List<RouteListItem>) {
        withContext(Dispatchers.IO) {
            val activeItems = dailyItems.filter { !it.isHidden }
            if (activeItems.size < 2) {
                _routeInfo.value = DailyRouteInfo(0.0, 0, 0, emptyList())
                return@withContext
            }

            var totalDistance = 0.0
            var totalTime = 0L
            val polylinePoints = mutableListOf<GeoPoint>()

            val validItems = mutableListOf<RouteListItem>()

            for (item in activeItems) {
                ensureActive()
                var lat = item.latitude
                var lon = item.longitude
                if (lat == 0.0 || lon == 0.0) {
                    if (item is StationItem) {
                        val coords = routeRepository.fetchAndSaveCoordinates(item.id.toLong(), item.name)
                        if (coords != null) {
                            lat = coords.first
                            lon = coords.second
                        }
                    }
                }
                if (lat != 0.0 && lon != 0.0) {
                    if (item is StationItem) {
                        validItems.add(StationItem(item.station.copy(latitude = lat, longitude = lon)))
                    } else if (item is PoiItem) {
                        validItems.add(item.copy(latitude = lat, longitude = lon))
                    }
                }
            }

            if (validItems.isNotEmpty()) {
                polylinePoints.add(GeoPoint(validItems.first().latitude, validItems.first().longitude))
            }

            for ((s1, s2) in validItems.zipWithNext()) {
                ensureActive()
                try {
                    val segment = routeRepository.getRouteSegment(s1.latitude, s1.longitude, s2.latitude, s2.longitude)
                    totalDistance += segment.distanceMeters
                    totalTime += segment.durationSeconds
                    polylinePoints.addAll(PolylineUtils.decode(segment.geometry))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Route segment fetch failed between '${s1.name}' and '${s2.name}'", e)
                }
            }

            val totalDistanceKm = totalDistance / 1000.0
            val totalTimeMins = (totalTime / 60).toInt()

            _routeInfo.value = DailyRouteInfo(
                totalDistanceKm = totalDistanceKm,
                totalTimeMins = totalTimeMins,
                waypointCount = validItems.count { it.name != "Home" },
                polylinePoints = polylinePoints.toList()
            )
        }
    }

    private fun cleanStationName(rawName: String): String {
        var name = rawName
        var parentheses = ""

        // Step 1: Extract and protect parenthetical suffixes (e.g. "(zastávka)")
        val parenRegex = "\\((.*?)\\)".toRegex()
        val parenMatch = parenRegex.find(name)
        if (parenMatch != null) {
            parentheses = parenMatch.value
            name = name.replace(parenMatch.value, "")
        }

        // Step 2: Strip domain-specific abbreviations
        name = name.replace("žst.", "").replace("os.n.", "")

        // Step 3: Truncate at the first dash separator (" - " or "- ")
        val dashSpaceIndex = name.indexOf("- ")
        val spaceDashSpaceIndex = name.indexOf(" - ")

        val cutIndex = when {
            dashSpaceIndex != -1 && spaceDashSpaceIndex != -1 -> minOf(dashSpaceIndex, spaceDashSpaceIndex)
            dashSpaceIndex != -1 -> dashSpaceIndex
            spaceDashSpaceIndex != -1 -> spaceDashSpaceIndex
            else -> -1
        }

        if (cutIndex != -1) {
            name = name.substring(0, cutIndex)
        }

        // Step 4: Re-attach parentheses; collapse whitespace
        if (parentheses.isNotEmpty()) {
            name = "$name $parentheses"
        }

        return name.replace("\\s+".toRegex(), " ").trim()
    }

    // ── CSV import ─────────────────────────────────────────────────────────────
    fun importStationsFromCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(
                            InputStreamReader(inputStream, Charsets.UTF_8)
                        )
                        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

                        val rawStations = mutableListOf<Station>()

                        reader.lineSequence().forEach { rawLine ->
                            val line = rawLine.trim()
                            if (line.isBlank()) return@forEach

                            val parts = if (line.contains(';')) line.split(';')
                            else line.split(',')

                            if (parts.size < 2) return@forEach

                            val name    = parts[0].trim()
                            val dateStr = parts[1].trim()
                            if (name.isBlank() || dateStr.isBlank()) return@forEach

                            val date = try {
                                LocalDate.parse(dateStr, dateFormatter)
                            } catch (e: DateTimeParseException) { null }

                            val cleanedName = cleanStationName(name)

                            rawStations.add(
                                Station(
                                    id             = 0,
                                    name           = cleanedName,
                                    address        = "",
                                    inspectionDate = date,
                                    status         = StationStatus.PENDING
                                )
                            )
                        }

                        // Deduplication: ensure only one unique entry per physical station is saved
                        val uniqueStations = rawStations.distinctBy { it.name }

                        uniqueStations.forEach { station ->
                            try {
                                stationRepository.saveStation(station)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save station '${station.name}' during CSV import", e)
                            }
                        }

                        stationRepository.seedCoordinatesIfMissing()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CSV import failed", e)
                _uiEvent.emit(UiEvent.ShowSnackbar("Import failed: ${e.localizedMessage ?: "Unknown error"}"))
            } finally {
                _isLoading.value = false
            }
        }
    }
}