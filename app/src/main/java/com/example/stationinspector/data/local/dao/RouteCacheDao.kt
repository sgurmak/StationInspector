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
}
