package com.example.stationinspector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stationinspector.ui.screens.PoiItem

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = false)
    val id: String,
    val label: String,
    val customName: String?,
    val poiItemJson: String?, // serialized PoiItem
    val isNew: Boolean,
    val isRoundTrip: Boolean = false
)
