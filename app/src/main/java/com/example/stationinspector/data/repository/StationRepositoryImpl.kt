package com.example.stationinspector.data.repository

import android.content.Context
import com.example.stationinspector.data.local.dao.PhotoDao
import com.example.stationinspector.data.local.dao.StationDao
import com.example.stationinspector.data.mapper.toDomain
import com.example.stationinspector.data.mapper.toEntity
import com.example.stationinspector.domain.model.Photo
import com.example.stationinspector.domain.model.Station
import com.example.stationinspector.domain.model.StationWithSplitCountsDomain
import com.example.stationinspector.domain.repository.StationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject

class StationRepositoryImpl @Inject constructor(
    private val stationDao: StationDao,
    private val photoDao: PhotoDao,
    @ApplicationContext private val context: Context
) : StationRepository {

    override fun getAllStations(): Flow<List<Station>> {
        return stationDao.getAllStations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllStationsWithSplitCounts(): Flow<List<StationWithSplitCountsDomain>> {
        return stationDao.getStationsWithSplitCounts().map { rows ->
            rows.map { row ->
                StationWithSplitCountsDomain(
                    id             = row.station.id,
                    name           = row.station.name,
                    address        = row.station.address,
                    latitude       = row.station.latitude,
                    longitude      = row.station.longitude,
                    inspectionDate = row.station.inspectionDate,
                    status         = row.station.status,
                    regularCount   = row.regularCount,
                    issueCount     = row.issueCount,
                    orderIndex     = row.station.orderIndex
                )
            }
        }
    }

    override fun getStationById(stationId: Long): Flow<Station?> {
        return stationDao.getStationById(stationId).map { it?.toDomain() }
    }

    override suspend fun saveStation(station: Station): Long {
        return stationDao.insertStation(station.toEntity())
    }

    override suspend fun updateStationCoordinates(stationId: Long, lat: Double, lon: Double) {
        stationDao.updateStationCoordinates(stationId, lat, lon)
    }

    override suspend fun getStationsForDateSync(date: java.time.LocalDate): List<Station> {
        return stationDao.getAllStationsSync()
            .filter { it.inspectionDate == date }
            .map { it.toDomain() }
    }

    override suspend fun updateStationOrder(stationId: Long, orderIndex: Int) {
        stationDao.updateStationOrder(stationId, orderIndex)
    }

    override suspend fun updateStationOrders(orders: List<Pair<Long, Int>>) {
        stationDao.updateStationOrders(orders)
    }

    override suspend fun deleteStation(station: Station) {
        stationDao.deleteStation(station.toEntity())
    }

    override suspend fun clearAllData() {
        // Photos first — they hold a FK reference to stations
        photoDao.deleteAllPhotos()
        stationDao.deleteAllStations()
    }

    override suspend fun seedCoordinatesIfMissing() {
        val stations = stationDao.getAllStationsSync()
        if (stations.any { it.latitude == 0.0 && it.longitude == 0.0 }) {
            val jsonString = context.assets.open("stations.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            val updatedStations = stations.map { station ->
                if (station.latitude == 0.0 && station.longitude == 0.0 && jsonObject.has(station.name)) {
                    val coords = jsonObject.getJSONObject(station.name)
                    station.copy(
                        latitude = coords.getDouble("lat"),
                        longitude = coords.getDouble("lon")
                    )
                } else {
                    station
                }
            }
            stationDao.updateStations(updatedStations)
        }
    }
    override fun getPhotosForStation(stationId: Long): Flow<List<Photo>> {
        return photoDao.getPhotosForStation(stationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getPhotosByStationAndZone(stationId: Long, zone: String): Flow<List<Photo>> {
        return photoDao.getPhotosByStationAndZone(stationId, zone).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUnexportedPhotos(): Flow<List<Photo>> {
        return photoDao.getUnexportedPhotos().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllPhotos(): Flow<List<Photo>> {
        return photoDao.getAllPhotos().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun savePhoto(photo: Photo): Long {
        return photoDao.insertPhoto(photo.toEntity())
    }

    override suspend fun savePhotos(photos: List<Photo>) {
        photoDao.insertPhotos(photos.map { it.toEntity() })
    }

    override suspend fun deletePhoto(photo: Photo) {
        photoDao.deletePhoto(photo.toEntity())
    }
}