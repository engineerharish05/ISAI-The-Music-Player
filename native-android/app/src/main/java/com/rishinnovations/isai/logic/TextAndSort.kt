package com.rishinnovations.isai.logic

import com.rishinnovations.isai.model.Album
import com.rishinnovations.isai.model.Artist
import com.rishinnovations.isai.model.Genre
import com.rishinnovations.isai.model.Library
import com.rishinnovations.isai.model.Playlist
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.model.SortField
import com.rishinnovations.isai.model.SortOrder
import java.text.Normalizer

private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

/** Lower-cases and strips Latin diacritics so "Beyoncé" matches "beyonce". Indic scripts are untouched. */
fun String.normalizeForSearch(): String =
    diacritics.replace(Normalizer.normalize(lowercase(), Normalizer.Form.NFD), "")

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun formatListeningTime(ms: Long): String {
    val minutes = ms / 60_000L
    return when {
        minutes < 1 -> "${ms / 1000L}s"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

fun List<Song>.sortedWith(field: SortField, order: SortOrder): List<Song> {
    val comparator: Comparator<Song> = when (field) {
        SortField.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        SortField.ARTIST -> compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        SortField.ALBUM -> compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy { it.trackNumber }
        SortField.DURATION -> compareBy { it.durationMs }
        SortField.DATE_ADDED -> compareBy { it.dateAddedSec }
    }
    val sorted = sortedWith(comparator)
    return if (order == SortOrder.DESCENDING) sorted.reversed() else sorted
}

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val playlists: List<Playlist> = emptyList()
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && genres.isEmpty() && playlists.isEmpty()
}

/** Local-library search. Every query word must appear somewhere in the item's searchable text. */
object SearchEngine {
    fun search(query: String, library: Library, playlists: List<Playlist>, maxSongs: Int = 100): SearchResults {
        val tokens = query.normalizeForSearch().split(' ', '\t').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return SearchResults()

        fun matches(text: String) = tokens.all { text.contains(it) }

        val songs = library.songs.mapNotNull { song ->
            val title = song.title.normalizeForSearch()
            val haystack = "$title ${song.artist.normalizeForSearch()} ${song.album.normalizeForSearch()} ${song.genre.orEmpty().normalizeForSearch()}"
            if (!matches(haystack)) null else {
                val score = when {
                    title == tokens.joinToString(" ") -> 0
                    title.startsWith(tokens.first()) -> 1
                    matches(title) -> 2
                    else -> 3
                }
                score to song
            }
        }.sortedWith(compareBy({ it.first }, { it.second.title.lowercase() })).map { it.second }.take(maxSongs)

        return SearchResults(
            songs = songs,
            albums = library.albums.filter { matches("${it.title.normalizeForSearch()} ${it.artist.normalizeForSearch()}") }.take(20),
            artists = library.artists.filter { matches(it.name.normalizeForSearch()) }.take(20),
            genres = library.genres.filter { matches(it.name.normalizeForSearch()) }.take(10),
            playlists = playlists.filter { matches(it.name.normalizeForSearch()) }
        )
    }
}
