package com.example.stationinspector.domain.model

import java.time.LocalDate

/**
 * Domain model carrying a Station plus its pre-computed photo split counts.
 * Used by StationListViewModel to display per-station counters without
 * issuing separate queries per station.
 */
data class StationWithSplitCountsDomain(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val inspectionDate: LocalDate?,
    val status: StationStatus,
    /** Photos of type CLIENT_REPORT */
    val regularCount: Int,
    /** Photos of type INTERNAL_DEFECT */
    val issueCount: Int,
    val orderIndex: Int = 0
)