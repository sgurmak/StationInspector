package com.example.stationinspector.ui.screens

import androidx.compose.runtime.Immutable
import com.example.stationinspector.domain.model.Poi
import com.example.stationinspector.domain.model.PoiLocation
import com.example.stationinspector.domain.model.Shortcut
import org.osmdroid.util.GeoPoint
import java.time.LocalDate
import java.util.UUID

// ── UI models for the daily route ───────────────────────────────────────────────

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
    val isRoundTrip: Boolean
)

sealed interface SearchUiState {
    data object Idle    : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<PoiItem>) : SearchUiState
    data class Error(val message: String)          : SearchUiState
}

/** One-shot UI events (snackbars) emitted by the route/settings view models. */
sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
}

// ── UI ↔ domain mappers (module-internal) ───────────────────────────────────────

internal fun Shortcut.toUiModel(): ShortcutUiModel = ShortcutUiModel(
    id          = id,
    label       = label,
    customName  = customName,
    poiItem     = location?.toPoiItem(),
    isNew       = isNew,
    isRoundTrip = isRoundTrip
)

internal fun PoiLocation.toPoiItem(): PoiItem = PoiItem(
    id        = id,
    name      = name,
    city      = city,
    address   = address,
    region    = region,
    latitude  = latitude,
    longitude = longitude
)

internal fun PoiItem.toPoiLocation(): PoiLocation = PoiLocation(
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
internal fun PoiItem.toDomainAt(date: LocalDate, orderIndex: Int, freshId: Boolean): Poi = Poi(
    id             = if (freshId) UUID.randomUUID().toString() else id,
    name           = name,
    city           = city,
    address        = address,
    region         = region,
    latitude       = latitude,
    longitude      = longitude,
    inspectionDate = date,
    orderIndex     = orderIndex
)
