package com.parthipan.colorclashcards.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AudioPreferences(private val context: Context) {

    private val soundEnabledKey = booleanPreferencesKey("sound_enabled")
    private val musicEnabledKey = booleanPreferencesKey("music_enabled")
    private val vibrationEnabledKey = booleanPreferencesKey("vibration_enabled")

    val isSoundEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[soundEnabledKey] ?: true
    }

    val isMusicEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[musicEnabledKey] ?: true
    }

    val isVibrationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[vibrationEnabledKey] ?: true
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[soundEnabledKey] = enabled
        }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[musicEnabledKey] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[vibrationEnabledKey] = enabled
        }
    }
}
