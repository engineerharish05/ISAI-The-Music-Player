package com.rishinnovations.isai.logic

import com.rishinnovations.isai.model.Library
import com.rishinnovations.isai.model.Playlist
import com.rishinnovations.isai.model.SongStat
import com.rishinnovations.isai.model.SortField
import com.rishinnovations.isai.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAndFormatTest {
    @Test fun normalizeStripsAccentsAndCase() = assertEquals("beyonce", "Beyoncé".normalizeForSearch())
    @Test fun normalizeKeepsTamil() = assertEquals("இசை", "இசை".normalizeForSearch())
    @Test fun durations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
        assertEquals("0:00", formatDuration(-5))
    }
    @Test fun listeningTime() {
        assertEquals("45s", formatListeningTime(45_000))
        assertEquals("12m", formatListeningTime(12 * 60_000L))
        assertEquals("2h 5m", formatListeningTime(125 * 60_000L))
    }
}

class SortingTest {
    private val songs = listOf(
        song(1, title = "banana", artist = "Z", durationMs = 100, added = 3),
        song(2, title = "Apple", artist = "Y", durationMs = 300, added = 1),
        song(3, title = "cherry", artist = "X", durationMs = 200, added = 2)
    )

    @Test fun titleIsCaseInsensitive() = assertEquals(listOf(2L, 1L, 3L), songs.sortedWith(SortField.TITLE, SortOrder.ASCENDING).map { it.id })
    @Test fun descending() = assertEquals(listOf(3L, 1L, 2L), songs.sortedWith(SortField.TITLE, SortOrder.DESCENDING).map { it.id })
    @Test fun artist() = assertEquals(listOf(3L, 2L, 1L), songs.sortedWith(SortField.ARTIST, SortOrder.ASCENDING).map { it.id })
    @Test fun duration() = assertEquals(listOf(1L, 3L, 2L), songs.sortedWith(SortField.DURATION, SortOrder.ASCENDING).map { it.id })
    @Test fun dateAdded() = assertEquals(listOf(2L, 3L, 1L), songs.sortedWith(SortField.DATE_ADDED, SortOrder.ASCENDING).map { it.id })
}

class LibraryModelTest {
    @Test fun groupsAlbumsArtistsAndGenres() {
        val lib = Library(listOf(
            song(1, artist = "A", album = "X", albumId = 10, genre = "Rock", track = 2),
            song(2, artist = "A", album = "X", albumId = 10, genre = "Rock", track = 1),
            song(3, artist = "B", album = "Y", albumId = 11, genre = null)
        ))
        assertEquals(2, lib.albums.size)
        assertEquals(listOf(2L, 1L), lib.album("ms:10")!!.songs.map { it.id })
        assertEquals(2, lib.artists.size)
        assertEquals(1, lib.genres.size)
        assertEquals(setOf(1L, 2L, 3L), lib.byId.keys)
    }

    @Test fun variousArtistsAlbum() {
        val lib = Library(listOf(song(1, artist = "A", albumId = 5), song(2, artist = "B", albumId = 5)))
        assertEquals("Various artists", lib.albums.single().artist)
    }
}

class SearchEngineTest {
    private val lib = Library(listOf(
        song(1, title = "Kannana Kanne", artist = "Sid Sriram", album = "Viswasam", albumId = 1, genre = "Tamil"),
        song(2, title = "Café del Mar", artist = "Energy 52", album = "Trance", albumId = 2),
        song(3, title = "Cafe", artist = "Someone", album = "Other", albumId = 3)
    ))
    private val playlists = listOf(Playlist(1, "Road trip", 0, listOf(1)))

    @Test fun blankQueryIsEmpty() = assertTrue(SearchEngine.search("  ", lib, playlists).isEmpty)
    @Test fun matchesAcrossFields() = assertEquals(listOf(1L), SearchEngine.search("sid viswasam", lib, playlists).songs.map { it.id })
    @Test fun ignoresAccents() = assertEquals(2, SearchEngine.search("cafe", lib, playlists).songs.size)
    @Test fun exactTitleRanksFirst() = assertEquals(3L, SearchEngine.search("cafe", lib, playlists).songs.first().id)
    @Test fun findsAlbumsArtistsGenresPlaylists() {
        assertEquals(1, SearchEngine.search("viswasam", lib, playlists).albums.size)
        assertEquals(1, SearchEngine.search("sriram", lib, playlists).artists.size)
        assertEquals(1, SearchEngine.search("tamil", lib, playlists).genres.size)
        assertEquals(1, SearchEngine.search("road", lib, playlists).playlists.size)
    }
    @Test fun noMatch() = assertTrue(SearchEngine.search("zzzz", lib, playlists).isEmpty)
    @Test fun allWordsMustMatch() = assertFalse(SearchEngine.search("sid zzzz", lib, playlists).songs.isNotEmpty())
}

class RecommendationEngineTest {
    private val day = 86_400_000L
    private val now = 100 * day

    @Test fun emptyLibraryGivesNothing() =
        assertTrue(RecommendationEngine.recommend(Library.EMPTY, emptySet(), emptyMap(), emptyList(), now).isEmpty())

    @Test fun noHistoryGivesNoSections() {
        val lib = Library(listOf(song(1), song(2)))
        assertTrue(RecommendationEngine.recommend(lib, emptySet(), emptyMap(), emptyList(), now).isEmpty())
    }

    @Test fun familiarUsesPlaysAndFavorites() {
        val lib = Library(listOf(song(1, artist = "A"), song(2, artist = "B"), song(3, artist = "C")))
        val stats = mapOf(1L to SongStat(1, 5, 0, now))
        val sections = RecommendationEngine.recommend(lib, setOf(2L), stats, emptyList(), now)
        val familiar = sections.first { it.id == "familiar" }
        assertEquals(setOf(1L, 2L), familiar.songs.map { it.id }.toSet())
        assertTrue(familiar.reason.isNotBlank())
    }

    @Test fun revisitWindow() {
        val lib = Library(listOf(song(1), song(2), song(3)))
        val stats = mapOf(
            1L to SongStat(1, 1, 0, now - 10 * day),  // in window
            2L to SongStat(2, 1, 0, now - 1 * day),   // too recent
            3L to SongStat(3, 1, 0, now - 60 * day)   // too old
        )
        val revisit = RecommendationEngine.recommend(lib, emptySet(), stats, emptyList(), now).first { it.id == "revisit" }
        assertEquals(listOf(1L), revisit.songs.map { it.id })
    }

    @Test fun moreFromTopArtist() {
        val lib = Library(listOf(song(1, artist = "A"), song(2, artist = "A"), song(3, artist = "B")))
        val stats = mapOf(1L to SongStat(1, 9, 0, now))
        val section = RecommendationEngine.recommend(lib, emptySet(), stats, emptyList(), now).first { it.id == "artist" }
        assertEquals(setOf(1L, 2L), section.songs.map { it.id }.toSet())
    }

    @Test fun deterministicForSameSeed() {
        val lib = Library((1L..40L).map { song(it) })
        val stats = (1L..40L).associateWith { SongStat(it, 3, 0, now) }
        val a = RecommendationEngine.recommend(lib, emptySet(), stats, emptyList(), now, seed = 7)
        val b = RecommendationEngine.recommend(lib, emptySet(), stats, emptyList(), now, seed = 7)
        assertEquals(a, b)
    }
}
