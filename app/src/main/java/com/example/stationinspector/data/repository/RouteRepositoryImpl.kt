package com.example.stationinspector.data.repository

import com.example.stationinspector.data.local.dao.StationDao
import com.example.stationinspector.data.local.dao.RouteCacheDao
import com.example.stationinspector.data.local.entity.RouteCacheEntity
import com.example.stationinspector.data.remote.OrsApiService
import com.example.stationinspector.data.remote.dto.DirectionsRequest
import com.example.stationinspector.data.remote.dto.OptimizationRequest
import com.example.stationinspector.data.remote.dto.VehicleDto
import com.example.stationinspector.data.remote.dto.JobDto
import com.example.stationinspector.domain.repository.RouteRepository
import com.example.stationinspector.domain.repository.RouteData
import com.example.stationinspector.ui.screens.RouteListItem
import com.example.stationinspector.utils.PolylineUtils
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import retrofit2.HttpException
import javax.inject.Inject

class RouteRepositoryImpl @Inject constructor(
    private val routeCacheDao: RouteCacheDao,
    private val orsApiService: OrsApiService,
    private val stationDao: StationDao
) : RouteRepository {

    override suspend fun getRouteSegment(lat1: Double, lon1: Double, lat2: Double, lon2: Double): RouteCacheEntity {
        val id = "$lat1,$lon1-$lat2,$lon2"
        
        // Check cache first
        val cached = routeCacheDao.getRouteCacheById(id)
        if (cached != null) {
            return cached
        }
        
        // If not found, fetch from network
        // Note: ORS expects coordinates in [longitude, latitude] format
        val request = DirectionsRequest(
            coordinates = listOf(
                listOf(lon1, lat1),
                listOf(lon2, lat2)
            )
        )
        
        val response = orsApiService.getDirections(request)
        val route = response.routes.firstOrNull() 
            ?: throw Exception("No route found between coordinates")
            
        val distance = route.summary.distance
        val duration = route.summary.duration.toLong()
        
        val newSegment = RouteCacheEntity(
            id = id,
            originLat = lat1,
            originLon = lon1,
            destLat = lat2,
            destLon = lon2,
            distanceMeters = distance,
            durationSeconds = duration,
            geometry = route.geometry ?: ""
        )
        
        // Save to cache
        routeCacheDao.insertRouteCache(newSegment)
        
        return newSegment
    }

    override suspend fun fetchAndSaveCoordinates(stationId: Long, stationName: String): Pair<Double, Double>? {
        try {
            val response = orsApiService.searchGeocode(stationName)
            val feature = response.features.firstOrNull() ?: return null
            
            val lon = feature.geometry.coordinates[0]
            val lat = feature.geometry.coordinates[1]
            
            val station = stationDao.getStationByIdSync(stationId)
            if (station != null) {
                stationDao.updateStation(station.copy(latitude = lat, longitude = lon))
            }
            return Pair(lat, lon)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun optimizeAndFetchGeometry(items: List<RouteListItem>): Result<RouteData> = withContext(Dispatchers.IO) {
        try {
            if (items.size < 2) return@withContext Result.failure(Exception("At least 2 points required"))

            val firstItem = items.first()
            val lastItem = items.last()
            
            // Step 1: Map
            val isRoundTrip = items.size > 2 && 
                              firstItem.latitude == lastItem.latitude && 
                              firstItem.longitude == lastItem.longitude

            val startItem = items.first()
            val endItem = if (items.size > 1 && isRoundTrip) items.last() else null

            val intermediateItems = items.subList(1, if (endItem != null) items.size - 1 else items.size)

            val vehicle = VehicleDto(
                id = 1,
                profile = "driving-car",
                start = listOf(startItem.longitude, startItem.latitude),
                end = if (endItem != null) listOf(endItem.longitude, endItem.latitude) else null
            )

            val jobs = intermediateItems.mapIndexed { index, item ->
                JobDto(
                    id = index + 1, // IDs for mapping back
                    location = listOf(item.longitude, item.latitude)
                )
            }

            val request = OptimizationRequest(
                jobs = jobs,
                vehicles = listOf(vehicle)
            )

            // Step 2: Optimize
            val optimizationResponse = orsApiService.getOptimization(request)
            val routeSteps = optimizationResponse.routes?.firstOrNull()?.steps 
                ?: throw Exception("Optimization failed to return routes")

            // Step 3: Reconstruct
            val sortedIntermediateJobs = mutableListOf<RouteListItem>()

            // The steps contain "start", "job", "end"
            for (step in routeSteps) {
                if (step.type == "job" && step.id != null) {
                    val jobId = step.id
                    val originalJob = jobs.find { it.id == jobId }
                    if (originalJob != null) {
                        val matchingItem = intermediateItems[jobId - 1]
                        sortedIntermediateJobs.add(matchingItem)
                    }
                }
            }

            val finalList = listOfNotNull(startItem) + sortedIntermediateJobs + listOfNotNull(endItem)
            
            val indexedList = finalList.mapIndexed { index, item ->
                when (item) {
                    is com.example.stationinspector.ui.screens.StationItem -> item.copy(orderIndex = index)
                    is com.example.stationinspector.ui.screens.PoiItem -> item.copy(orderIndex = index)
                    else -> item
                }
            }

            // Step 4: Geometry
            val directionsCoords = indexedList.map { listOf(it.longitude, it.latitude) }
            val directionsRequest = DirectionsRequest(coordinates = directionsCoords)
            
            val directionsResponse = orsApiService.getDirections(directionsRequest)
            val route = directionsResponse.routes.firstOrNull() 
                ?: throw Exception("No route found between coordinates")
                
            val geometryString = route.geometry ?: ""
            val polylinePoints = PolylineUtils.decode(geometryString)

            // Cache it
            val cacheId = "full_opt_route_${directionsCoords.hashCode()}"
            val newSegment = RouteCacheEntity(
                id = cacheId,
                originLat = firstItem.latitude,
                originLon = firstItem.longitude,
                destLat = lastItem.latitude,
                destLon = lastItem.longitude,
                distanceMeters = route.summary.distance,
                durationSeconds = route.summary.duration.toLong(),
                geometry = geometryString
            )
            routeCacheDao.insertRouteCache(newSegment)

            Result.success(RouteData(indexedList, polylinePoints))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Optimization timeout. The route might be too complex.", e))
        } catch (e: HttpException) {
            Result.failure(Exception("Route error. Check if all locations are reachable by car.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
