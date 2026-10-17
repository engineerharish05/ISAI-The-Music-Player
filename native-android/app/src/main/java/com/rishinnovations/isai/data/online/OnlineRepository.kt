package com.rishinnovations.isai.data.online

import com.rishinnovations.isai.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Optional online catalogue search. Talks only to documented, permitted APIs:
 *  - Internet Archive (no key): audio items under Creative Commons / public domain licenses
 *  - Jamendo (needs a free client id supplied at build time): Creative Commons music
 * It never touches YouTube, Spotify or any other service that forbids third-party extraction.
 */
class OnlineRepository {
    val jamendoConfigured: Boolean get() = BuildConfig.JAMENDO_CLIENT_ID.isNotBlank()

    suspend fun search(query: String): OnlineResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext OnlineResult.Success(emptyList())
        try {
            val tracks = coroutineScope {
                val archive = async { runCatching { searchArchive(q) } }
                val jamendo = async { if (jamendoConfigured) runCatching { searchJamendo(q) } else Result.success(emptyList()) }
                val a = archive.await()
                val j = jamendo.await()
                if (a.isFailure && j.isFailure) throw (a.exceptionOrNull() ?: j.exceptionOrNull()!!)
                // Interleave so one source does not bury the other.
                val la = a.getOrDefault(emptyList()); val lj = j.getOrDefault(emptyList())
                (0 until maxOf(la.size, lj.size)).flatMap { i -> listOfNotNull(lj.getOrNull(i), la.getOrNull(i)) }
            }
            OnlineResult.Success(tracks)
        } catch (e: Exception) {
            OnlineResult.Failure("Couldn't reach the music catalogues. Check your connection and try again.")
        }
    }

    private fun searchJamendo(q: String): List<OnlineTrack> {
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=${BuildConfig.JAMENDO_CLIENT_ID}&format=json&limit=20&audioformat=mp32&include=musicinfo&imagesize=300&search=${enc(q)}"
        return OnlineParsers.jamendo(get(url))
    }

    private fun searchArchive(q: String): List<OnlineTrack> {
        // Only openly licensed audio: Creative Commons or the Archive's public "opensource_audio" collection.
        val query = "($q) AND mediatype:audio AND (licenseurl:(*creativecommons.org*) OR collection:(opensource_audio) OR collection:(etree))"
        val url = "https://archive.org/advancedsearch.php?q=${enc(query)}&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=licenseurl&rows=8&output=json&sort%5B%5D=downloads+desc"
        val items = OnlineParsers.archiveSearch(get(url))
        return items.flatMap { item ->
            runCatching { OnlineParsers.archiveMetadata(get("https://archive.org/metadata/${enc(item.id)}"), item, maxTracks = 4) }.getOrDefault(emptyList())
        }
    }

    /** Downloads [track] into [dir]; calls [onProgress] with 0..1. Returns the finished file. */
    suspend fun download(track: OnlineTrack, dir: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val safe = "${track.artist} - ${track.title}".replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80)
        val target = File(dir, "$safe.${track.fileExtension}")
        val partial = File(dir, "$safe.part")
        val conn = open(track.streamUrl, timeoutMs = 20_000)
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                partial.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (!partial.renameTo(target)) throw java.io.IOException("Could not save file")
            target
        } finally {
            conn.disconnect()
            partial.delete()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String, timeoutMs: Int = 10_000): HttpURLConnection {
        require(url.startsWith("https://")) { "Only HTTPS sources are allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs; readTimeout = timeoutMs
            setRequestProperty("User-Agent", "ISAI-Music-Player/0.3 (open source; https://github.com/engineerharish05/isai-the-music-player)")
            instanceFollowRedirects = true
        }
    }

    private fun get(url: String): String {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 4_000_000) throw java.io.IOException("Response too large")
                }
                out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }
}
