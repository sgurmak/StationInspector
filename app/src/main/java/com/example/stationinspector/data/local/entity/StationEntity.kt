package com.example.stationinspector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stationinspector.domain.model.StationStatus
import java.time.LocalDate

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val inspectionDate: LocalDate?,
    val status: StationStatus,
    val orderIndex: Int = 0
)