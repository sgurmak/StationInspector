package com.example.stationinspector.data.remote.dto

data class DirectionsRequest(
    val coordinates: List<List<Double>> // [[lon, lat], [lon, lat]]
)

data class DirectionsResponse(
    val routes: List<RouteDto>
)

data class RouteDto(
    val summary: RouteSummaryDto,
    val geometry: String?
)

data class RouteSummaryDto(
    val distance: Double,
    val duration: Double
)

data class OptimizationRequest(
    val jobs: List<JobDto>,
    val vehicles: List<VehicleDto>
)

data class JobDto(
    val id: Int,
    val location: List<Double> // [lon, lat]
)

data class VehicleDto(
    val id: Int,
    val profile: String = "driving-car",
    val start: List<Double>,
    val end: List<Double>? = null
)

data class OptimizationResponse(
    val code: Int?,
    val unassigned: List<Any>?,
    val routes: List<OptRouteDto>?
)

data class OptRouteDto(
    val vehicle: Int,
    val distance: Int,
    val duration: Int,
    val steps: List<OptStepDto>
)

data class OptStepDto(
    val type: String,
    val location: List<Double>?,
    val id: Int?,
    val duration: Int,
    val distance: Int
)

data class GeocodeResponse(
    val features: List<GeocodeFeature>
)

data class GeocodeFeature(
    val geometry: GeocodeGeometry
)

data class GeocodeGeometry(
    val coordinates: List<Double> // [lon, lat]
)
