package com.rishinnovations.isai.ui

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rishinnovations.isai.container
import com.rishinnovations.isai.model.RepeatMode
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.playback.MediaItems
import com.rishinnovations.isai.playback.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class QueueEntry(val index: Int, val mediaId: String, val song: Song?, val title: String, val artist: String)

data class PlayerUiState(
    val connected: Boolean = false,
    val hasMedia: Boolean = false,
    val mediaId: String? = null,
    val song: Song? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF
)

/**
 * UI-side view of the playback service. It never holds its own copy of the queue or position that could
 * drift: everything shown here is read back from the MediaController connected to the service's session.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var uiVisible = false

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private val _position = MutableStateFlow(0L)
    /** Playback position, refreshed only while the UI is visible and playing. */
    val positionMs: StateFlow<Long> = _position

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player, rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                sync(c, rebuildQueue = true)
                applyTicker()
            }
        }, ContextCompat.getMainExecutor(app))

        // Library may finish loading after the controller connects; re-resolve queue entries then.
        viewModelScope.launch {
            container.library.library.collect { controller?.let { c -> sync(c, rebuildQueue = true) } }
        }
    }

    fun onUiVisible(visible: Boolean) {
        uiVisible = visible
        controller?.let { _position.value = it.currentPosition.coerceAtLeast(0L) }
        applyTicker()
    }

    private fun applyTicker() {
        tickerJob?.cancel()
        if (!uiVisible) return
        tickerJob = viewModelScope.launch {
            while (true) {
                controller?.let { if (it.isPlaying) _position.value = it.currentPosition.coerceAtLeast(0L) }
                delay(500)
            }
        }
    }

    private fun sync(player: Player, rebuildQueue: Boolean) {
        val previous = _state.value
        val library = container.library.snapshot()
        val queue = if (rebuildQueue) {
            (0 until player.mediaItemCount).map { i ->
                val item = player.getMediaItemAt(i)
                val song = item.mediaId.toLongOrNull()?.let { library.byId[it] }
                QueueEntry(i, item.mediaId, song, song?.title ?: item.mediaMetadata.title?.toString() ?: "Unavailable song", song?.artist ?: item.mediaMetadata.artist?.toString().orEmpty())
            }
        } else previous.queue
        val current: MediaItem? = player.currentMediaItem
        val song = current?.mediaId?.toLongOrNull()?.let { library.byId[it] }
        _state.value = PlayerUiState(
            connected = true,
            hasMedia = current != null,
            mediaId = current?.mediaId,
            song = song,
            title = song?.title ?: current?.mediaMetadata?.title?.toString().orEmpty(),
            artist = song?.artist ?: current?.mediaMetadata?.artist?.toString().orEmpty(),
            album = song?.album ?: current?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L,
            queue = queue,
            currentIndex = player.currentMediaItemIndex,
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
        )
        _position.value = player.currentPosition.coerceAtLeast(0L)
    }

    // ------------------------------------------------------------------ actions

    /** Replaces the queue with [songs] and starts playing at [startIndex]. */
    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        else if (container.settingsSnapshot().shuffleNewQueues) c.shuffleModeEnabled = true
        c.setMediaItems(songs.map(MediaItems::fromSong), startIndex.coerceIn(0, songs.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    /** Streams tracks from an open online catalogue. Online items are not restored after a restart. */
    fun playOnline(tracks: List<com.rishinnovations.isai.data.online.OnlineTrack>, startIndex: Int = 0) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map(com.rishinnovations.isai.playback.OnlineMediaItems::from), startIndex.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSongs(songs, Random.nextInt(songs.size), shuffle = true)
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song))
        c.addMediaItem(c.currentMediaItemIndex + 1, MediaItems.fromSong(song))
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song))
        c.addMediaItem(MediaItems.fromSong(song))
    }

    fun addAllToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(songs)
        c.addMediaItems(songs.map(MediaItems::fromSong))
    }

    fun playQueueIndex(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.prepare()
        c.play()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }

    fun clearQueue() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        val c = controller ?: return
        c.seekTo(ms.coerceAtLeast(0L))
        _position.value = ms.coerceAtLeast(0L)
    }

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

    override fun onCleared() {
        tickerJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
