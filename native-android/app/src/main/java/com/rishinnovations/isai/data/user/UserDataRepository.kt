package com.rishinnovations.isai.data.user

import com.rishinnovations.isai.data.db.FavoriteEntity
import com.rishinnovations.isai.data.db.IsaiDatabase
import com.rishinnovations.isai.data.db.PlayEventEntity
import com.rishinnovations.isai.data.db.PlaylistEntity
import com.rishinnovations.isai.model.Playlist
import com.rishinnovations.isai.model.SongStat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Favorites, playlists and listening history. Everything is stored locally in Room. */
class UserDataRepository(private val db: IsaiDatabase, private val scope: CoroutineScope) {

    /** Favorite song ids, most recently added first. */
    val favorites: StateFlow<List<Long>> = db.favorites().observeAll()
        .map { list -> list.map { it.songId } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val favoriteSet: StateFlow<Set<Long>> = favorites.map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val playlists: StateFlow<List<Playlist>> = combine(db.playlists().observePlaylists(), db.playlists().observeLinks()) { playlists, links ->
        val grouped = links.groupBy { it.playlistId }
        playlists.map { p -> Playlist(p.id, p.name, p.createdAt, grouped[p.id].orEmpty().map { it.songId }) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<Map<Long, SongStat>> = db.playEvents().observeAggregates()
        .map { rows -> rows.associate { it.songId to SongStat(it.songId, it.plays, it.listenedMs, it.lastPlayedAt) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val totalListenedMs: StateFlow<Long> = db.playEvents().observeTotalListenedMs()
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    fun eventsSince(sinceMs: Long): Flow<List<PlayEventEntity>> = db.playEvents().observeSince(sinceMs)

    fun toggleFavorite(songId: Long) {
        scope.launch {
            if (songId in favoriteSet.value) db.favorites().remove(songId)
            else db.favorites().add(FavoriteEntity(songId, System.currentTimeMillis()))
        }
    }

    fun createPlaylist(name: String, initialSongs: List<Long> = emptyList(), onCreated: (Long) -> Unit = {}) {
        val clean = name.trim().ifEmpty { return }
        scope.launch {
            val id = db.playlists().insertPlaylist(PlaylistEntity(name = clean, createdAt = System.currentTimeMillis()))
            if (initialSongs.isNotEmpty()) db.playlists().addSongs(id, initialSongs)
            onCreated(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        val clean = name.trim().ifEmpty { return }
        scope.launch { db.playlists().rename(id, clean) }
    }

    fun deletePlaylist(id: Long) {
        scope.launch { db.playlists().delete(id) }
    }

    fun addToPlaylist(id: Long, songIds: List<Long>) {
        scope.launch { db.playlists().addSongs(id, songIds) }
    }

    fun removeFromPlaylist(id: Long, songIds: List<Long>) {
        scope.launch { db.playlists().removeAllMatching(id, songIds) }
    }

    fun movePlaylistSong(id: Long, from: Int, to: Int) {
        scope.launch { db.playlists().move(id, from, to) }
    }

    // ---- Listening history (written by the playback service) ----

    suspend fun insertPlayEvent(songId: Long, startedAt: Long, listenedMs: Long, counted: Boolean): Long =
        db.playEvents().insert(PlayEventEntity(songId = songId, startedAt = startedAt, listenedMs = listenedMs, counted = counted))

    suspend fun updatePlayEvent(id: Long, listenedMs: Long, counted: Boolean) {
        db.playEvents().update(id, listenedMs, counted)
    }

    fun clearHistory() {
        scope.launch { db.playEvents().clear() }
    }
}
