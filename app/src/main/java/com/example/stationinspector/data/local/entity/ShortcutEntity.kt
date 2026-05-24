package com.example.stationinspector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = false)
    val id: String,
    val label: String,
    val customName: String?,
    /** Serialized [com.example.stationinspector.domain.model.PoiLocation]. */
    val poiItemJson: String?,
    val isNew: Boolean,
    val isRoundTrip: Boolean = false
)
