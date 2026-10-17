package com.rishinnovations.isai.logic

import java.io.InputStream
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

object LrcParser {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("""^\[[a-zA-Z#]+:.*]\s*$""")
    private val wordTime = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    /** Parses LRC text. Falls back to unsynchronised lines when the text has no timestamps. */
    fun parse(raw: String, source: String): Lyrics? {
        val text = raw.removePrefix("﻿")
        var offset = 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val offsetMatch = offsetTag.find(line)
            if (offsetMatch != null) { offset = offsetMatch.groupValues[1].toLong(); continue }
            val stamps = timestamp.findAll(line).toList()
            if (stamps.isEmpty()) {
                if (!metaTag.matches(line)) plain += LyricLine(null, line)
                continue
            }
            val content = wordTime.replace(timestamp.replace(line, ""), "").trim()
            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toLong()
                val seconds = stamp.groupValues[2].toLong()
                val fraction = stamp.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                timed += LyricLine((minutes * 60 + seconds) * 1000 + millis + offset, content)
            }
        }
        return when {
            timed.isNotEmpty() -> Lyrics(timed.sortedBy { it.timeMs }, true, source)
            plain.isNotEmpty() -> Lyrics(plain, false, source)
            else -> null
        }
    }

    /** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val time = lines[mid].timeMs ?: return -1
            if (time <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

/** Reads unsynchronised (USLT) and synchronised (SYLT) lyrics from an ID3v2 tag. */
object Id3Lyrics {
    class Result(val unsynced: String?, val synced: List<LyricLine>?)

    /** Total tag size (header + body) if [header] starts with a valid ID3v2 header, else -1. */
    fun tagSize(header: ByteArray): Int {
        if (header.size < 10 || header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return -1
        return 10 + syncSafe(header, 6)
    }

    fun parse(tag: ByteArray): Result? {
        if (tagSize(tag) < 0) return null
        val major = tag[3].toInt()
        if (major !in 2..4) return null
        val flags = tag[5].toInt()
        var pos = 10
        val end = minOf(tag.size, 10 + syncSafe(tag, 6))
        if (major >= 3 && flags and 0x40 != 0 && pos + 4 <= end) {
            pos += if (major == 4) syncSafe(tag, pos) else 4 + readInt(tag, pos, 4)
        }
        val headerLen = if (major == 2) 6 else 10
        var unsynced: String? = null
        var synced: List<LyricLine>? = null

        while (pos + headerLen <= end) {
            val id = String(tag, pos, if (major == 2) 3 else 4, Charsets.ISO_8859_1)
            if (id[0].code == 0) break
            val size = when (major) {
                2 -> readInt(tag, pos + 3, 3)
                4 -> syncSafe(tag, pos + 4)
                else -> readInt(tag, pos + 4, 4)
            }
            val bodyStart = pos + headerLen
            val bodyEnd = bodyStart + size
            if (size <= 0 || bodyEnd > end) break
            val body = tag.copyOfRange(bodyStart, bodyEnd)
            when (id) {
                "USLT", "ULT" -> if (unsynced == null) unsynced = parseUslt(body)
                "SYLT", "SLT" -> if (synced == null) synced = parseSylt(body)
            }
            pos = bodyEnd
        }
        return if (unsynced == null && synced == null) null else Result(unsynced, synced)
    }

    private fun parseUslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val encoding = body[0].toInt()
        val descEnd = terminatorEnd(body, 4, encoding)
        if (descEnd > body.size) return null
        return decode(body, descEnd, body.size, encoding).trim().takeIf { it.isNotEmpty() }
    }

    private fun parseSylt(body: ByteArray): List<LyricLine>? {
        if (body.size < 7) return null
        val encoding = body[0].toInt()
        val timeFormat = body[4].toInt() // 1 = MPEG frames, 2 = milliseconds
        if (timeFormat != 2) return null
        var pos = terminatorEnd(body, 6, encoding)
        val lines = ArrayList<LyricLine>()
        while (pos < body.size) {
            val textEnd = terminatorStart(body, pos, encoding)
            if (textEnd < 0) break
            val afterText = textEnd + terminatorLength(encoding)
            if (afterText + 4 > body.size) break
            lines += LyricLine(readInt(body, afterText, 4).toLong().and(0xFFFFFFFFL), decode(body, pos, textEnd, encoding).trim('\n', '\r'))
            pos = afterText + 4
        }
        return lines.filter { it.text.isNotBlank() }.takeIf { it.isNotEmpty() }
    }

    private fun terminatorLength(encoding: Int) = if (encoding == 1 || encoding == 2) 2 else 1

    private fun terminatorStart(data: ByteArray, from: Int, encoding: Int): Int {
        var i = from
        if (terminatorLength(encoding) == 1) {
            while (i < data.size) { if (data[i].toInt() == 0) return i; i++ }
        } else {
            while (i + 1 < data.size) { if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i; i += 2 }
        }
        return -1
    }

    private fun terminatorEnd(data: ByteArray, from: Int, encoding: Int): Int {
        val start = terminatorStart(data, from, encoding)
        return if (start < 0) data.size + 1 else start + terminatorLength(encoding)
    }

    private fun decode(data: ByteArray, from: Int, to: Int, encoding: Int): String {
        if (to <= from) return ""
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16 // honours the byte-order mark
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(data, from, to - from, charset)
    }

    private fun syncSafe(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0x7F) shl 21) or ((data[at + 1].toInt() and 0x7F) shl 14) or
            ((data[at + 2].toInt() and 0x7F) shl 7) or (data[at + 3].toInt() and 0x7F)

    private fun readInt(data: ByteArray, at: Int, bytes: Int): Int {
        var value = 0
        for (i in 0 until bytes) value = (value shl 8) or (data[at + i].toInt() and 0xFF)
        return value
    }
}

/** Reads lyrics stored in a FLAC file's Vorbis comment block. */
object FlacLyrics {
    fun parse(stream: InputStream): String? {
        val magic = ByteArray(4)
        if (stream.readFully(magic) != 4 || String(magic, Charsets.ISO_8859_1) != "fLaC") return null
        while (true) {
            val header = ByteArray(4)
            if (stream.readFully(header) != 4) return null
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            if (type == 4) {
                if (length > 4 * 1024 * 1024) return null
                val block = ByteArray(length)
                if (stream.readFully(block) != length) return null
                return parseVorbisComments(block)
            }
            stream.skipFully(length.toLong())
            if (last) return null
        }
    }

    internal fun parseVorbisComments(block: ByteArray): String? {
        var pos = 0
        fun int(): Int {
            if (pos + 4 > block.size) throw IndexOutOfBoundsException()
            val v = (block[pos].toInt() and 0xFF) or ((block[pos + 1].toInt() and 0xFF) shl 8) or
                ((block[pos + 2].toInt() and 0xFF) shl 16) or ((block[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }
        return try {
            pos += int() // vendor string
            val count = int()
            var found: String? = null
            repeat(count) {
                val len = int()
                if (len < 0 || pos + len > block.size) throw IndexOutOfBoundsException()
                val entry = String(block, pos, len, Charsets.UTF_8)
                pos += len
                val eq = entry.indexOf('=')
                if (eq > 0 && found == null) {
                    val key = entry.substring(0, eq).uppercase().replace(" ", "")
                    if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS") found = entry.substring(eq + 1)
                }
            }
            found?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) return
                remaining--
            } else remaining -= skipped
        }
    }
}
