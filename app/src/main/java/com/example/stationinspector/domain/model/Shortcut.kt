package com.example.stationinspector.domain.model

/**
 * Domain model for a user-defined shortcut (Home, Work, or custom preset).
 *
 * The location is stored as a [PoiLocation] rather than a raw JSON string —
 * (de)serialization is fully encapsulated inside the repository so callers
 * never touch Gson.
 */
data class Shortcut(
    val id: String,
    val label: String,
    val customName: String?,
    val location: PoiLocation?,
    val isNew: Boolean,
    val isRoundTrip: Boolean = false
) {
    companion object {
        const val ID_HOME = "1"
        const val ID_WORK = "2"
    }
}
