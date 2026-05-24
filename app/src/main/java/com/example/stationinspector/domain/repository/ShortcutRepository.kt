package com.example.stationinspector.domain.repository

import com.example.stationinspector.domain.model.PoiLocation
import com.example.stationinspector.domain.model.Shortcut
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for user-defined shortcuts.
 *
 * Encapsulates the Room DAO + Gson (de)serialization so the rest of the app
 * works exclusively with [Shortcut] / [PoiLocation] domain types.
 */
interface ShortcutRepository {

    fun observeShortcuts(): Flow<List<Shortcut>>

    suspend fun count(): Int

    suspend fun insertAll(shortcuts: List<Shortcut>)

    /** Updates the bound location and label of an existing shortcut. */
    suspend fun updateShortcut(
        id: String,
        location: PoiLocation?,
        customName: String?,
        isNew: Boolean
    )

    /** Inserts a brand-new "Preset" shortcut. Returns the generated id. */
    suspend fun insertPreset(location: PoiLocation?, customName: String?): String

    suspend fun deleteShortcut(id: String)

    /** Removes any auto-created empty shortcuts that aren't Home/Work. */
    suspend fun clearOldBlankShortcuts()

    /** Seeds Home and Work entries on first run if the table is empty. */
    suspend fun ensureDefaults()
}
