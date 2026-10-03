package com.rishinnovations.isai.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.isaiDataStore by preferencesDataStore("isai_preferences")

class IsaiPreferences(private val context: Context) {
    private object Keys {
        val shuffle = booleanPreferencesKey("shuffle")
        val repeatMode = intPreferencesKey("repeat_mode")
        val positionMs = longPreferencesKey("position_ms")
    }

    val shuffle: Flow<Boolean> = context.isaiDataStore.data.map { it[Keys.shuffle] ?: false }
    val repeatMode: Flow<Int> = context.isaiDataStore.data.map { it[Keys.repeatMode] ?: 0 }
    val positionMs: Flow<Long> = context.isaiDataStore.data.map { it[Keys.positionMs] ?: 0L }

    suspend fun setShuffle(enabled: Boolean) { context.isaiDataStore.edit { it[Keys.shuffle] = enabled } }
    suspend fun setRepeatMode(mode: Int) { context.isaiDataStore.edit { it[Keys.repeatMode] = mode } }
    suspend fun setPositionMs(value: Long) { context.isaiDataStore.edit { it[Keys.positionMs] = value.coerceAtLeast(0L) } }
}
