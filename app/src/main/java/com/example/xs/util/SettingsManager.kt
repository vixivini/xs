package com.example.xs.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        private val WIFI_ONLY_PRELOAD = booleanPreferencesKey("wifi_only_preload")
        private val PROFILE_PICTURE_URI = androidx.datastore.preferences.core.stringPreferencesKey("profile_picture_uri")
    }

    val wifiOnlyPreload: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[WIFI_ONLY_PRELOAD] ?: false
        }

    val profilePictureUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PROFILE_PICTURE_URI]
        }

    suspend fun setProfilePictureUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PROFILE_PICTURE_URI)
            } else {
                preferences[PROFILE_PICTURE_URI] = uri
            }
        }
    }

    suspend fun setWifiOnlyPreload(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_ONLY_PRELOAD] = enabled
        }
    }

    fun shouldPreload(isWifiOnly: Boolean): Boolean {
        if (!isWifiOnly) return true
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
