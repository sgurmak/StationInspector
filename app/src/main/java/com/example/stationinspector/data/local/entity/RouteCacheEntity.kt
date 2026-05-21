package com.example.stationinspector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_cache")
data class RouteCacheEntity(
    @PrimaryKey
    val id: String, // e.g. "lat1,lon1-lat2,lon2"
    val originLat: Double,
    val originLon: Double,
    val destLat: Double,
    val destLon: Double,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val geometry: String = ""
)
