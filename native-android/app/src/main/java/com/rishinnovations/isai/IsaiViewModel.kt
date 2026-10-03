package com.rishinnovations.isai

import android.app.Application
import android.content.ComponentName
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentSongId: Long? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class IsaiViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository(app.contentResolver)
    private val prefs: SharedPreferences = app.getSharedPreferences("isai_preferences", 0)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _favorites = MutableStateFlow(
        prefs.getStringSet("favorites", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    )
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow(
        prefs.all.keys.filter { it.startsWith("playlist_") }
            .associateWith { prefs.getStringSet(it, emptySet()) ?: emptySet() }
            .mapKeys { it.key.removePrefix("playlist_") }
            .mapValues { it.value.mapNotNull(String::toLongOrNull).toSet() }
    )
    val playlists: StateFlow<Map<String, Set<Long>>> = _playlists.asStateFlow()

    private val _player = MutableStateFlow(PlayerUiState())
    val player: StateFlow<PlayerUiState> = _player.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private var controller: MediaController? = null

    fun scan() {
        viewModelScope.launch {
            runCatching { repo.scan() }
                .onSuccess { _songs.value = it }
                .onFailure { _error.value = "Music access is required to scan your device." }
        }
    }

    fun clearError() { _error.value = null }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener({
            runCatching {
                val c = future.get()
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) = publish()
                    override fun onPlaybackStateChanged(playbackState: Int) = publish()
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) = publish()
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = publish()
                    override fun onRepeatModeChanged(repeatMode: Int) = publish()
                })
                publish()
            }.onFailure { _error.value = "Playback service could not be started." }
        }, executor)
    }

    private fun publish() {
        val c = controller ?: return
        _player.value = PlayerUiState(
            connected = true,
            isPlaying = c.isPlaying,
            currentSongId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode
        )
    }

    fun tick() {
        if (controller != null) publish()
    }

    fun play(song: Song) {
        controller?.let { c ->
            c.setMediaItem(toMediaItem(song))
            c.prepare()
            c.play()
            publish()
        }
    }

    fun playAll(list: List<Song>, startIndex: Int = 0) {
        if (list.isEmpty()) return
        controller?.let { c ->
            c.setMediaItems(list.map(::toMediaItem))
            c.seekToDefaultPosition(startIndex.coerceIn(0, list.lastIndex))
            c.prepare()
            c.play()
            publish()
        }
    }

    private fun toMediaItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.artworkUri)
                    .build()
            )
            .build()

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
        publish()
    }

    fun next() { controller?.seekToNextMediaItem(); publish() }
    fun previous() { controller?.seekToPreviousMediaItem(); publish() }
    fun seekTo(position: Long) { controller?.seekTo(position); publish() }

    fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
            publish()
        }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            publish()
        }
    }

    fun toggleFavorite(id: Long) {
        val next = _favorites.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _favorites.value = next
        prefs.edit().putStringSet("favorites", next.map(Long::toString).toSet()).apply()
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || _playlists.value.containsKey(clean)) return
        val next = _playlists.value.toMutableMap()
        next[clean] = emptySet()
        _playlists.value = next
        prefs.edit().putStringSet("playlist_$clean", emptySet()).apply()
    }

    fun addToPlaylist(name: String, songId: Long) {
        val next = _playlists.value.toMutableMap()
        val ids = (next[name] ?: emptySet()).toMutableSet()
        ids.add(songId)
        next[name] = ids
        _playlists.value = next
        prefs.edit().putStringSet("playlist_$name", ids.map(Long::toString).toSet()).apply()
    }

    fun removeFromPlaylist(name: String, songId: Long) {
        val next = _playlists.value.toMutableMap()
        next[name] = (next[name] ?: emptySet()) - songId
        _playlists.value = next
        prefs.edit().putStringSet("playlist_$name", next[name]!!.map(Long::toString).toSet()).apply()
    }

    fun deletePlaylist(name: String) {
        val next = _playlists.value.toMutableMap()
        next.remove(name)
        _playlists.value = next
        prefs.edit().remove("playlist_$name").apply()
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        executor.shutdown()
        super.onCleared()
    }
}
