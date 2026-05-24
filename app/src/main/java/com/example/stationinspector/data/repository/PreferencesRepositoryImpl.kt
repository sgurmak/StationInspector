package com.example.stationinspector.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.stationinspector.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    private companion object {
        val KEY_ROUND_TRIP_ENABLED = booleanPreferencesKey("home_round_trip_enabled")
    }

    override val isRoundTripEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[KEY_ROUND_TRIP_ENABLED] ?: false }

    override suspend fun setRoundTripEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_ROUND_TRIP_ENABLED] = enabled }
    }
}
