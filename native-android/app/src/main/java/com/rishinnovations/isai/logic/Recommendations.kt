package com.rishinnovations.isai.logic

import com.rishinnovations.isai.model.Library
import com.rishinnovations.isai.model.Playlist
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.model.SongStat
import java.util.Random

data class RecommendationSection(val id: String, val title: String, val reason: String, val songs: List<Song>)

/**
 * Offline, rule-based recommendations. There is no model and no network: every section states the
 * rule that produced it so the result is explainable. Sections are only returned when the user's
 * own data supports them.
 */
object RecommendationEngine {
    private const val DAY_MS = 86_400_000L

    fun recommend(
        library: Library,
        favorites: Set<Long>,
        stats: Map<Long, SongStat>,
        playlists: List<Playlist>,
        nowMs: Long,
        seed: Long = nowMs / DAY_MS
    ): List<RecommendationSection> {
        if (library.songs.isEmpty()) return emptyList()
        val rng = Random(seed)
        val sections = ArrayList<RecommendationSection>()

        fun weight(song: Song) = (stats[song.id]?.plays ?: 0) + if (song.id in favorites) 3 else 0

        // 1. Familiar: the songs the user keeps coming back to.
        val familiar = library.songs.filter { weight(it) >= 2 }.sortedByDescending { weight(it) }.take(30).shuffled(rng).take(20)
        if (familiar.isNotEmpty()) {
            sections += RecommendationSection("familiar", "Play something familiar", "Songs you have played often or marked as favorites.", familiar)
        }

        // 2. More from the artist the user leans on most.
        val artistScore = HashMap<String, Int>()
        library.songs.forEach { artistScore.merge(it.artist, weight(it), Int::plus) }
        val topArtist = artistScore.filter { it.value > 0 && !it.key.startsWith("Unknown", true) }.maxByOrNull { it.value }
        if (topArtist != null) {
            val more = library.songs.filter { it.artist == topArtist.key }
                .sortedWith(compareBy({ stats[it.id]?.plays ?: 0 }, { it.title.lowercase() })).take(20)
            if (more.size > 1) {
                sections += RecommendationSection("artist", "More from ${topArtist.key}", "You listen to ${topArtist.key} more than any other artist. Least-played tracks come first.", more)
            }
        }

        // 3. Revisit: heard within the last month, but not in the last two days.
        val revisit = library.songs.filter {
            val last = stats[it.id]?.lastPlayedAt ?: return@filter false
            nowMs - last in (2 * DAY_MS)..(30 * DAY_MS)
        }.sortedByDescending { stats[it.id]?.plays ?: 0 }.take(20)
        if (revisit.isNotEmpty()) {
            sections += RecommendationSection("revisit", "Revisit recently enjoyed music", "Played in the last 30 days, but not in the last two.", revisit)
        }

        // 4. Similar: songs that share an artist, album, genre or playlist with the user's anchors.
        val anchors = library.songs.filter { weight(it) > 0 }.sortedByDescending { weight(it) }.take(5)
        if (anchors.isNotEmpty()) {
            val anchorIds = anchors.map { it.id }.toSet()
            val playlistPeers = HashSet<Long>()
            playlists.filter { p -> p.songIds.any { it in anchorIds } }.forEach { playlistPeers += it.songIds }
            val scored = library.songs.filter { it.id !in anchorIds }.mapNotNull { song ->
                var score = 0
                for (anchor in anchors) {
                    if (song.artist == anchor.artist) score += 3
                    if (song.albumKey == anchor.albumKey) score += 2
                    if (!song.genre.isNullOrBlank() && song.genre == anchor.genre) score += 1
                }
                if (song.id in playlistPeers) score += 2
                if (score >= 2) song to score else null
            }.sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() }).take(20).map { it.first }
            if (scored.isNotEmpty()) {
                sections += RecommendationSection("similar", "Similar music in your library", "Shares an artist, album, genre or playlist with your most played songs.", scored)
            }
        }
        return sections
    }
}
