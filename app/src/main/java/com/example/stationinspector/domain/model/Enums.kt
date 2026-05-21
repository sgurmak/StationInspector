package com.example.stationinspector.domain.model

enum class StationStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}

enum class PhotoZone {
    ENTRANCE,
    PLATFORM,
    TICKETING,
    RESTROOM,
    OTHER
}

enum class PhotoType {
    PANORAMIC,
    DETAIL,
    DOCUMENT,
    CLIENT_REPORT,
    INTERNAL_DEFECT
}