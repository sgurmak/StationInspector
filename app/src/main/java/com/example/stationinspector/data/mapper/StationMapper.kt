package com.example.stationinspector.data.mapper

import com.example.stationinspector.data.local.entity.PhotoEntity
import com.example.stationinspector.data.local.entity.StationEntity
import com.example.stationinspector.domain.model.Photo
import com.example.stationinspector.domain.model.Station

fun StationEntity.toDomain(): Station {
    return Station(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        inspectionDate = inspectionDate,
        status = status
    )
}

fun Station.toEntity(): StationEntity {
    return StationEntity(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        inspectionDate = inspectionDate,
        status = status
    )
}

fun PhotoEntity.toDomain(): Photo {
    return Photo(
        id = id,
        stationId = stationId,
        zone = zone,
        type = type,
        localPath = localPath,
        timestamp = timestamp,
        description = description,
        exported = exported,
        assignedDate = assignedDate
    )
}

fun Photo.toEntity(): PhotoEntity {
    return PhotoEntity(
        id = id,
        stationId = stationId,
        zone = zone,
        type = type,
        localPath = localPath,
        timestamp = timestamp,
        description = description,
        exported = exported,
        assignedDate = assignedDate
    )
}
