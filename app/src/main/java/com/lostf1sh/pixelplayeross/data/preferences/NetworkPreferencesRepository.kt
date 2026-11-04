package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single switch for every online feature (Navidrome / Jellyfin, lyrics lookups, ListenBrainz,
 * artist artwork, update checks). Local-first: off by default.
 */
@Singleton
class NetworkPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val NETWORK_FEATURES_ENABLED = booleanPreferencesKey("network_features_enabled_v1")
    }

    val networkEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.NETWORK_FEATURES_ENABLED] ?: false
    }

    suspend fun setNetworkEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.NETWORK_FEATURES_ENABLED] = enabled
        }
    }

    /**
     * One-time migration: anybody who already had a Navidrome or Jellyfin server saved keeps
     * working online after upgrading, instead of silently going offline on a default that only
     * makes sense for new local-only installs.
     */
    suspend fun migrateLegacyServerUsers(hasExistingServer: Boolean) {
        dataStore.edit { preferences ->
            if (preferences[Keys.NETWORK_FEATURES_ENABLED] == null) {
                preferences[Keys.NETWORK_FEATURES_ENABLED] = hasExistingServer
            }
        }
    }
}