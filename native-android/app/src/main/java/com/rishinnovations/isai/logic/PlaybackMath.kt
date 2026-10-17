package com.rishinnovations.isai.logic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Rules for what counts as a listen. */
object PlayRules {
    /** A listening session is stored once the track has actually been heard for this long. */
    const val RECORD_AFTER_MS = 5_000L

    /** A play is counted after min(30 s, half the track) of real listening time. */
    fun countThresholdMs(durationMs: Long): Long =
        if (durationMs <= 0L) 30_000L else minOf(30_000L, durationMs / 2).coerceAtLeast(1_000L)
}

/** Accumulates real listening time for one playback of one track. Seeking does not add time. */
class ListenTracker(val songId: Long, val durationMs: Long) {
    var listenedMs: Long = 0L
        private set

    fun add(deltaMs: Long) {
        if (deltaMs > 0) listenedMs += deltaMs
    }

    val shouldRecord: Boolean get() = listenedMs >= PlayRules.RECORD_AFTER_MS || (durationMs in 1 until PlayRules.RECORD_AFTER_MS && listenedMs >= durationMs / 2)
    val counted: Boolean get() = listenedMs >= PlayRules.countThresholdMs(durationMs)
}

/** Volume shaping used for fades at track boundaries and for the sleep timer. */
object FadeMath {
    /**
     * Fade-out at the end and fade-in at the start of a track. The factor depends only on the
     * playback position so it stays correct across seeks, pauses and very short tracks.
     */
    fun trackFactor(positionMs: Long, durationMs: Long, fadeMs: Long): Float {
        if (fadeMs <= 0L || durationMs <= 0L) return 1f
        val fade = minOf(fadeMs, durationMs / 4).coerceAtLeast(1L)
        val fadeIn = (positionMs.toFloat() / fade).coerceIn(0f, 1f)
        val fadeOut = ((durationMs - positionMs).toFloat() / fade).coerceIn(0f, 1f)
        return minOf(fadeIn, fadeOut)
    }

    fun sleepFactor(remainingMs: Long, fadeMs: Long): Float =
        if (fadeMs <= 0L) 1f else (remainingMs.toFloat() / fadeMs).coerceIn(0f, 1f)
}

/** Countdown state machine for the sleep timer. Time is injected so it can be tested. */
class SleepTimerMachine(private val now: () -> Long) {
    sealed interface State {
        data object Off : State
        data class Running(val endAt: Long, val totalMs: Long) : State
        data class Paused(val remainingMs: Long, val totalMs: Long) : State
        data object EndOfTrack : State
    }

    @Volatile
    var state: State = State.Off
        private set

    fun start(durationMs: Long) {
        require(durationMs > 0)
        state = State.Running(now() + durationMs, durationMs)
    }

    fun startEndOfTrack() {
        state = State.EndOfTrack
    }

    fun pause() {
        val s = state
        if (s is State.Running) state = State.Paused(remainingMs().coerceAtLeast(1L), s.totalMs)
    }

    fun resume() {
        val s = state
        if (s is State.Paused) state = State.Running(now() + s.remainingMs, s.totalMs)
    }

    fun cancel() {
        state = State.Off
    }

    fun remainingMs(): Long = when (val s = state) {
        is State.Running -> (s.endAt - now()).coerceAtLeast(0L)
        is State.Paused -> s.remainingMs
        else -> 0L
    }

    /** True once a running countdown reaches zero; the machine returns to Off. */
    fun pollExpired(): Boolean {
        val s = state
        if (s is State.Running && now() >= s.endAt) {
            state = State.Off
            return true
        }
        return false
    }
}

/** Radix-2 FFT and band analysis used by the real-time visualiser. */
class SpectrumAnalyzer(val bands: Int = 32, val fftSize: Int = 1024) {
    private val window = FloatArray(fftSize) { (0.5 - 0.5 * cos(2.0 * PI * it / (fftSize - 1))).toFloat() }
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val smoothed = FloatArray(bands)

    /** Returns band levels in 0..1 for [samples] (mono, -1..1, length >= fftSize). */
    fun analyze(samples: FloatArray, sampleRate: Int): FloatArray {
        for (i in 0 until fftSize) {
            re[i] = samples[i] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val binHz = sampleRate.toFloat() / fftSize
        val minHz = 40f
        val maxHz = minOf(16_000f, sampleRate / 2f - 1f)
        val out = FloatArray(bands)
        for (b in 0 until bands) {
            val lo = minHz * Math.pow((maxHz / minHz).toDouble(), b.toDouble() / bands).toFloat()
            val hi = minHz * Math.pow((maxHz / minHz).toDouble(), (b + 1).toDouble() / bands).toFloat()
            val from = (lo / binHz).toInt().coerceIn(1, fftSize / 2 - 1)
            val to = (hi / binHz).toInt().coerceIn(from, fftSize / 2 - 1)
            var peak = 0f
            for (k in from..to) {
                val magnitude = sqrt(re[k] * re[k] + im[k] * im[k]) / (fftSize / 4f)
                if (magnitude > peak) peak = magnitude
            }
            val db = 20f * log10(peak.coerceAtLeast(1e-6f))
            val level = ((db + 60f) / 60f).coerceIn(0f, 1f)
            smoothed[b] = if (level > smoothed[b]) level else smoothed[b] * 0.82f + level * 0.18f
            out[b] = smoothed[b]
        }
        return out
    }

    companion object {
        fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2.0 * PI / len
                val wr = cos(angle).toFloat()
                val wi = sin(angle).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f
                    var ci = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val b = i + k + len / 2
                        val xr = re[b] * cr - im[b] * ci
                        val xi = re[b] * ci + im[b] * cr
                        re[b] = re[a] - xr; im[b] = im[a] - xi
                        re[a] += xr; im[a] += xi
                        val nr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}

/** Index maths for queue editing, kept free of Android types. */
object QueueMath {
    /** Index of the playing item after moving an item from [from] to [to]. */
    fun currentAfterMove(current: Int, from: Int, to: Int): Int = when {
        current == from -> to
        from < current && to >= current -> current - 1
        from > current && to <= current -> current + 1
        else -> current
    }
}
