package com.example.stationinspector.data.mapper

import android.util.Log
import com.example.stationinspector.data.local.entity.ShortcutEntity
import com.example.stationinspector.domain.model.PoiLocation
import com.example.stationinspector.domain.model.Shortcut
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

private const val TAG = "ShortcutMapper"

/**
 * (De)serialization helpers between [ShortcutEntity] (Room) and [Shortcut]
 * (domain). The Gson JSON format is preserved bit-for-bit so existing rows in
 * the database continue to deserialize after this refactor:
 *
 *   { "id":..., "name":..., "city":..., "address":..., "region":...,
 *     "latitude":..., "longitude":... }
 *
 * Historic rows carried extra UI-only fields (isHidden, uniqueId,
 * orderIndex). Gson silently ignores unknown fields, so they parse fine.
 */
internal fun ShortcutEntity.toDomain(gson: Gson): Shortcut = Shortcut(
    id          = id,
    label       = label,
    customName  = customName,
    location    = poiItemJson?.let { json -> deserializeLocation(gson, id, json) },
    isNew       = isNew,
    isRoundTrip = isRoundTrip
)

internal fun Shortcut.toEntity(gson: Gson): ShortcutEntity = ShortcutEntity(
    id           = id,
    label        = label,
    customName   = customName,
    poiItemJson  = location?.let { gson.toJson(it) },
    isNew        = isNew,
    isRoundTrip  = isRoundTrip
)

private fun deserializeLocation(gson: Gson, shortcutId: String, json: String): PoiLocation? =
    try {
        gson.fromJson(json, PoiLocation::class.java)
    } catch (e: JsonSyntaxException) {
        Log.w(TAG, "Failed to parse poiItemJson for shortcut '$shortcutId' — returning null", e)
        null
    }
