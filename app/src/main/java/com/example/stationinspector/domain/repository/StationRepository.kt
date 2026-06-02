package com.example.stationinspector.domain.repository

import com.example.stationinspector.domain.model.Photo
import com.example.stationinspector.domain.model.Station
import com.example.stationinspector.domain.model.StationWithSplitCountsDomain
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    fun getAllStations(): Flow<List<Station>>
    fun getAllStationsWithSplitCounts(): Flow<List<StationWithSplitCountsDomain>>
    fun getStationById(stationId: Long): Flow<Station?>
    suspend fun saveStation(station: Station): Long
    suspend fun updateStationCoordinates(stationId: Long, lat: Double, lon: Double)
    suspend fun deleteStation(station: Station)
    suspend fun clearAllData()

    /** Synchronous one-shot read of all stations scheduled for [date]. */
    suspend fun getStationsForDateSync(date: java.time.LocalDate): List<Station>
    suspend fun updateStationOrder(stationId: Long, orderIndex: Int)
    suspend fun updateStationOrders(orders: List<Pair<Long, Int>>)

    suspend fun seedCoordinatesIfMissing()

    fun getPhotosForStation(stationId: Long): Flow<List<Photo>>
    fun getPhotosByStationAndZone(stationId: Long, zone: String): Flow<List<Photo>>
    fun getAllPhotos(): Flow<List<Photo>>
    fun getUnexportedPhotos(): Flow<List<Photo>>
    suspend fun savePhoto(photo: Photo): Long
    suspend fun savePhotos(photos: List<Photo>)
    suspend fun deletePhoto(photo: Photo)
}
