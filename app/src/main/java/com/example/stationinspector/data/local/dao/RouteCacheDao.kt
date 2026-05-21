package com.example.stationinspector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stationinspector.data.local.entity.RouteCacheEntity

@Dao
interface RouteCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteCache(routeCache: RouteCacheEntity)

    @Query("SELECT * FROM route_cache WHERE id = :id")
    suspend fun getRouteCacheById(id: String): RouteCacheEntity?

    @Query("SELECT * FROM route_cache WHERE originLat = :originLat AND originLon = :originLon AND destLat = :destLat AND destLon = :destLon")
    suspend fun getRouteCacheByCoordinates(originLat: Double, originLon: Double, destLat: Double, destLon: Double): RouteCacheEntity?
}
