package com.rishinnovations.isai.data.library

import android.content.Context
import android.net.Uri
import com.rishinnovations.isai.logic.FlacLyrics
import com.rishinnovations.isai.logic.Id3Lyrics
import com.rishinnovations.isai.logic.LrcParser
import com.rishinnovations.isai.logic.Lyrics
import com.rishinnovations.isai.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File

/**
 * Finds lyrics for a song from sources that need no network: a .lrc/.txt file the user attached
 * to the song, or lyrics embedded in the audio file (MP3 ID3 USLT/SYLT and FLAC Vorbis comments).
 */
class LyricsRepository(private val context: Context) {
    private fun dir() = File(context.filesDir, "lyrics").apply { mkdirs() }
    private fun fileFor(song: Song) = File(dir(), "${song.id}.lrc")

    fun hasAttached(song: Song): Boolean = fileFor(song).exists()

    suspend fun load(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val attached = fileFor(song)
        if (attached.exists()) {
            LrcParser.parse(attached.readText(), "Lyrics file you added")?.let { return@withContext it }
        }
        runCatching { readEmbedded(song) }.getOrNull()
    }

    /** Copies a user-selected .lrc/.txt file into app storage for [song]. Returns false if it holds no lyrics. */
    suspend fun attach(song: Song, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(source)?.use { readLimited(it, MAX_LYRICS_BYTES).toString(Charsets.UTF_8) }
        }.getOrNull() ?: return@withContext false
        if (LrcParser.parse(text, "") == null) return@withContext false
        fileFor(song).writeText(text)
        true
    }

    fun removeAttached(song: Song) {
        fileFor(song).delete()
    }

    private fun readLimited(input: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < max) {
            val n = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun readEmbedded(song: Song): Lyrics? {
        val stream = context.contentResolver.openInputStream(song.uri) ?: return null
        BufferedInputStream(stream, 16 * 1024).use { input ->
            input.mark(16)
            val magic = ByteArray(10)
            val read = input.read(magic)
            input.reset()
            if (read < 4) return null
            return when {
                Id3Lyrics.tagSize(magic) > 0 -> {
                    val total = Id3Lyrics.tagSize(magic)
                    if (total > MAX_TAG_BYTES) return null
                    val tag = ByteArray(total)
                    var off = 0
                    while (off < total) {
                        val n = input.read(tag, off, total - off)
                        if (n < 0) break
                        off += n
                    }
                    val result = Id3Lyrics.parse(tag) ?: return null
                    result.synced?.takeIf { it.isNotEmpty() }?.let { Lyrics(it, true, "Embedded in file (ID3 SYLT)") }
                        ?: result.unsynced?.let { LrcParser.parse(it, "Embedded in file (ID3 USLT)") }
                }
                String(magic, 0, 4, Charsets.ISO_8859_1) == "fLaC" ->
                    FlacLyrics.parse(input)?.let { LrcParser.parse(it, "Embedded in file (FLAC)") }
                else -> null
            }
        }
    }

    companion object {
        private const val MAX_LYRICS_BYTES = 1024 * 1024
        private const val MAX_TAG_BYTES = 6 * 1024 * 1024
    }
}

/** Convenience for the UI: lines, with the index of the active one. */
fun Lyrics.activeIndex(positionMs: Long): Int = if (synced) LrcParser.currentIndex(lines, positionMs) else -1
