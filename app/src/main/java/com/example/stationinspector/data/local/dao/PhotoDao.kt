package com.example.stationinspector.data.local.dao

import androidx.room.*
import com.example.stationinspector.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE stationId = :stationId ORDER BY timestamp DESC")
    fun getPhotosForStation(stationId: Long): Flow<List<PhotoEntity>>

    /**
     * Fetch photos for a specific station AND zone.
     * [zone] must be the PhotoZone enum name string (e.g. "ENTRANCE"),
     * which is what Room's TypeConverter stores in the `zone` column.
     */
    @Query("SELECT * FROM photos WHERE stationId = :stationId AND zone = :zone ORDER BY timestamp DESC")
    fun getPhotosByStationAndZone(stationId: Long, zone: String): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos WHERE exported = 0")
    fun getUnexportedPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos")
    suspend fun deleteAllPhotos()
}
