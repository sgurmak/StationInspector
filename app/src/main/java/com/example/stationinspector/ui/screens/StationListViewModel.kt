package com.example.stationinspector.ui.screens

import androidx.compose.runtime.Immutable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationinspector.data.local.dao.StationDao
import com.example.stationinspector.data.local.entity.ShortcutEntity
import com.example.stationinspector.data.repository.MapyCzRepository
import com.example.stationinspector.domain.model.Poi
import com.example.stationinspector.domain.model.PoiLocation
import com.example.stationinspector.domain.model.Shortcut
import com.example.stationinspector.domain.repository.PoiRepository
import com.example.stationinspector.domain.repository.PreferencesRepository
import com.example.stationinspector.domain.repository.RouteRepository
import com.example.stationinspector.domain.repository.ShortcutRepository
import com.example.stationinspector.domain.repository.StationRepository
import com.example.stationinspector.domain.usecase.ImportStationsUseCase
import com.example.stationinspector.utils.PolylineUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
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
import java.time.LocalDate
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
    private val shortcutRepository: ShortcutRepository,
    private val poiRepository: PoiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val importStationsUseCase: ImportStationsUseCase,
    private val stationDao: StationDao,
    private val database: AppDatabase
) : ViewModel() {

    companion object {
        private const val SHORTCUT_ID_HOME = Shortcut.ID_HOME
        private const val SHORTCUT_ID_WORK = Shortcut.ID_WORK
        private const val TAG = "StationListViewModel"
    }

    val isRoundTripEnabled: StateFlow<Boolean> = preferencesRepository.isRoundTripEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setRoundTripEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRoundTripEnabled(enabled)
            val date = _selectedDate.value
            if (date != null) {
                withContext(Dispatchers.IO) {
                    rebuildHomePointsAndIndices(date, enabled)
                }
            }
        }
    }

    val shortcuts: StateFlow<List<ShortcutUiModel>> = shortcutRepository.observeShortcuts()
        .map { domainList -> domainList.map { it.toUiModel() } }
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
                        poiRepository.observePoisForDate(date),
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

                        val poiItems = pois.map { poi ->
                            PoiItem(
                                id       = poi.id,
                                uniqueId = poi.id,
                                name     = poi.name,
                                city     = poi.city,
                                address  = poi.address,
                                region   = poi.region,
                                latitude = poi.latitude,
                                longitude = poi.longitude,
                                orderIndex = poi.orderIndex,
                                isHidden   = hiddenIds.contains(poi.id)
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
                    poiRepository.updateOrders(poiOrders)
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
                    // Propagate the new coordinates to any shortcut that points
                    // at this POI so the saved Home/Work pin moves too.
                    val currentShortcuts = shortcutRepository.observeShortcuts().first()
                    currentShortcuts.forEach { shortcut ->
                        val location = shortcut.location
                        if (location != null && location.id == poi.id) {
                            shortcutRepository.updateShortcut(
                                id = shortcut.id,
                                location = location.copy(latitude = lat, longitude = lon),
                                customName = shortcut.customName,
                                isNew = shortcut.isNew
                            )
                        }
                    }
                    poiRepository.insertPoi(
                        Poi(
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
                    )
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
            poiRepository.insertPoi(
                poi.toDomainAt(date = date, orderIndex = targetOrderIndex, freshId = true)
            )
            poiRepository.updateOrder(lastItem.id, targetOrderIndex + 1)
        } else {
            val maxOrder = currentItems.maxOfOrNull { it.orderIndex } ?: -1
            poiRepository.insertPoi(
                poi.toDomainAt(date = date, orderIndex = maxOrder + 1, freshId = true)
            )
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
            val pois = poiRepository.getPoisForDate(date)

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

            poiRepository.deletePoisByNameAndDate("Home", date)
            
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
                
                val startPoi = Poi(
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
                poiRepository.insertPoi(startPoi)

                nonHomeItems.forEachIndexed { idx, item ->
                    val newIdx = idx + 1
                    when (item) {
                        is StationItem -> stationDao.updateStationOrder(item.station.id.toLong(), newIdx)
                        is PoiItem -> poiRepository.updateOrder(item.id, newIdx)
                    }
                }

                if (isRoundTrip) {
                    val endPoi = Poi(
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
                    poiRepository.insertPoi(endPoi)
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
                        is PoiItem -> poiRepository.updateOrder(item.id, idx)
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
                if (poiOrders.isNotEmpty())     poiRepository.updateOrders(poiOrders)
            }
        }
    }

    fun toggleHidePoi(id: String) {
        _hiddenIds.update { if (id in it) it - id else it + id }
    }

    fun deletePoi(id: String) {
        val date = _selectedDate.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pois = poiRepository.getPoisForDate(date)
            val poiToDelete = pois.firstOrNull { it.id == id }
            if (poiToDelete != null && poiToDelete.name == "Home") {
                rebuildHomePointsAndIndices(date, false, null, clearHome = true)
            } else {
                poiRepository.deletePoi(id)
                rebuildHomePointsAndIndices(date, isRoundTripEnabled.value)
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
            if (id == SHORTCUT_ID_HOME || id == SHORTCUT_ID_WORK) {
                // Keep Home and Work entries — just clear their bound location.
                shortcutRepository.updateShortcut(id, location = null, customName = null, isNew = true)
            } else {
                shortcutRepository.deleteShortcut(id)
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
                shortcutRepository.clearOldBlankShortcuts()
                shortcutRepository.ensureDefaults()
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

    // ── CSV import ─────────────────────────────────────────────────────────────
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
}

// ── UI ↔ domain mappers (private to this file) ─────────────────────────────────

private fun Shortcut.toUiModel(): ShortcutUiModel = ShortcutUiModel(
    id          = id,
    label       = label,
    customName  = customName,
    poiItem     = location?.toPoiItem(),
    isNew       = isNew,
    isRoundTrip = isRoundTrip,
    // Synthesized for source compatibility with existing previews.
    // The `entity` field on ShortcutUiModel is no longer the canonical
    // source — repository is. Slated for removal in a later UI cleanup.
    entity      = ShortcutEntity(
        id          = id,
        label       = label,
        customName  = customName,
        poiItemJson = null,
        isNew       = isNew,
        isRoundTrip = isRoundTrip
    )
)

private fun PoiLocation.toPoiItem(): PoiItem = PoiItem(
    id        = id,
    name      = name,
    city      = city,
    address   = address,
    region    = region,
    latitude  = latitude,
    longitude = longitude
)

private fun PoiItem.toPoiLocation(): PoiLocation = PoiLocation(
    id        = id,
    name      = name,
    city      = city,
    address   = address,
    region    = region,
    latitude  = latitude,
    longitude = longitude
)

/**
 * Build a [Poi] domain object from a [PoiItem] for a given date/order, optionally
 * forcing a fresh UUID when a new POI row is being inserted into the route.
 */
private fun PoiItem.toDomainAt(date: LocalDate, orderIndex: Int, freshId: Boolean): Poi = Poi(
    id             = if (freshId) java.util.UUID.randomUUID().toString() else id,
    name           = name,
    city           = city,
    address        = address,
    region         = region,
    latitude       = latitude,
    longitude      = longitude,
    inspectionDate = date,
    orderIndex     = orderIndex
)
