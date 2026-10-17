package com.rishinnovations.isai.logic

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test fun parsesTimestampsAndSorts() {
        val lyrics = LrcParser.parse("[00:12.50]Second\n[00:01.00]First", "t")!!
        assertTrue(lyrics.synced)
        assertEquals(listOf("First", "Second"), lyrics.lines.map { it.text })
        assertEquals(listOf(1000L, 12500L), lyrics.lines.map { it.timeMs })
    }

    @Test fun fractionDigitsAreScaled() {
        val lines = LrcParser.parse("[01:02.5]a\n[01:02.50]b\n[01:02.500]c\n[01:02]d", "t")!!.lines
        assertEquals(listOf(62500L, 62500L, 62500L, 62000L), lines.map { it.timeMs })
    }

    @Test fun multipleStampsOnOneLine() {
        val lines = LrcParser.parse("[00:10.00][00:20.00]Chorus", "t")!!.lines
        assertEquals(2, lines.size)
        assertEquals(listOf(10_000L, 20_000L), lines.map { it.timeMs })
        assertTrue(lines.all { it.text == "Chorus" })
    }

    @Test fun offsetTagShiftsTimes() {
        val lines = LrcParser.parse("[offset:500]\n[00:01.00]x", "t")!!.lines
        assertEquals(1500L, lines.single().timeMs)
    }

    @Test fun metadataTagsAreIgnored() {
        val lyrics = LrcParser.parse("[ar:Someone]\n[ti:Title]\n[00:01.00]Line", "t")!!
        assertEquals(1, lyrics.lines.size)
    }

    @Test fun plainTextFallsBackToUnsynced() {
        val lyrics = LrcParser.parse("one\ntwo\n\nthree", "t")!!
        assertFalse(lyrics.synced)
        assertEquals(listOf("one", "two", "three"), lyrics.lines.map { it.text })
        assertTrue(lyrics.lines.all { it.timeMs == null })
    }

    @Test fun blankInputGivesNull() {
        assertNull(LrcParser.parse("", "t"))
        assertNull(LrcParser.parse("   \n \n", "t"))
        assertNull(LrcParser.parse("[ar:Only metadata]", "t"))
    }

    @Test fun wordLevelTimingsAreStripped() {
        val lines = LrcParser.parse("[00:01.00]<00:01.00>Hel<00:01.50>lo", "t")!!.lines
        assertEquals("Hello", lines.single().text)
    }

    @Test fun currentIndexFollowsPosition() {
        val lines = LrcParser.parse("[00:05.00]a\n[00:10.00]b\n[00:15.00]c", "t")!!.lines
        assertEquals(-1, LrcParser.currentIndex(lines, 0))
        assertEquals(-1, LrcParser.currentIndex(lines, 4_999))
        assertEquals(0, LrcParser.currentIndex(lines, 5_000))
        assertEquals(1, LrcParser.currentIndex(lines, 12_000))
        assertEquals(2, LrcParser.currentIndex(lines, 999_999))
    }

    @Test fun byteOrderMarkIsRemoved() {
        val lyrics = LrcParser.parse("﻿[00:01.00]Hi", "t")!!
        assertEquals(1000L, lyrics.lines.single().timeMs)
    }
}

