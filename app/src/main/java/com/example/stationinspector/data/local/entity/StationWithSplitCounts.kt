package com.example.stationinspector.data.local.entity

import androidx.room.Embedded

/**
 * Room result class for the getStationsWithSplitCounts() query.
 * regularCount = photos of type CLIENT_REPORT
 * issueCount   = photos of type INTERNAL_DEFECT
 */
data class StationWithSplitCounts(
    @Embedded val station: StationEntity,
    val regularCount: Int,
    val issueCount: Int
)