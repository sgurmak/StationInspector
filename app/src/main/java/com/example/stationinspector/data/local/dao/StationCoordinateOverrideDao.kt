package com.example.stationinspector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stationinspector.data.local.entity.StationCoordinateOverrideEntity

@Dao
interface StationCoordinateOverrideDao {

    /** Records (or replaces) the manual coordinate override for a station name. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: StationCoordinateOverrideEntity)

    @Query("SELECT * FROM station_coord_overrides")
    suspend fun getAll(): List<StationCoordinateOverrideEntity>

    @Query("DELETE FROM station_coord_overrides")
    suspend fun deleteAll()
}