class Id3LyricsTest {
    private fun syncSafe(n: Int) = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())
    private fun be32(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

    private fun v23Tag(vararg frames: Pair<String, ByteArray>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, data) in frames) {
            body.write(id.toByteArray(Charsets.ISO_8859_1)); body.write(be32(data.size)); body.write(byteArrayOf(0, 0)); body.write(data)
        }
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray()); out.write(byteArrayOf(3, 0, 0)); out.write(syncSafe(body.size())); out.write(body.toByteArray())
        return out.toByteArray()
    }

    @Test fun readsUnsyncedLyrics() {
        val uslt = byteArrayOf(0) + "eng".toByteArray() + byteArrayOf(0) + "Hello\nWorld".toByteArray(Charsets.ISO_8859_1)
        val result = Id3Lyrics.parse(v23Tag("TIT2" to byteArrayOf(0, 'x'.code.toByte()), "USLT" to uslt))
        assertNotNull(result)
        assertEquals("Hello\nWorld", result!!.unsynced)
        assertNull(result.synced)
    }

    @Test fun readsSyncedLyricsInMilliseconds() {
        val sylt = ByteArrayOutputStream().apply {
            write(0); write("eng".toByteArray()); write(2); write(1); write(0) // encoding, lang, ms format, type, empty descriptor
            write("First".toByteArray()); write(0); write(be32(1000))
            write("Second".toByteArray()); write(0); write(be32(2500))
        }.toByteArray()
        val result = Id3Lyrics.parse(v23Tag("SYLT" to sylt))!!
        assertEquals(listOf("First", "Second"), result.synced!!.map { it.text })
        assertEquals(listOf(1000L, 2500L), result.synced!!.map { it.timeMs })
    }

    @Test fun mpegFrameTimestampsAreNotGuessed() {
        val sylt = ByteArrayOutputStream().apply {
            write(0); write("eng".toByteArray()); write(1); write(1); write(0)
            write("Line".toByteArray()); write(0); write(be32(100))
        }.toByteArray()
        assertNull(Id3Lyrics.parse(v23Tag("SYLT" to sylt)))
    }

    @Test fun notAnId3TagGivesNull() {
        assertNull(Id3Lyrics.parse(ByteArray(64)))
        assertEquals(-1, Id3Lyrics.tagSize(ByteArray(4)))
    }

    @Test fun truncatedTagDoesNotCrash() {
        val tag = v23Tag("USLT" to byteArrayOf(0, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0, 'a'.code.toByte()))
        Id3Lyrics.parse(tag.copyOf(tag.size - 3))
    }

    @Test fun tagWithoutLyricsGivesNull() {
        assertNull(Id3Lyrics.parse(v23Tag("TIT2" to byteArrayOf(0, 'x'.code.toByte()))))
    }
}

class FlacLyricsTest {
    private fun le32(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

    private fun vorbis(vararg comments: String): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "isai-test".toByteArray()
        out.write(le32(vendor.size)); out.write(vendor); out.write(le32(comments.size))
        comments.forEach { val b = it.toByteArray(Charsets.UTF_8); out.write(le32(b.size)); out.write(b) }
        return out.toByteArray()
    }

    private fun flac(block: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        out.write(byteArrayOf(0x00, 0, 0, 2, 0, 0)) // a tiny non-comment block (type 0) that must be skipped
        out.write(byteArrayOf((0x80 or 4).toByte(), (block.size shr 16).toByte(), (block.size shr 8).toByte(), block.size.toByte()))
        out.write(block)
        return out.toByteArray()
    }

    @Test fun readsLyricsComment() {
        val data = flac(vorbis("TITLE=Song", "LYRICS=La la la"))
        assertEquals("La la la", FlacLyrics.parse(ByteArrayInputStream(data)))
    }

    @Test fun keyIsCaseInsensitive() {
        assertEquals("x", FlacLyrics.parse(ByteArrayInputStream(flac(vorbis("unsyncedlyrics=x")))))
    }

    @Test fun missingLyricsGivesNull() {
        assertNull(FlacLyrics.parse(ByteArrayInputStream(flac(vorbis("TITLE=Song")))))
    }

    @Test fun notFlacGivesNull() {
        assertNull(FlacLyrics.parse(ByteArrayInputStream("RIFFxxxx".toByteArray())))
    }

    @Test fun corruptLengthDoesNotCrash() {
        val bad = vorbis("LYRICS=abc")
        bad[bad.size - 8] = 0x7F
        FlacLyrics.parse(ByteArrayInputStream(flac(bad)))
    }
}
