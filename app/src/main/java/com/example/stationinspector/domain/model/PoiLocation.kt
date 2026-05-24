package com.example.stationinspector.domain.model

/**
 * Lightweight, persistence-free projection of a Point of Interest used inside
 * a Shortcut. Unlike [Poi] it has no [inspectionDate] or [orderIndex] because
 * a shortcut is reusable across dates and isn't part of any ordered route.
 *
 * This is the type that [com.example.stationinspector.domain.model.Shortcut]
 * exposes to the rest of the app; the data layer is responsible for
 * (de)serializing this from/to the stored JSON.
 */
data class PoiLocation(
    val id: String,
    val name: String,
    val city: String?,
    val address: String?,
    val region: String?,
    val latitude: Double,
    val longitude: Double
)
