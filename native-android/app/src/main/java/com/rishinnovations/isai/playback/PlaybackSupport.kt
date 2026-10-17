@file:OptIn(UnstableApi::class)

package com.rishinnovations.isai.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.rishinnovations.isai.data.library.ArtworkProvider
import com.rishinnovations.isai.data.settings.AppSettings
import com.rishinnovations.isai.data.settings.SettingsRepository
import com.rishinnovations.isai.logic.FadeMath
import com.rishinnovations.isai.logic.SleepTimerMachine
import com.rishinnovations.isai.logic.SpectrumAnalyzer
import com.rishinnovations.isai.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** State the playback service publishes for the UI process-wide (they share one process). */
object PlaybackBridge {
    val effectsInfo = MutableStateFlow(EffectsInfo())
    val spectrum = MutableStateFlow(FloatArray(SPECTRUM_BANDS))
    val spectrumEnabled = AtomicBoolean(false)
    const val SPECTRUM_BANDS = 32
}

/** What the device's audio-effect stack actually supports. The UI only shows controls that are available. */
data class EffectsInfo(
    val eqSupported: Boolean = false,
    val bandCount: Int = 0,
    val minLevelMb: Int = 0,
    val maxLevelMb: Int = 0,
    val centerFreqsHz: List<Int> = emptyList(),
    val presets: List<String> = emptyList(),
    val bassSupported: Boolean = false,
    val loudnessSupported: Boolean = false,
    val sessionActive: Boolean = false
)

object MediaItems {
    fun fromSong(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(song.uriString)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setDurationMs(song.durationMs)
                .setArtworkUri(ArtworkProvider.uriFor(song.id))
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}

/** Persists the queue (song ids) as a plain text file; position and modes live in DataStore. */
object QueueStore {
    private fun file(context: Context) = File(context.filesDir, "queue.txt")

    fun save(context: Context, ids: List<Long>) {
        runCatching {
            val tmp = File(context.filesDir, "queue.tmp")
            tmp.writeText(ids.joinToString(","))
            tmp.renameTo(file(context))
        }
    }

    fun load(context: Context): List<Long> = runCatching {
        file(context).takeIf { it.exists() }?.readText()?.split(',')?.mapNotNull { it.toLongOrNull() }
    }.getOrNull().orEmpty()
}

/** Counts the real PCM flowing to the audio sink and turns it into spectrum bands for the visualiser. */
class SpectrumSink : TeeAudioProcessor.AudioBufferSink {
    private var sampleRate = 44_100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private val analyzer = SpectrumAnalyzer(PlaybackBridge.SPECTRUM_BANDS, 1024)
    private val ring = FloatArray(1024)
    private var filled = 0
    private var lastPublish = 0L

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        filled = 0
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!PlaybackBridge.spectrumEnabled.get()) return
        val buf = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        when (encoding) {
            C.ENCODING_PCM_16BIT -> while (buf.remaining() >= 2 * channels) {
                var sum = 0f
                repeat(channels) { sum += buf.short / 32768f }
                push(sum / channels)
            }
            C.ENCODING_PCM_FLOAT -> while (buf.remaining() >= 4 * channels) {
                var sum = 0f
                repeat(channels) { sum += buf.float }
                push(sum / channels)
            }
            else -> Unit // other encodings: no analysis, the UI falls back to a calm idle state
        }
    }

    private fun push(sample: Float) {
        ring[filled++] = sample
        if (filled == ring.size) {
            filled = 0
            val now = SystemClock.uptimeMillis()
            if (now - lastPublish >= 33) {
                lastPublish = now
                PlaybackBridge.spectrum.value = analyzer.analyze(ring, sampleRate)
            }
        }
    }
}

/**
 * Owns the Android audio effects attached to the player's audio session and keeps them in step with
 * the saved settings. Effects the device doesn't support are simply reported as unavailable.
 */
