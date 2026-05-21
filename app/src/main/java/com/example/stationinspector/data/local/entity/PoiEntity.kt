package com.example.stationinspector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

import java.time.LocalDate

@Entity(tableName = "pois")
data class PoiEntity(
    @PrimaryKey(autoGenerate = false)
    val id: String,
    val name: String,
    val city: String?,
    val address: String?,
    val region: String?,
    val latitude: Double,
    val longitude: Double,
    val inspectionDate: LocalDate,
    val orderIndex: Int = 0
)
