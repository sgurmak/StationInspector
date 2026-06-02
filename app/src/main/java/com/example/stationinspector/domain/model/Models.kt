package com.example.stationinspector.domain.model

import java.time.LocalDate

data class Station(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val inspectionDate: LocalDate?,
    val status: StationStatus,
    val orderIndex: Int = 0
)

data class Photo(
    val id: Long,
    val stationId: Long,
    val zone: PhotoZone,
    val type: PhotoType,
    val localPath: String,
    val timestamp: Long,
    val description: String,
    val exported: Boolean,
    val assignedDate: String
)