package com.rishinnovations.isai

import android.app.Application
import android.content.ComponentName
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
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _player = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _player
    private val executor = Executors.newSingleThreadExecutor()
    private var controller: MediaController? = null
    private var queuedSongs: List<Song> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        viewModelScope.launch {
            while (true) {
                controller?.let(::sync)
                delay(250)
            }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get().also {
                    it.addListener(listener)
                    if (queuedSongs.isNotEmpty()) install(it, queuedSongs, _player.value.queueIndex.coerceAtLeast(0), false)
                    sync(it)
                }
            }
        }, executor)
    }

    fun scan() {
        viewModelScope.launch {
            _songs.value = repository.loadLocalSongs()
            if (_player.value.queue.isEmpty()) replaceQueue(_songs.value)
        }
    }

    fun replaceQueue(songs: List<Song>, index: Int = 0, playNow: Boolean = false) {
        if (songs.isEmpty()) return
        val safe = index.coerceIn(0, songs.lastIndex)
        queuedSongs = songs
        _player.value = _player.value.copy(queue = songs, queueIndex = safe)
        controller?.let { install(it, songs, safe, playNow) }
    }

    fun play(song: Song) {
        val queue = _player.value.queue.ifEmpty { _songs.value }
        val position = queue.indexOfFirst { it.id == song.id }.let { if (it < 0) 0 else it }
        replaceQueue(if (queue.isEmpty()) listOf(song) else queue, position, true)
    }

    fun playAll() = _songs.value.takeIf { it.isNotEmpty() }?.let { replaceQueue(it, 0, true) }
    fun togglePlayPause() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms.coerceAtLeast(0L))
    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun install(player: MediaController, songs: List<Song>, index: Int, playNow: Boolean) {
        player.setMediaItems(songs.map(::toItem), index, 0L)
        player.prepare()
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
        ).build()

    private fun sync(player: Player) {
        val queue = _player.value.queue
        val index = player.currentMediaItemIndex
        val current = queue.getOrNull(index)
        _player.value = _player.value.copy(
            currentSong = current,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: current?.durationMs ?: 0,
            queueIndex = index,
            shuffle = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
        )
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        executor.shutdown()
        super.onCleared()
    }
}
