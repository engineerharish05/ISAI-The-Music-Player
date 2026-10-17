@file:OptIn(UnstableApi::class)

package com.rishinnovations.isai.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.common.audio.AudioProcessor
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rishinnovations.isai.AppMessages
import com.rishinnovations.isai.MainActivity
import com.rishinnovations.isai.R
import com.rishinnovations.isai.container
import com.rishinnovations.isai.data.settings.AppSettings
import com.rishinnovations.isai.data.settings.SavedPlayback
import com.rishinnovations.isai.logic.FadeMath
import com.rishinnovations.isai.logic.ListenTracker
import com.rishinnovations.isai.logic.SleepTimerMachine
import com.rishinnovations.isai.model.RepeatMode
import com.rishinnovations.isai.widget.IsaiWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the one ExoPlayer for the whole app. The UI, the notification, the lock screen, the widget and
 * Android Auto all control this same player through its media session, so queue and state never diverge.
 */
class PlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private lateinit var effects: AudioEffectsManager
    private lateinit var recorder: ListeningRecorder
    private lateinit var browseTree: BrowseTree
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settings = AppSettings()
    private var tickerJob: Job? = null
    private var saveJob: Job? = null
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        val container = container
        browseTree = BrowseTree(container)
        val sink = SpectrumSink()

        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(sink)))
                    .build()
        }

        player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).setSessionActivity(openApp).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.mipmap.ic_launcher_monochrome) })

        effects = AudioEffectsManager(container.settings, scope).also { it.start(); it.onSessionId(player.audioSessionId) }
        recorder = ListeningRecorder(container.userData, container.library, container.scope)
        player.addListener(PlayerEvents())

        scope.launch {
            container.settings.settings.collect { new ->
                settings = new
                player.setPlaybackSpeed(new.playbackSpeed)
            }
        }
        scope.launch { container.sleepTimer.expired.collect { fadeOutPause() } }
        scope.launch {
            container.sleepTimer.state.collect { state -> player.pauseAtEndOfMediaItems = state is SleepTimerMachine.State.EndOfTrack }
        }
        scope.launch { restoreQueue() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        saveStateNow()
        recorder.flush()
        IsaiWidgetProvider.push(this, null, null, null, false)
        tickerJob?.cancel()
        effects.release()
        session?.release()
        player.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- restore & persistence

    private suspend fun restoreQueue() {
        val container = container
        container.library.loaded.first { it }
        val ids = QueueStore.load(this)
        if (ids.isEmpty() || player.mediaItemCount > 0) return
        val saved = container.settings.savedPlayback.first()
        val library = container.library.snapshot()
        val songs = ids.mapNotNull { library.byId[it] }
        if (songs.isEmpty() || player.mediaItemCount > 0) return
        val wanted = ids.getOrNull(saved.index)
        val index = songs.indexOfFirst { it.id == wanted }.takeIf { it >= 0 } ?: 0
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = when (saved.repeat) {
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        // Restored paused: playback never starts by itself when the app is reopened.
        player.setMediaItems(songs.map(MediaItems::fromSong), index, saved.positionMs.coerceAtLeast(0L))
        player.prepare()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            saveStateNow()
        }
    }

    private fun saveStateNow() {
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        val state = SavedPlayback(
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
        )
        val container = container
        container.scope.launch {
            QueueStore.save(this@PlaybackService, ids)
            if (ids.isEmpty()) container.settings.clearPlayback() else container.settings.savePlayback(state)
        }
    }

    // ---------------------------------------------------------------- tickers: volume shaping, history, position

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            var sinceSave = 0L
            var sinceRecord = 0L
            while (isActive) {
                applyVolume()
                delay(250)
                val now = SystemClock.elapsedRealtime()
                val delta = (now - lastTick).coerceIn(0L, 1000L)
                lastTick = now
                if (player.isPlaying) {
                    sinceRecord += delta
                    sinceSave += delta
                    recorder.add(delta)
                    if (sinceRecord >= 10_000L) { recorder.persist(); sinceRecord = 0L }
                    if (sinceSave >= 5_000L) { saveStateNow(); sinceSave = 0L }
                }
            }
        }
    }

    private fun applyVolume() {
        val repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE
        val trackFade = if (repeatOne) 1f else FadeMath.trackFactor(player.currentPosition, player.duration.takeIf { it > 0 } ?: 0L, settings.fadeSeconds * 1000L)
        val sleepFade = container.sleepTimer.volumeFactor(if (settings.sleepFadeOut) SLEEP_FADE_MS else 0L)
        val target = (trackFade * sleepFade).coerceIn(0f, 1f)
        if (kotlin.math.abs(player.volume - target) > 0.01f) player.volume = target
    }

    private fun fadeOutPause() {
        player.pause()
        player.volume = 1f
    }

    // ---------------------------------------------------------------- player events

    private inner class PlayerEvents : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recorder.onItemChanged(mediaItem?.mediaId?.toLongOrNull())
            scheduleSave()
            pushWidget()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = scheduleSave()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = scheduleSave()
        override fun onRepeatModeChanged(repeatMode: Int) = scheduleSave()
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = pushWidget()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                consecutiveErrors = 0
                startTicker()
                applyVolume()
            } else {
                recorder.persist()
                saveStateNow()
            }
            pushWidget()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                container.sleepTimer.state.value is SleepTimerMachine.State.EndOfTrack
            ) {
                container.sleepTimer.cancel()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = effects.onSessionId(audioSessionId)

        override fun onPlayerError(error: PlaybackException) {
            val title = player.currentMediaItem?.mediaMetadata?.title ?: "this song"
            consecutiveErrors++
            AppMessages.post("Can't play \"$title\": ${describe(error)}")
            // Skip past unplayable tracks, but give up once the whole queue has failed in a row.
            if (player.hasNextMediaItem() && consecutiveErrors < player.mediaItemCount) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                consecutiveErrors = 0
            }
        }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "the file is missing or no longer accessible"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "the audio is corrupted or in an unsupported format"
        else -> "playback failed (${error.errorCodeName})"
    }

    private fun pushWidget() {
        val item = player.currentMediaItem
        IsaiWidgetProvider.push(
            this,
            title = item?.mediaMetadata?.title?.toString(),
            artist = item?.mediaMetadata?.artist?.toString(),
            songId = item?.mediaId?.toLongOrNull(),
            playing = player.isPlaying
        )
    }

    // ---------------------------------------------------------------- media session callback

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(browseTree.root(), params))

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = browseTree.item(mediaId)
            return Futures.immediateFuture(if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val all = browseTree.children(parentId)
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params))
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // A single item chosen from a browse list expands to the whole list so next/previous work.
            val single = mediaItems.singleOrNull()
            val expanded = single?.mediaId?.let { browseTree.queueFor(it) }
            if (expanded != null) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(expanded.first.map(MediaItems::fromSong), expanded.second, C.TIME_UNSET)
                )
            }
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems.map(::resolve), startIndex, startPositionMs))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems.map(::resolve).toMutableList())

        private fun resolve(item: MediaItem): MediaItem {
            if (item.localConfiguration != null) return item
            val songId = item.mediaId.substringAfter('|').toLongOrNull() ?: return item
            return container.library.snapshot().byId[songId]?.let(MediaItems::fromSong) ?: item
        }
    }

    companion object {
        private const val SLEEP_FADE_MS = 10_000L
    }
}

