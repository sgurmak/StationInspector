package com.example.stationinspector.data.local.converter

import androidx.room.TypeConverter
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.domain.model.PhotoZone
import com.example.stationinspector.domain.model.StationStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.format(formatter)
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it, formatter) }
    }

    @TypeConverter
    fun fromStationStatus(status: StationStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStationStatus(value: String): StationStatus {
        return StationStatus.valueOf(value)
    }

    @TypeConverter
    fun fromPhotoZone(zone: PhotoZone): String {
        return zone.name
    }

    @TypeConverter
    fun toPhotoZone(value: String): PhotoZone {
        return PhotoZone.valueOf(value)
    }

    @TypeConverter
    fun fromPhotoType(type: PhotoType): String {
        return type.name
    }

    @TypeConverter
    fun toPhotoType(value: String): PhotoType {
        return PhotoType.valueOf(value)
    }
}