class AudioEffectsManager(private val settingsRepo: SettingsRepository, private val scope: CoroutineScope) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var latest = AppSettings()
    private var lastPreset = -2
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            settingsRepo.settings.collect {
                latest = it
                apply()
            }
        }
    }

    fun onSessionId(id: Int) {
        release()
        if (id == C.AUDIO_SESSION_ID_UNSET || id <= 0) {
            PlaybackBridge.effectsInfo.value = EffectsInfo()
            return
        }
        equalizer = runCatching { Equalizer(0, id) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, id) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(id) }.getOrNull()
        lastPreset = -2
        publishInfo()
        apply()
    }

    private fun publishInfo() {
        val eq = equalizer
        var info = EffectsInfo(
            bassSupported = bassBoost?.strengthSupported == true,
            loudnessSupported = loudness != null,
            sessionActive = true
        )
        if (eq != null) {
            runCatching {
                val range = eq.bandLevelRange
                info = info.copy(
                    eqSupported = true,
                    bandCount = eq.numberOfBands.toInt(),
                    minLevelMb = range[0].toInt(),
                    maxLevelMb = range[1].toInt(),
                    centerFreqsHz = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                    presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
                )
            }
        }
        PlaybackBridge.effectsInfo.value = info
    }

    private fun apply() {
        val s = latest
        equalizer?.let { eq ->
            runCatching {
                eq.enabled = s.eqEnabled
                val bands = eq.numberOfBands.toInt()
                val min = eq.bandLevelRange[0].toInt()
                val max = eq.bandLevelRange[1].toInt()
                if (s.eqPreset >= 0 && s.eqPreset < eq.numberOfPresets) {
                    if (s.eqPreset != lastPreset) {
                        eq.usePreset(s.eqPreset.toShort())
                        lastPreset = s.eqPreset
                        val levels = (0 until bands).map { eq.getBandLevel(it.toShort()).toInt() }
                        scope.launch { settingsRepo.setEqLevels(levels) }
                    }
                } else {
                    lastPreset = -1
                    if (s.eqLevels.size == bands) {
                        s.eqLevels.forEachIndexed { i, level -> eq.setBandLevel(i.toShort(), level.coerceIn(min, max).toShort()) }
                    }
                }
            }
        }
        bassBoost?.let { bb ->
            runCatching {
                bb.enabled = s.bassStrength > 0
                if (bb.strengthSupported) bb.setStrength(s.bassStrength.toShort())
            }
        }
        loudness?.let { le ->
            runCatching {
                le.enabled = s.loudnessGainMb > 0
                le.setTargetGain(s.loudnessGainMb)
            }
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        loudness = null
    }
}

/** Process-wide sleep timer. The countdown lives here, not in any screen, so navigation never resets it. */
class SleepTimerController(private val scope: CoroutineScope) {
    private val machine = SleepTimerMachine { SystemClock.elapsedRealtime() }
    private val _state = MutableStateFlow<SleepTimerMachine.State>(SleepTimerMachine.State.Off)
    private val _remaining = MutableStateFlow(0L)
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var ticker: Job? = null

    val state: StateFlow<SleepTimerMachine.State> = _state
    val remainingMs: StateFlow<Long> = _remaining
    val expired: SharedFlow<Unit> = _expired

    fun start(durationMs: Long) {
        machine.start(durationMs)
        run()
    }

    fun startEndOfTrack() {
        machine.startEndOfTrack()
        publish()
    }

    fun pause() {
        machine.pause()
        publish()
    }

    fun resume() {
        machine.resume()
        run()
    }

    fun cancel() {
        machine.cancel()
        ticker?.cancel()
        publish()
    }

    /** 1.0 normally; ramps to 0 over [fadeMs] as a running countdown nears zero. */
    fun volumeFactor(fadeMs: Long): Float =
        if (machine.state is SleepTimerMachine.State.Running) FadeMath.sleepFactor(machine.remainingMs(), fadeMs) else 1f

    private fun publish() {
        _state.value = machine.state
        _remaining.value = machine.remainingMs()
    }

    private fun run() {
        ticker?.cancel()
        publish()
        ticker = scope.launch {
            while (isActive) {
                if (machine.pollExpired()) {
                    publish()
                    _expired.tryEmit(Unit)
                    break
                }
                publish()
                if (machine.state !is SleepTimerMachine.State.Running) break
                delay(250)
            }
        }
    }
}
