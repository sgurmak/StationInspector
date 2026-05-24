package com.example.stationinspector.domain.model

import java.time.LocalDate

/**
 * Domain model for a Point of Interest persisted in the user's daily route.
 *
 * Mirrors [com.example.stationinspector.data.local.entity.PoiEntity] but lives
 * in the domain layer so the UI/ViewModel can depend on a stable contract that
 * is independent of the Room schema.
 */
data class Poi(
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
