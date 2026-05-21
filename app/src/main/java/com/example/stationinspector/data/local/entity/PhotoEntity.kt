package com.example.stationinspector.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.domain.model.PhotoZone

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["stationId"])]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationId: Long,
    val zone: PhotoZone,
    val type: PhotoType,
    val localPath: String,
    val timestamp: Long,
    val description: String,
    val exported: Boolean,
    val assignedDate: String
)