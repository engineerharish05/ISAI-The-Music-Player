@file:OptIn(UnstableApi::class)

package com.rishinnovations.isai.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.rishinnovations.isai.AppContainer
import com.rishinnovations.isai.data.library.ArtworkProvider
import com.rishinnovations.isai.model.Song

/**
 * The media browse tree exposed to external controllers such as Android Auto and system media UIs.
 * It reads the same library, favorites and playlists the app uses, and playing from it fills the
 * same player queue as the app does.
 */
class BrowseTree(private val container: AppContainer) {

    fun root(): MediaItem = folder(ROOT, "ISAI", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, null)

    fun item(id: String): MediaItem? = when {
        id == ROOT -> root()
        id in TOP_LEVEL -> topLevel().firstOrNull { it.mediaId == id }
        id.contains('|') -> {
            val parent = id.substringBefore('|')
            val songId = id.substringAfter('|').toLongOrNull()
            songsOf(parent).firstOrNull { it.id == songId }?.let { browseSong(parent, it) }
        }
        else -> children(ROOT).firstOrNull { it.mediaId == id }
            ?: container.library.snapshot().albums.firstOrNull { "album:${it.key}" == id }?.let { albumFolder(it.key, it.title, it.artist, it.representative) }
    }

    fun children(parentId: String): List<MediaItem> {
        val library = container.library.snapshot()
        return when {
            parentId == ROOT -> topLevel()
            parentId == ALBUMS -> library.albums.map { albumFolder(it.key, it.title, it.artist, it.representative) }
            parentId == ARTISTS -> library.artists.map {
                folder("artist:${it.name}", it.name, MediaMetadata.MEDIA_TYPE_ARTIST, it.songs.firstOrNull())
            }
            parentId == PLAYLISTS -> container.userData.playlists.value.map {
                folder("playlist:${it.id}", it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST, null)
            }
            else -> songsOf(parentId).map { browseSong(parentId, it) }
        }
    }

    fun songsOf(parentId: String): List<Song> {
        val library = container.library.snapshot()
        return when {
            parentId == SONGS -> library.songs.sortedBy { it.title.lowercase() }.take(MAX_ITEMS)
            parentId == FAVORITES -> container.userData.favorites.value.mapNotNull { library.byId[it] }
            parentId == RECENT -> library.recentlyAdded.take(100)
            parentId == MOST_PLAYED -> {
                val stats = container.userData.stats.value
                library.songs.filter { (stats[it.id]?.plays ?: 0) > 0 }.sortedByDescending { stats[it.id]?.plays ?: 0 }.take(100)
            }
            parentId.startsWith("album:") -> library.album(parentId.removePrefix("album:"))?.songs.orEmpty()
            parentId.startsWith("artist:") -> library.artist(parentId.removePrefix("artist:"))?.songs.orEmpty()
            parentId.startsWith("playlist:") -> container.userData.playlists.value
                .firstOrNull { it.id == parentId.removePrefix("playlist:").toLongOrNull() }
                ?.songIds?.mapNotNull { library.byId[it] }.orEmpty()
            else -> emptyList()
        }
    }

    /** For a browse item id ("parent|songId") returns the whole parent list and the index of the song. */
    fun queueFor(mediaId: String): Pair<List<Song>, Int>? {
        if (!mediaId.contains('|')) return null
        val parent = mediaId.substringBefore('|')
        val songId = mediaId.substringAfter('|').toLongOrNull() ?: return null
        val songs = songsOf(parent)
        val index = songs.indexOfFirst { it.id == songId }
        return if (index >= 0) songs to index else null
    }

    private fun topLevel(): List<MediaItem> = listOf(
        folder(SONGS, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, null),
        folder(ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, null),
        folder(ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, null),
        folder(FAVORITES, "Favorites", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, null),
        folder(PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, null),
        folder(MOST_PLAYED, "Most played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, null),
        folder(RECENT, "Recently added", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, null)
    )

    private fun albumFolder(key: String, title: String, artist: String, art: Song?): MediaItem =
        MediaItem.Builder()
            .setMediaId("album:$key")
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(artist).setIsBrowsable(true).setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .apply { art?.let { setArtworkUri(ArtworkProvider.uriFor(it.id)) } }
                    .build()
            ).build()

    private fun folder(id: String, title: String, type: Int, art: Song?): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(type)
                    .apply { art?.let { setArtworkUri(ArtworkProvider.uriFor(it.id)) } }
                    .build()
            ).build()

    private fun browseSong(parent: String, song: Song): MediaItem =
        MediaItems.fromSong(song).buildUpon().setMediaId("$parent|${song.id}").build()

    companion object {
        const val ROOT = "root"
        const val SONGS = "songs"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val FAVORITES = "favorites"
        const val PLAYLISTS = "playlists"
        const val MOST_PLAYED = "most_played"
        const val RECENT = "recent"
        private val TOP_LEVEL = setOf(SONGS, ALBUMS, ARTISTS, FAVORITES, PLAYLISTS, MOST_PLAYED, RECENT)
        private const val MAX_ITEMS = 500
    }
}
