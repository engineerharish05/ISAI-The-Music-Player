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
    private val repo = MusicRepository(app.contentResolver)
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState
    private val controllerExecutor = Executors.newSingleThreadExecutor()
    private var controller: MediaController? = null
    private var pendingQueue: List<Song> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    init {
        viewModelScope.launch {
            while (true) {
                controller?.let(::publish)
                delay(500)
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            _songs.value = repo.scan()
            if (_playerState.value.queue.isEmpty()) setQueue(_songs.value)
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
                controller = future.get().also { c ->
                    c.addListener(listener)
                    if (pendingQueue.isNotEmpty()) {
                        val items = pendingQueue.map(::toMediaItem)
                        c.setMediaItems(items, 0, 0L)
                        c.prepare()
                    }
                    publish(c)
                }
            }
        }, controllerExecutor)
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0, autoPlay: Boolean = false) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        pendingQueue = songs
        _playerState.value = _playerState.value.copy(queue = songs, queueIndex = safeIndex)

        val c = controller ?: return
        val items = songs.map(::toMediaItem)
        c.setMediaItems(items, safeIndex, 0L)
        c.prepare()
        if (autoPlay) c.play()
    }

    fun play(song: Song) {
        val queue = _playerState.value.queue.ifEmpty { _songs.value }
        val actualQueue = queue.ifEmpty { listOf(song) }
        val index = actualQueue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        setQueue(actualQueue, index, autoPlay = true)
    }

    fun playAll() {
        if (_songs.value.isNotEmpty()) setQueue(_songs.value, 0, autoPlay = true)
    }

    fun pause() { controller?.pause() }
    fun resume() { controller?.play() }
    fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0L)) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
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

    private fun publish(player: Player) {
        val queue = _playerState.value.queue
        val index = player.currentMediaItemIndex
        val current = queue.getOrNull(index)
        val repeat = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        _playerState.value = PlayerState(
            currentSong = current,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: current?.durationMs ?: 0L,
            queue = queue,
            queueIndex = index,
            shuffle = player.shuffleModeEnabled,
            repeatMode = repeat
        )
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        controllerExecutor.shutdown()
        super.onCleared()
    }
}
