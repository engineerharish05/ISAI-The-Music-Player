package com.rishinnovations.isai.data.online

import org.json.JSONArray
import org.json.JSONObject

/** Pure JSON → model parsing, kept separate from networking so it can be unit tested. */
object OnlineParsers {
    private val audioFormats = listOf("VBR MP3", "MP3", "128Kbps MP3", "64Kbps MP3", "Ogg Vorbis")

    /** Internet Archive advancedsearch response → candidate items (identifier, title, creator). */
    fun archiveSearch(json: String): List<ArchiveItem> {
        val docs = JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        return docs.objects().mapNotNull { doc ->
            val id = doc.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ArchiveItem(id, doc.optString("title", id).ifBlank { id }, doc.optStringOrArray("creator") ?: "Unknown artist", doc.optString("licenseurl").takeIf { it.isNotBlank() })
        }
    }

    data class ArchiveItem(val id: String, val title: String, val creator: String, val licenseUrl: String?)

    /** Internet Archive item metadata → playable tracks (one per audio file, best format only). */
    fun archiveMetadata(json: String, item: ArchiveItem, maxTracks: Int = 30): List<OnlineTrack> {
        val root = JSONObject(json)
        val files = root.optJSONArray("files") ?: return emptyList()
        val all = files.objects().filter { it.optString("format") in audioFormats }
        // Prefer original/derivative MP3s; fall back to Ogg when no MP3 exists.
        val mp3 = all.filter { it.optString("format").contains("MP3") }
        val chosen = mp3.ifEmpty { all.filter { it.optString("format") == "Ogg Vorbis" } }
        val license = item.licenseUrl?.let { licenseName(it) } ?: "See item page"
        return chosen.take(maxTracks).mapNotNull { f ->
            val name = f.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            OnlineTrack(
                source = "archive",
                sourceId = "${item.id}/$name",
                title = f.optString("title").ifBlank { name.substringBeforeLast('.') },
                artist = f.optString("creator").ifBlank { item.creator },
                album = item.title,
                durationMs = parseLength(f.optString("length")),
                streamUrl = "https://archive.org/download/${item.id}/" + name.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
                artworkUrl = "https://archive.org/services/img/${item.id}",
                license = license,
                pageUrl = "https://archive.org/details/${item.id}"
            )
        }
    }

    fun jamendo(json: String): List<OnlineTrack> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return results.objects().mapNotNull { t ->
            val id = t.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val stream = t.optString("audio").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            OnlineTrack(
                source = "jamendo", sourceId = id,
                title = t.optString("name", "Untitled"),
                artist = t.optString("artist_name", "Unknown artist"),
                album = t.optString("album_name"),
                durationMs = t.optLong("duration") * 1000L,
                streamUrl = stream,
                artworkUrl = t.optString("image").takeIf { it.startsWith("https://") },
                license = t.optString("license_ccurl").takeIf { it.isNotBlank() }?.let(::licenseName) ?: "Creative Commons",
                pageUrl = t.optString("shareurl").ifBlank { "https://www.jamendo.com/track/$id" }
            )
        }
    }

    /** "369.5" or "4:05" → milliseconds. */
    fun parseLength(raw: String): Long {
        if (raw.isBlank()) return 0L
        raw.toDoubleOrNull()?.let { return (it * 1000).toLong() }
        val parts = raw.split(':').mapNotNull { it.toLongOrNull() }
        return if (parts.size in 2..3) parts.fold(0L) { acc, v -> acc * 60 + v } * 1000 else 0L
    }

    fun licenseName(url: String): String {
        val m = Regex("""creativecommons\.org/(licenses|publicdomain)/([a-z\-]+)/?([0-9.]*)""").find(url) ?: return url
        return if (m.groupValues[1] == "publicdomain") "Public domain (${m.groupValues[2].uppercase()})"
        else "CC ${m.groupValues[2].uppercase()}" + m.groupValues[3].let { if (it.isNotEmpty()) " $it" else "" }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONObject.optStringOrArray(key: String): String? {
        val v = opt(key) ?: return null
        return when (v) {
            is JSONArray -> (0 until v.length()).mapNotNull { v.optString(it).takeIf { s -> s.isNotBlank() } }.joinToString(", ").ifBlank { null }
            else -> v.toString().takeIf { it.isNotBlank() }
        }
    }
}
