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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class IsaiViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MusicRepository(app.contentResolver)
    private val prefs: SharedPreferences = app.getSharedPreferences("isai_player_state", 0)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _player = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _player

    private val _favorites = MutableStateFlow(loadFavoriteIds())
    val favorites: StateFlow<Set<Long>> = _favorites

    private val executor = Executors.newSingleThreadExecutor()
    private var controller: MediaController? = null
    private var queuedSongs: List<Song> = emptyList()
    private var lastPersistedPositionMs = -1L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        viewModelScope.launch {
            while (true) {
                controller?.let(::sync)
                delay(1000)
            }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get().also {
                    it.addListener(listener)
                    restoreSettings(it)
                    if (queuedSongs.isNotEmpty()) {
                        val safeIndex = _player.value.queueIndex
                            .coerceIn(0, queuedSongs.lastIndex)
                        install(it, queuedSongs, safeIndex, false)
                    }
                    sync(it)
                }
            }
        }, executor)
    }

    fun scan() {
        viewModelScope.launch {
            val scanned = runCatching { repository.loadLocalSongs() }.getOrDefault(emptyList())
            _songs.value = scanned

            val savedIds = prefs.getString("queue_ids", null)
                ?.split(',')
                ?.mapNotNull(String::toLongOrNull)
                .orEmpty()
            val restored = savedIds.mapNotNull { id -> scanned.firstOrNull { it.id == id } }

            if (restored.isNotEmpty()) {
                val safeIndex = prefs.getInt("queue_index", 0)
                    .coerceIn(0, restored.lastIndex)
                queuedSongs = restored
                _player.value = _player.value.copy(
                    queue = restored,
                    queueIndex = safeIndex,
                    currentSong = restored[safeIndex]
                )
                controller?.let { install(it, restored, safeIndex, true) }
            } else if (_player.value.queue.isEmpty() && scanned.isNotEmpty()) {
                replaceQueue(scanned)
            }
        }
    }

    fun replaceQueue(songs: List<Song>, index: Int = 0, playNow: Boolean = false) {
        if (songs.isEmpty()) return
        val safeIndex = index.coerceIn(0, songs.lastIndex)
        queuedSongs = songs
        _player.value = _player.value.copy(
            queue = songs,
            queueIndex = safeIndex,
            currentSong = songs[safeIndex]
        )
        persistQueue(songs, safeIndex)
        controller?.let { install(it, songs, safeIndex, playNow) }
    }

    fun play(song: Song) {
        val currentQueue = _player.value.queue
        val queue = when {
            currentQueue.any { it.id == song.id } -> currentQueue
            _songs.value.any { it.id == song.id } -> _songs.value
            else -> listOf(song)
        }
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        replaceQueue(queue, index, true)
    }

    fun playAll() {
        _songs.value.takeIf { it.isNotEmpty() }?.let { replaceQueue(it, 0, true) }
    }

    fun toggleFavorite(songId: Long) {
        val next = _favorites.value.toMutableSet().apply {
            if (!add(songId)) remove(songId)
        }.toSet()
        _favorites.value = next
        prefs.edit().putString("favorite_ids", next.joinToString(",")).apply()
    }

    fun removeFromQueue(songId: Long) {
        val player = controller ?: return
        var index = -1
        for (candidate in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(candidate).mediaId == songId.toString()) {
                index = candidate
                break
            }
        }
        if (index < 0 || player.currentMediaItemIndex == index) return

        val updatedQueue = _player.value.queue.toMutableList().apply { removeAt(index) }
        val currentIndex = player.currentMediaItemIndex
        val updatedIndex = if (currentIndex > index) currentIndex - 1 else currentIndex
        queuedSongs = updatedQueue
        _player.value = _player.value.copy(
            queue = updatedQueue,
            queueIndex = updatedIndex,
            currentSong = updatedQueue.getOrNull(updatedIndex)
        )
        persistQueue(updatedQueue, updatedIndex)
        player.removeMediaItem(index)
    }

    fun clearQueue() {
        controller?.stop()
        controller?.clearMediaItems()
        queuedSongs = emptyList()
        _player.value = PlayerState(
            shuffle = false,
            repeatMode = RepeatMode.OFF
        )
        prefs.edit()
            .remove("queue_ids")
            .remove("queue_index")
            .remove("position_ms")
            .putBoolean("shuffle", false)
            .putInt("repeat_mode", Player.REPEAT_MODE_OFF)
            .apply()
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        val safe = ms.coerceAtLeast(0L)
        controller?.seekTo(safe)
        prefs.edit().putLong("position_ms", safe).apply()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun loadFavoriteIds(): Set<Long> =
        prefs.getString("favorite_ids", null)
            ?.split(",")
            ?.mapNotNull(String::toLongOrNull)
            ?.toSet()
            .orEmpty()

    private fun restoreSettings(player: MediaController) {
        player.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
        player.repeatMode = prefs.getInt("repeat_mode", Player.REPEAT_MODE_OFF)
    }

    private fun install(
        player: MediaController,
        songs: List<Song>,
        index: Int,
        resumePosition: Boolean,
        playNow: Boolean = false
    ) {
        player.setMediaItems(songs.map(::toItem), index, 0L)
        player.prepare()
        if (resumePosition) {
            val position = prefs.getLong("position_ms", 0L)
            if (position > 0L) player.seekTo(position)
        }
        if (playNow) player.play()
    }

    private fun toItem(song: Song) = MediaItem.Builder()
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

    private fun sync(player: Player) {
        val queue = _player.value.queue
        val index = player.currentMediaItemIndex
        val current = queue.getOrNull(index)

        val wasPlaying = _player.value.isPlaying
        val next = _player.value.copy(
            currentSong = current,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: current?.durationMs ?: 0L,
            queueIndex = index,
            shuffle = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
        )
        _player.value = next

        if (current != null) persistQueue(queue, index)
        if (kotlin.math.abs(next.positionMs - lastPersistedPositionMs) >= 1000L || next.isPlaying != wasPlaying) {
            lastPersistedPositionMs = next.positionMs
        }
        prefs.edit()
            .putLong("position_ms", next.positionMs)
            .putBoolean("shuffle", next.shuffle)
            .putInt(
                "repeat_mode",
                when (next.repeatMode) {
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            )
            .apply()
    }

    private fun persistQueue(songs: List<Song>, index: Int) {
        prefs.edit()
            .putString("queue_ids", songs.joinToString(",") { it.id.toString() })
            .putInt("queue_index", index.coerceAtLeast(0))
            .apply()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        executor.shutdown()
        super.onCleared()
    }
}
