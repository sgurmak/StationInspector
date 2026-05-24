package com.example.stationinspector.data.mapper

import com.example.stationinspector.data.local.entity.PoiEntity
import com.example.stationinspector.domain.model.Poi

internal fun PoiEntity.toDomain(): Poi = Poi(
    id             = id,
    name           = name,
    city           = city,
    address        = address,
    region         = region,
    latitude       = latitude,
    longitude      = longitude,
    inspectionDate = inspectionDate,
    orderIndex     = orderIndex
)

internal fun Poi.toEntity(): PoiEntity = PoiEntity(
    id             = id,
    name           = name,
    city           = city,
    address        = address,
    region         = region,
    latitude       = latitude,
    longitude      = longitude,
    inspectionDate = inspectionDate,
    orderIndex     = orderIndex
)
