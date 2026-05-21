package com.example.stationinspector.data.local.entity

import androidx.room.Embedded
import com.example.stationinspector.domain.model.StationStatus
import java.time.LocalDate

data class StationWithPhotoCount(
    @Embedded val station: StationEntity,
    val photoCount: Int
)
