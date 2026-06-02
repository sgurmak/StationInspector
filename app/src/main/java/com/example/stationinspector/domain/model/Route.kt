package com.example.stationinspector.domain.model

/** A latitude/longitude pair, free of any map-library type. */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double
)

/** A single computed leg of a route between two points. */
data class RouteSegment(
    val distanceMeters: Double,
    val durationSeconds: Long,
    /** Encoded (Google precision-5) polyline of the leg geometry. */
    val geometry: String
)

/**
 * A point fed into route optimization. [id] + [isStation] let the caller map
 * the optimized order back to the original stations/POIs.
 */
data class RouteWaypoint(
    val id: String,
    val isStation: Boolean,
    val latitude: Double,
    val longitude: Double
)

/** Result of optimizing a set of waypoints — the input waypoints, reordered. */
data class OptimizedRoute(
    val orderedWaypoints: List<RouteWaypoint>
)
