package com.example.stationinspector.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.stationinspector.data.local.AppDatabase
import com.example.stationinspector.domain.model.Poi
import com.example.stationinspector.domain.model.Shortcut
import com.example.stationinspector.domain.repository.PoiRepository
import com.example.stationinspector.domain.repository.PreferencesRepository
import com.example.stationinspector.domain.repository.RouteRepository
import com.example.stationinspector.domain.repository.ShortcutRepository
import com.example.stationinspector.domain.repository.StationRepository
import com.example.stationinspector.utils.PolylineUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Owns the daily route: selected date, the route item list, route info
 * (distance/time/polyline), editing/optimize/reorder, Home & round-trip logic,
 * map-expanded preference, and list scroll persistence.
 *
 * This is the primary cross-tab ViewModel shared between the Work
 * (StationListScreen) and Map (MapScreen) tabs — both resolve the same instance
 * because they share the NavBackStackEntry ViewModelStoreOwner.
 */
@HiltViewModel
class RouteViewModel @Inject constructor(
    private val stationRepository: StationRepository,
    private val routeRepository: RouteRepository,
    private val poiRepository: PoiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val shortcutRepository: ShortcutRepository,
    private val database: AppDatabase
) : ViewModel() {

    private companion object {
        const val TAG = "RouteViewModel"
        // Single source of truth lives on the domain Shortcut model.
        const val NAME_HOME = Shortcut.NAME_HOME
        const val NAME_WORK = Shortcut.NAME_WORK
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

    // ── Selected date ──────────────────────────────────────────────────────────
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _availableDates = MutableStateFlow<List<LocalDate>>(emptyList())
    val availableDates: StateFlow<List<LocalDate>> = _availableDates.asStateFlow()

    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())

    // ── Route items — date-atomic flow ────────────────────────────────────────
    // combine() nested in flatMapLatest so a date change atomically cancels the
    // previous (stations + pois + hiddenIds) subscription before the new one,
    // avoiding a stale-pois double emission. The date is paired with the list so
    // the screen can use it as a coherent Compose key().
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

    private val _editingPoi = MutableStateFlow<RouteListItem?>(null)
    val editingPoi: StateFlow<RouteListItem?> = _editingPoi.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

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

                    stationRepository.updateStationOrders(stationOrders)
                    poiRepository.updateOrders(poiOrders)
                }.onFailure { e ->
                    Log.w(TAG, "Route optimization returned a failure result", e)
                    _uiEvent.emit(UiEvent.ShowSnackbar(e.message ?: "Optimization failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during route optimization", e)
                _uiEvent.emit(UiEvent.ShowSnackbar("Optimization failed unexpectedly"))
            } finally {
                _isOptimizing.value = false
            }
        }
    }

    fun onInspectionConfirmed() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSnackbar("Inspection completed successfully"))
        }
    }

    fun startEditingPoi(poi: RouteListItem) { _editingPoi.value = poi }

    fun cancelEditingPoi() { _editingPoi.value = null }

    fun saveEditedPoi(poi: RouteListItem, lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _selectedDate.value ?: run {
                Log.w(TAG, "saveEditedPoi called with no selected date — aborting write")
                return@launch
            }
            try {
                if (poi is StationItem) {
                    stationRepository.updateStationCoordinates(poi.id.toLong(), lat, lon)
                } else if (poi is PoiItem) {
                    // Propagate the new coordinates to any shortcut bound to this POI.
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
        _editingPoi.value = null
    }

    fun addPoiToRoute(poi: PoiItem) {
        val date = _selectedDate.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            database.withTransaction {
                insertPoiAtCorrectOrderIndex(poi, date)
            }
        }
    }

    fun addShortcutToRoute(shortcutId: String, poi: PoiItem) {
        val date = _selectedDate.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val finalPoi = when (shortcutId) {
                Shortcut.ID_HOME -> poi.copy(name = NAME_HOME)
                Shortcut.ID_WORK -> poi.copy(name = NAME_WORK)
                else             -> poi
            }

            if (shortcutId == Shortcut.ID_HOME) {
                rebuildHomePointsAndIndices(date, isRoundTripEnabled.value, finalPoi)
            } else {
                database.withTransaction {
                    insertPoiAtCorrectOrderIndex(finalPoi, date)
                }
            }
        }
    }

    private suspend fun insertPoiAtCorrectOrderIndex(poi: PoiItem, date: LocalDate) {
        val currentItems = routeItems.value
        val lastItem = currentItems.lastOrNull()
        val isRoundTrip = isRoundTripEnabled.value
        if (isRoundTrip && lastItem != null && lastItem.name == NAME_HOME && lastItem is PoiItem) {
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
            val stations = stationRepository.getStationsForDateSync(date)
            val pois = poiRepository.getPoisForDate(date)

            val existingHome = if (clearHome) null else pois.firstOrNull { it.name == NAME_HOME }
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

            poiRepository.deletePoisByNameAndDate(NAME_HOME, date)

            if (homePoi != null) {
                val nonHomePois = pois.filter { it.name != NAME_HOME }
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
                    id = homePoi.id.takeIf { it.isNotBlank() && it != "NEW" } ?: UUID.randomUUID().toString(),
                    name = NAME_HOME,
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
                        is StationItem -> stationRepository.updateStationOrder(item.station.id.toLong(), newIdx)
                        is PoiItem -> poiRepository.updateOrder(item.id, newIdx)
                    }
                }

                if (isRoundTrip) {
                    val endPoi = Poi(
                        id = UUID.randomUUID().toString(),
                        name = NAME_HOME,
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
                val remainingPois = pois.filter { it.name != NAME_HOME }
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
                        is StationItem -> stationRepository.updateStationOrder(item.station.id.toLong(), idx)
                        is PoiItem -> poiRepository.updateOrder(item.id, idx)
                    }
                }
            }
        }
    }

    /**
     * Persists the visual drag-and-drop order back to the database so the next
     * Room emission reflects the reordering rather than reverting.
     */
    fun reorderItems(items: List<RouteListItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            database.withTransaction {
                val isRoundTrip = isRoundTripEnabled.value
                val hasHome = items.any { it.name == NAME_HOME }

                val adjustedList = if (hasHome) {
                    val homePoints = items.filter { it.name == NAME_HOME }
                    val startHome = homePoints.first()
                    val endHome = if (homePoints.size >= 2) homePoints.last() else null
                    val middleItems = items.filter { it.name != NAME_HOME }

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

                if (stationOrders.isNotEmpty()) stationRepository.updateStationOrders(stationOrders)
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
            if (poiToDelete != null && poiToDelete.name == NAME_HOME) {
                rebuildHomePointsAndIndices(date, false, null, clearHome = true)
            } else {
                poiRepository.deletePoi(id)
                rebuildHomePointsAndIndices(date, isRoundTripEnabled.value)
            }
        }
    }

    // ── Scroll-position persistence (in-memory, keyed by date) ──────────────────
    private val _scrollPositionByDate = mutableMapOf<LocalDate, Pair<Int, Int>>()

    fun saveScrollPositionForDate(date: LocalDate, index: Int, offset: Int) {
        if (index > 0 || offset > 0) {
            _scrollPositionByDate[date] = index to offset
        } else {
            _scrollPositionByDate.remove(date)
        }
    }

    fun getScrollPositionForDate(date: LocalDate?): Pair<Int, Int> =
        date?.let { _scrollPositionByDate[it] } ?: (0 to 0)

    // ── Map expanded preference (per date) ──────────────────────────────────────
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
                stationRepository.seedCoordinatesIfMissing()
            }
            // JOIN query keeps photo/issue counts in sync; Room re-emits on change.
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
                _isExportButtonEnabled.value =
                    items.filterIsInstance<StationItem>()
                        .sumOf { it.station.photoCount + it.station.issueCount } > 0
            }
        }
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

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

            _routeInfo.value = DailyRouteInfo(
                totalDistanceKm = totalDistance / 1000.0,
                totalTimeMins = (totalTime / 60).toInt(),
                waypointCount = validItems.count { it.name != NAME_HOME },
                polylinePoints = polylinePoints.toList()
            )
        }
    }
}
