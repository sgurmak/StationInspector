package com.example.stationinspector.domain.repository

import com.example.stationinspector.domain.model.GeoCoordinate
import com.example.stationinspector.domain.model.OptimizedRoute
import com.example.stationinspector.domain.model.RouteSegment
import com.example.stationinspector.domain.model.RouteWaypoint

/**
 * Routing operations backed by the ORS API + local cache. The contract is
 * expressed purely in domain types — no Room entities, UI models, or map-library
 * classes leak across this boundary.
 */
interface RouteRepository {

    /** Distance/time/geometry for the leg between two coordinates (cached). */
    suspend fun getRouteSegment(lat1: Double, lon1: Double, lat2: Double, lon2: Double): RouteSegment

    /** Geocodes [stationName], persists the coordinates on the station, returns them. */
    suspend fun fetchAndSaveCoordinates(stationId: Long, stationName: String): GeoCoordinate?

    /** Optimizes the visiting order of [waypoints] (first/last may anchor a round trip). */
    suspend fun optimizeAndFetchGeometry(waypoints: List<RouteWaypoint>): Result<OptimizedRoute>
}
