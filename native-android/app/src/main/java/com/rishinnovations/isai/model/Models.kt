package com.rishinnovations.isai.model

import android.net.Uri

/**
 * A playable track. [id] is the MediaStore id for songs found on the device, or a negative number
 * (the negated row id) for songs the user imported through the system file picker.
 *
 * The URI is kept as a string so the model stays a plain Kotlin value that is trivial to unit test.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val genre: String?,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAddedSec: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val uriString: String,
    val isImported: Boolean = false
) {
    val uri: Uri get() = Uri.parse(uriString)
    val albumKey: String get() = if (albumId > 0L && !isImported) "ms:$albumId" else "t:${album.lowercase()}|${artist.lowercase()}"
}

data class Album(val key: String, val title: String, val artist: String, val songs: List<Song>) {
    val representative: Song get() = songs.first()
    val year: Int get() = songs.maxOf { it.year }
}

data class Artist(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.map { it.albumKey }.distinct().size
}

data class Genre(val name: String, val songs: List<Song>)

data class Playlist(val id: Long, val name: String, val createdAt: Long, val songIds: List<Long>)

enum class SortField(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DURATION("Duration"), DATE_ADDED("Date added")
}

enum class SortOrder { ASCENDING, DESCENDING }

enum class RepeatMode { OFF, ALL, ONE }

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

/** Aggregated listening numbers for a single song, computed from real listening events. */
data class SongStat(val songId: Long, val plays: Int, val listenedMs: Long, val lastPlayedAt: Long)

/** Immutable snapshot of everything discovered on the device plus imported files. */
class Library(val songs: List<Song>) {
    val byId: Map<Long, Song> = songs.associateBy { it.id }

    val albums: List<Album> = songs.groupBy { it.albumKey }.map { (key, list) ->
        val sorted = list.sortedWith(compareBy({ it.trackNumber == 0 }, { it.trackNumber }, { it.title.lowercase() }))
        val artists = sorted.map { it.artist }.distinct()
        Album(key, sorted.first().album, if (artists.size == 1) artists.first() else "Various artists", sorted)
    }.sortedBy { it.title.lowercase() }

    val artists: List<Artist> = songs.groupBy { it.artist }.map { (name, list) ->
        Artist(name, list.sortedBy { it.title.lowercase() })
    }.sortedBy { it.name.lowercase() }

    val genres: List<Genre> = songs.filter { !it.genre.isNullOrBlank() }.groupBy { it.genre!! }.map { (name, list) ->
        Genre(name, list.sortedBy { it.title.lowercase() })
    }.sortedBy { it.name.lowercase() }

    val recentlyAdded: List<Song> get() = songs.sortedByDescending { it.dateAddedSec }

    fun album(key: String): Album? = albums.firstOrNull { it.key == key }
    fun artist(name: String): Artist? = artists.firstOrNull { it.name == name }
    fun genre(name: String): Genre? = genres.firstOrNull { it.name == name }

    companion object {
        val EMPTY = Library(emptyList())
    }
}
