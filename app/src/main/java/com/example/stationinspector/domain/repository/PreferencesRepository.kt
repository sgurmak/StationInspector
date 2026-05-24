package com.example.stationinspector.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for typed user preferences.
 *
 * Hides Jetpack DataStore so ViewModels never deal with raw keys or the
 * Preferences mutator API.
 */
interface PreferencesRepository {

    val isRoundTripEnabled: Flow<Boolean>

    suspend fun setRoundTripEnabled(enabled: Boolean)
}
