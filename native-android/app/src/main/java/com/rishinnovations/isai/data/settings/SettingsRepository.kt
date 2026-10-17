package com.rishinnovations.isai.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rishinnovations.isai.model.RepeatMode
import com.rishinnovations.isai.model.SortField
import com.rishinnovations.isai.model.SortOrder
import com.rishinnovations.isai.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.playbackStore: DataStore<Preferences> by preferencesDataStore(name = "playback_state")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val visualizerEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val shuffleNewQueues: Boolean = false,
    val fadeSeconds: Int = 0,
    val playbackSpeed: Float = 1f,
    val sleepFadeOut: Boolean = true,
    val sortField: SortField = SortField.TITLE,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val includeNonMusic: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: Int = -1,
    val eqLevels: List<Int> = emptyList(),
    val bassStrength: Int = 0,
    val loudnessGainMb: Int = 0
)

data class SavedPlayback(
    val index: Int = 0,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF
)

class SettingsRepository(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val visualizer = booleanPreferencesKey("visualizer")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val shuffleNew = booleanPreferencesKey("shuffle_new_queues")
        val fade = intPreferencesKey("fade_seconds")
        val speed = floatPreferencesKey("speed")
        val sleepFade = booleanPreferencesKey("sleep_fade")
        val sortField = stringPreferencesKey("sort_field")
        val sortOrder = stringPreferencesKey("sort_order")
        val nonMusic = booleanPreferencesKey("include_non_music")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqPreset = intPreferencesKey("eq_preset")
        val eqLevels = stringPreferencesKey("eq_levels")
        val bass = intPreferencesKey("bass")
        val loudness = intPreferencesKey("loudness")

        val pIndex = intPreferencesKey("index")
        val pPosition = longPreferencesKey("position")
        val pShuffle = booleanPreferencesKey("shuffle")
        val pRepeat = stringPreferencesKey("repeat")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            themeMode = p[K.theme].toEnum(ThemeMode.DARK),
            visualizerEnabled = p[K.visualizer] ?: true,
            reduceMotion = p[K.reduceMotion] ?: false,
            shuffleNewQueues = p[K.shuffleNew] ?: false,
            fadeSeconds = (p[K.fade] ?: 0).coerceIn(0, 12),
            playbackSpeed = (p[K.speed] ?: 1f).coerceIn(0.5f, 2f),
            sleepFadeOut = p[K.sleepFade] ?: true,
            sortField = p[K.sortField].toEnum(SortField.TITLE),
            sortOrder = p[K.sortOrder].toEnum(SortOrder.ASCENDING),
            includeNonMusic = p[K.nonMusic] ?: false,
            eqEnabled = p[K.eqEnabled] ?: false,
            eqPreset = p[K.eqPreset] ?: -1,
            eqLevels = p[K.eqLevels]?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty(),
            bassStrength = (p[K.bass] ?: 0).coerceIn(0, 1000),
            loudnessGainMb = (p[K.loudness] ?: 0).coerceIn(0, 2000)
        )
    }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { context.settingsStore.edit { block(it) } }
    }

    suspend fun setTheme(mode: ThemeMode) = edit { it[K.theme] = mode.name }
    suspend fun setVisualizer(on: Boolean) = edit { it[K.visualizer] = on }
    suspend fun setReduceMotion(on: Boolean) = edit { it[K.reduceMotion] = on }
    suspend fun setShuffleNewQueues(on: Boolean) = edit { it[K.shuffleNew] = on }
    suspend fun setFadeSeconds(seconds: Int) = edit { it[K.fade] = seconds.coerceIn(0, 12) }
    suspend fun setSpeed(speed: Float) = edit { it[K.speed] = speed.coerceIn(0.5f, 2f) }
    suspend fun setSleepFadeOut(on: Boolean) = edit { it[K.sleepFade] = on }
    suspend fun setSort(field: SortField, order: SortOrder) = edit { it[K.sortField] = field.name; it[K.sortOrder] = order.name }
    suspend fun setIncludeNonMusic(on: Boolean) = edit { it[K.nonMusic] = on }

    suspend fun setEqEnabled(on: Boolean) = edit { it[K.eqEnabled] = on }
    suspend fun setEqPreset(index: Int) = edit { it[K.eqPreset] = index }
    suspend fun setEqLevels(levels: List<Int>, preset: Int? = null) = edit {
        it[K.eqLevels] = levels.joinToString(",")
        if (preset != null) it[K.eqPreset] = preset
    }
    suspend fun setBass(strength: Int) = edit { it[K.bass] = strength.coerceIn(0, 1000) }
    suspend fun setLoudness(gainMb: Int) = edit { it[K.loudness] = gainMb.coerceIn(0, 2000) }
    suspend fun resetEffects(bandCount: Int) = edit {
        it[K.eqPreset] = -1
        it[K.eqLevels] = List(bandCount) { 0 }.joinToString(",")
        it[K.bass] = 0
        it[K.loudness] = 0
    }

    // ---- Playback state (kept in its own small store so frequent position saves stay cheap) ----

    val savedPlayback: Flow<SavedPlayback> = context.playbackStore.data.map { p ->
        SavedPlayback(
            index = p[K.pIndex] ?: 0,
            positionMs = p[K.pPosition] ?: 0L,
            shuffle = p[K.pShuffle] ?: false,
            repeat = p[K.pRepeat].toEnum(RepeatMode.OFF)
        )
    }

    suspend fun savePlayback(state: SavedPlayback) {
        runCatching {
            context.playbackStore.edit {
                it[K.pIndex] = state.index
                it[K.pPosition] = state.positionMs
                it[K.pShuffle] = state.shuffle
                it[K.pRepeat] = state.repeat.name
            }
        }
    }

    suspend fun clearPlayback() {
        runCatching { context.playbackStore.edit { it.clear() } }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
