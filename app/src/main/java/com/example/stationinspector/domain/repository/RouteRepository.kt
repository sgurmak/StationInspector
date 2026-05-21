package com.example.stationinspector.domain.repository

import com.example.stationinspector.data.local.entity.RouteCacheEntity
import com.example.stationinspector.ui.screens.RouteListItem
import org.osmdroid.util.GeoPoint

data class RouteData(
    val reorderedItems: List<RouteListItem>,
    val polyline: List<GeoPoint>
)

interface RouteRepository {
    suspend fun getRouteSegment(lat1: Double, lon1: Double, lat2: Double, lon2: Double): RouteCacheEntity
    suspend fun fetchAndSaveCoordinates(stationId: Long, stationName: String): Pair<Double, Double>?
    suspend fun optimizeAndFetchGeometry(items: List<RouteListItem>): Result<RouteData>
}
