package com.rishinnovations.isai.logic

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayRulesTest {
    @Test fun thresholdIsThirtySecondsForNormalTracks() = assertEquals(30_000L, PlayRules.countThresholdMs(240_000))
    @Test fun thresholdIsHalfForShortTracks() = assertEquals(10_000L, PlayRules.countThresholdMs(20_000))
    @Test fun unknownDurationUsesThirtySeconds() = assertEquals(30_000L, PlayRules.countThresholdMs(0))

    @Test fun trackerRecordsAfterFiveSeconds() {
        val t = ListenTracker(1, 200_000)
        t.add(4_000); assertFalse(t.shouldRecord)
        t.add(1_000); assertTrue(t.shouldRecord)
        assertFalse(t.counted)
    }

    @Test fun trackerCountsAfterThreshold() {
        val t = ListenTracker(1, 200_000)
        t.add(29_999); assertFalse(t.counted)
        t.add(1); assertTrue(t.counted)
    }

    @Test fun negativeDeltaIgnored() {
        val t = ListenTracker(1, 200_000)
        t.add(-5_000)
        assertEquals(0L, t.listenedMs)
    }

    @Test fun veryShortTrackRecordsAtHalf() {
        val t = ListenTracker(1, 4_000)
        t.add(2_000)
        assertTrue(t.shouldRecord)
    }
}

class FadeMathTest {
    @Test fun noFadeWhenDisabled() = assertEquals(1f, FadeMath.trackFactor(0, 100_000, 0), 0f)
    @Test fun fadesInAtStart() {
        assertEquals(0f, FadeMath.trackFactor(0, 100_000, 4_000), 0.001f)
        assertEquals(0.5f, FadeMath.trackFactor(2_000, 100_000, 4_000), 0.001f)
        assertEquals(1f, FadeMath.trackFactor(50_000, 100_000, 4_000), 0.001f)
    }
    @Test fun fadesOutAtEnd() {
        assertEquals(0.5f, FadeMath.trackFactor(98_000, 100_000, 4_000), 0.001f)
        assertEquals(0f, FadeMath.trackFactor(100_000, 100_000, 4_000), 0.001f)
    }
    @Test fun shortTrackFadeIsCapped() {
        // fade limited to a quarter of the track, so the middle stays at full volume
        assertEquals(1f, FadeMath.trackFactor(5_000, 10_000, 12_000), 0.001f)
    }
    @Test fun unknownDurationIsFullVolume() = assertEquals(1f, FadeMath.trackFactor(1000, 0, 4000), 0f)
    @Test fun sleepFactor() {
        assertEquals(1f, FadeMath.sleepFactor(10_000, 5_000), 0f)
        assertEquals(0.5f, FadeMath.sleepFactor(2_500, 5_000), 0.001f)
        assertEquals(1f, FadeMath.sleepFactor(0, 0), 0f)
    }
}

class SleepTimerMachineTest {
    private var clock = 0L
    private val m = SleepTimerMachine { clock }

    @Test fun countsDownAndExpiresOnce() {
        m.start(60_000)
        clock = 30_000
        assertEquals(30_000L, m.remainingMs())
        assertFalse(m.pollExpired())
        clock = 60_000
        assertTrue(m.pollExpired())
        assertEquals(SleepTimerMachine.State.Off, m.state)
        assertFalse(m.pollExpired())
    }

    @Test fun pauseKeepsRemainingTime() {
        m.start(60_000)
        clock = 20_000
        m.pause()
        clock = 500_000
        assertEquals(40_000L, m.remainingMs())
        assertFalse(m.pollExpired())
        m.resume()
        clock = 520_000
        assertEquals(20_000L, m.remainingMs())
    }

    @Test fun cancelTurnsOff() {
        m.start(1_000)
        m.cancel()
        assertEquals(SleepTimerMachine.State.Off, m.state)
        assertEquals(0L, m.remainingMs())
    }

    @Test fun endOfTrackState() {
        m.startEndOfTrack()
        assertEquals(SleepTimerMachine.State.EndOfTrack, m.state)
        assertFalse(m.pollExpired())
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsZeroDuration() = m.start(0)
}

class SpectrumAnalyzerTest {
    private val rate = 44_100

    private fun tone(hz: Double, n: Int = 1024) = FloatArray(n) { (0.8 * sin(2 * PI * hz * it / rate)).toFloat() }

    @Test fun silenceGivesZeroLevels() {
        val out = SpectrumAnalyzer().analyze(FloatArray(1024), rate)
        assertTrue(out.all { it == 0f })
    }

    @Test fun toneLightsUpTheRightBand() {
        val a = SpectrumAnalyzer()
        val low = a.analyze(tone(200.0), rate)
        val b = SpectrumAnalyzer()
        val high = b.analyze(tone(8000.0), rate)
        assertTrue(low.indices.maxByOrNull { low[it] }!! < high.indices.maxByOrNull { high[it] }!!)
        assertTrue(low.max() > 0.5f)
        assertTrue(low.all { it in 0f..1f })
    }

    @Test fun fftMatchesKnownSine() {
        val n = 64
        val re = FloatArray(n) { sin(2 * PI * 4 * it / n).toFloat() }
        val im = FloatArray(n)
        SpectrumAnalyzer.fft(re, im)
        val mags = FloatArray(n) { kotlin.math.hypot(re[it], im[it]) }
        assertEquals(4, mags.indices.take(n / 2).maxByOrNull { mags[it] })
        assertEquals(n / 2f, mags[4], 0.5f)
    }

    @Test fun levelsDecaySmoothly() {
        val a = SpectrumAnalyzer()
        val loud = a.analyze(tone(1000.0), rate)
        val after = a.analyze(FloatArray(1024), rate)
        val band = loud.indices.maxByOrNull { loud[it] }!!
        assertTrue(after[band] < loud[band])
        assertTrue(after[band] > 0f)
    }
}

class QueueMathTest {
    @Test fun movingCurrentItemFollowsIt() = assertEquals(5, QueueMath.currentAfterMove(2, 2, 5))
    @Test fun movingItemFromBeforeToAfterShiftsCurrentDown() = assertEquals(2, QueueMath.currentAfterMove(3, 1, 4))
    @Test fun movingItemFromAfterToBeforeShiftsCurrentUp() = assertEquals(4, QueueMath.currentAfterMove(3, 5, 1))
    @Test fun unrelatedMoveLeavesCurrent() = assertEquals(0, QueueMath.currentAfterMove(0, 3, 5))
}
