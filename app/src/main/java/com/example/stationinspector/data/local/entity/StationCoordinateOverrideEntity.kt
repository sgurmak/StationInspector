package com.example.stationinspector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user's manual coordinate correction for a station, keyed by station [name].
 *
 * Station rows are wiped (and their auto-generated ids reset) on every "clear
 * all data" + CSV re-import cycle, so hand-edited positions stored only in the
 * `stations` row would be lost. This table is deliberately NOT touched by
 * [com.example.stationinspector.data.repository.StationRepositoryImpl.clearAllData];
 * it survives the clear and is re-applied by name after the next import so the
 * inspector never has to re-drag a marker they already corrected.
 */
@Entity(tableName = "station_coord_overrides")
data class StationCoordinateOverrideEntity(
    @PrimaryKey val stationName: String,
    val latitude: Double,
    val longitude: Double
)
