package com.example.stationinspector.data.repository

import android.util.Log
import com.example.stationinspector.data.local.dao.RouteCacheDao
import com.example.stationinspector.data.local.dao.StationDao
import com.example.stationinspector.data.local.entity.RouteCacheEntity
import com.example.stationinspector.data.remote.OrsApiService
import com.example.stationinspector.data.remote.dto.DirectionsRequest
import com.example.stationinspector.data.remote.dto.JobDto
import com.example.stationinspector.data.remote.dto.OptimizationRequest
import com.example.stationinspector.data.remote.dto.VehicleDto
import com.example.stationinspector.domain.model.GeoCoordinate
import com.example.stationinspector.domain.model.OptimizedRoute
import com.example.stationinspector.domain.model.RouteSegment
import com.example.stationinspector.domain.model.RouteWaypoint
import com.example.stationinspector.domain.repository.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.SocketTimeoutException
import javax.inject.Inject

class RouteRepositoryImpl @Inject constructor(
    private val routeCacheDao: RouteCacheDao,
    private val orsApiService: OrsApiService,
    private val stationDao: StationDao
) : RouteRepository {

    private companion object {
        const val TAG = "RouteRepositoryImpl"
    }

    override suspend fun getRouteSegment(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double
    ): RouteSegment {
        val id = "$lat1,$lon1-$lat2,$lon2"

        routeCacheDao.getRouteCacheById(id)?.let { return it.toSegment() }

        // ORS expects coordinates as [longitude, latitude].
        val request = DirectionsRequest(
            coordinates = listOf(listOf(lon1, lat1), listOf(lon2, lat2))
        )
        val route = orsApiService.getDirections(request).routes.firstOrNull()
            ?: throw IllegalStateException("No route found between coordinates")

        val entity = RouteCacheEntity(
            id = id,
            originLat = lat1,
            originLon = lon1,
            destLat = lat2,
            destLon = lon2,
            distanceMeters = route.summary.distance,
            durationSeconds = route.summary.duration.toLong(),
            geometry = route.geometry ?: ""
        )
        routeCacheDao.insertRouteCache(entity)
        return entity.toSegment()
    }

    override suspend fun fetchAndSaveCoordinates(
        stationId: Long, stationName: String
    ): GeoCoordinate? {
        return try {
            val feature = orsApiService.searchGeocode(stationName).features.firstOrNull()
                ?: return null
            val lon = feature.geometry.coordinates[0]
            val lat = feature.geometry.coordinates[1]
            stationDao.getStationByIdSync(stationId)?.let { station ->
                stationDao.updateStation(station.copy(latitude = lat, longitude = lon))
            }
            GeoCoordinate(lat, lon)
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed for station '$stationName'", e)
            null
        }
    }

    override suspend fun optimizeAndFetchGeometry(
        waypoints: List<RouteWaypoint>
    ): Result<OptimizedRoute> = withContext(Dispatchers.IO) {
        try {
            if (waypoints.size < 2) {
                return@withContext Result.failure(IllegalArgumentException("At least 2 points required"))
            }

            val first = waypoints.first()
            val last = waypoints.last()
            val isRoundTrip = waypoints.size > 2 &&
                first.latitude == last.latitude && first.longitude == last.longitude

            val start = first
            val end = if (isRoundTrip) last else null
            val intermediate = waypoints.subList(1, if (end != null) waypoints.size - 1 else waypoints.size)

            val vehicle = VehicleDto(
                id = 1,
                profile = "driving-car",
                start = listOf(start.longitude, start.latitude),
                end = end?.let { listOf(it.longitude, it.latitude) }
            )
            val jobs = intermediate.mapIndexed { index, wp ->
                JobDto(id = index + 1, location = listOf(wp.longitude, wp.latitude))
            }

            val steps = orsApiService
                .getOptimization(OptimizationRequest(jobs = jobs, vehicles = listOf(vehicle)))
                .routes?.firstOrNull()?.steps
                ?: throw IllegalStateException("Optimization returned no route")

            // Reconstruct the visiting order from the optimizer's job steps.
            val sortedIntermediate = steps
                .filter { it.type == "job" && it.id != null }
                .mapNotNull { step -> intermediate.getOrNull(step.id!! - 1) }

            val ordered = listOf(start) + sortedIntermediate + listOfNotNull(end)
            Result.success(OptimizedRoute(ordered))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Optimization timeout. The route might be too complex.", e))
        } catch (e: HttpException) {
            Result.failure(Exception("Route error. Check if all locations are reachable by car.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun RouteCacheEntity.toSegment() =
        RouteSegment(distanceMeters = distanceMeters, durationSeconds = durationSeconds, geometry = geometry)
}