/**
 * Records real listening for the statistics screen. A session is stored after 5 seconds of audible
 * playback and counts as a play after min(30 s, half the track). Seeking and UI refreshes add nothing.
 */
class ListeningRecorder(
    private val userData: com.rishinnovations.isai.data.user.UserDataRepository,
    private val library: com.rishinnovations.isai.data.library.LibraryRepository,
    private val scope: CoroutineScope
) {
    private class Session(val tracker: ListenTracker, val startedAt: Long) {
        var rowId: Long? = null
    }

    private var current: Session? = null
    private val mutex = Mutex()

    fun onItemChanged(songId: Long?) {
        persist()
        current = songId?.let { id ->
            Session(ListenTracker(id, library.snapshot().byId[id]?.durationMs ?: 0L), System.currentTimeMillis())
        }
    }

    fun add(deltaMs: Long) {
        current?.tracker?.add(deltaMs)
    }

    fun persist() {
        val session = current ?: return
        val tracker = session.tracker
        if (!tracker.shouldRecord) return
        val listened = tracker.listenedMs
        val counted = tracker.counted
        scope.launch(Dispatchers.Default) {
            mutex.withLock {
                val id = session.rowId
                if (id == null) session.rowId = userData.insertPlayEvent(tracker.songId, session.startedAt, listened, counted)
                else userData.updatePlayEvent(id, listened, counted)
            }
        }
    }

    fun flush() = persist()
}
