package com.rishinnovations.isai.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rishinnovations.isai.container
import com.rishinnovations.isai.data.library.ArtworkCache
import com.rishinnovations.isai.data.settings.AppSettings
import com.rishinnovations.isai.logic.RecommendationEngine
import com.rishinnovations.isai.logic.RecommendationSection
import com.rishinnovations.isai.model.Library
import com.rishinnovations.isai.model.Playlist
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.model.SongStat
import com.rishinnovations.isai.model.SortField
import com.rishinnovations.isai.model.SortOrder
import com.rishinnovations.isai.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Library, favorites, playlists, settings and statistics for the UI. Playback lives in [PlayerViewModel]. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val container = app.container

    val library: StateFlow<Library> = container.library.library
    val libraryLoaded = container.library.loaded
    val permissionGranted = container.library.permissionGranted
    val scanning = container.library.scanning
    val importBusy = container.imports.busy
    val importedFolders = container.db.imports().observeFolders()

    // The theme is read synchronously once so a light-theme user never sees a dark flash at launch.
    val settings: StateFlow<AppSettings> = container.settings.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        runBlocking(Dispatchers.IO) { container.settings.current() }
    )

    val favorites: StateFlow<List<Long>> = container.userData.favorites
    val favoriteSet: StateFlow<Set<Long>> = container.userData.favoriteSet
    val playlists: StateFlow<List<Playlist>> = container.userData.playlists
    val stats: StateFlow<Map<Long, SongStat>> = container.userData.stats
    val totalListenedMs: StateFlow<Long> = container.userData.totalListenedMs

    val recommendations: StateFlow<List<RecommendationSection>> =
        combine(library, favoriteSet, stats, playlists) { lib, favs, stats, playlists ->
            RecommendationEngine.recommend(lib, favs, stats, playlists, System.currentTimeMillis())
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ---- derived song lists ----

    fun favoriteSongs(lib: Library, ids: List<Long>): List<Song> = ids.mapNotNull { lib.byId[it] }

    fun recentlyPlayed(lib: Library, stats: Map<Long, SongStat>): List<Song> =
        stats.values.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }.mapNotNull { lib.byId[it.songId] }

    fun mostPlayed(lib: Library, stats: Map<Long, SongStat>): List<Song> =
        stats.values.filter { it.plays > 0 }.sortedByDescending { it.plays }.mapNotNull { lib.byId[it.songId] }

    // ---- library ----

    fun refreshLibrary() {
        container.library.refresh()
        container.imports.rescanFolders()
    }

    fun importFiles(uris: List<Uri>) = container.imports.importFiles(uris)
    fun importFolder(uri: Uri) = container.imports.importFolder(uri)
    fun removeFolder(treeUri: String) = container.imports.removeFolder(treeUri)

    // ---- user data ----

    fun toggleFavorite(songId: Long) = container.userData.toggleFavorite(songId)
    fun createPlaylist(name: String, songs: List<Long> = emptyList(), onCreated: (Long) -> Unit = {}) = container.userData.createPlaylist(name, songs, onCreated)
    fun renamePlaylist(id: Long, name: String) = container.userData.renamePlaylist(id, name)
    fun deletePlaylist(id: Long) = container.userData.deletePlaylist(id)
    fun addToPlaylist(id: Long, songs: List<Long>) = container.userData.addToPlaylist(id, songs)
    fun removeFromPlaylist(id: Long, songs: List<Long>) = container.userData.removeFromPlaylist(id, songs)
    fun movePlaylistSong(id: Long, from: Int, to: Int) = container.userData.movePlaylistSong(id, from, to)
    fun clearHistory() = container.userData.clearHistory()

    // ---- settings ----

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setTheme(mode: ThemeMode) = launch { container.settings.setTheme(mode) }
    fun setVisualizer(on: Boolean) = launch { container.settings.setVisualizer(on) }
    fun setReduceMotion(on: Boolean) = launch { container.settings.setReduceMotion(on) }
    fun setShuffleNewQueues(on: Boolean) = launch { container.settings.setShuffleNewQueues(on) }
    fun setFadeSeconds(s: Int) = launch { container.settings.setFadeSeconds(s) }
    fun setSpeed(speed: Float) = launch { container.settings.setSpeed(speed) }
    fun setSleepFadeOut(on: Boolean) = launch { container.settings.setSleepFadeOut(on) }
    fun setSort(field: SortField, order: SortOrder) = launch { container.settings.setSort(field, order) }
    fun setOnlineEnabled(on: Boolean) = launch { container.settings.setOnlineEnabled(on) }
    fun setIncludeNonMusic(on: Boolean) = launch { container.settings.setIncludeNonMusic(on) }
    fun setEqEnabled(on: Boolean) = launch { container.settings.setEqEnabled(on) }
    fun setEqPreset(index: Int) = launch { container.settings.setEqPreset(index) }
    fun setEqLevels(levels: List<Int>) = launch { container.settings.setEqLevels(levels, preset = -1) }
    fun setBass(strength: Int) = launch { container.settings.setBass(strength) }
    fun setLoudness(gainMb: Int) = launch { container.settings.setLoudness(gainMb) }
    fun resetEffects(bandCount: Int) = launch { container.settings.resetEffects(bandCount) }

    // ---- optional online catalogue ----
    private val _downloads = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Float>>(emptyMap())
    /** Progress 0..1 of downloads in flight, keyed by track media id. */
    val downloads: StateFlow<Map<String, Float>> = _downloads

    suspend fun searchOnline(query: String) = container.online.search(query)

    fun download(track: com.rishinnovations.isai.data.online.OnlineTrack) {
        if (_downloads.value.containsKey(track.mediaId)) return
        _downloads.value = _downloads.value + (track.mediaId to 0f)
        viewModelScope.launch {
            try {
                val dir = java.io.File(getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "ISAI")
                val file = container.online.download(track, dir) { p -> _downloads.value = _downloads.value + (track.mediaId to p) }
                container.imports.importFiles(listOf(Uri.fromFile(file)))
            } catch (e: Exception) {
                com.rishinnovations.isai.AppMessages.post("Download failed: ${track.title}")
            } finally {
                _downloads.value = _downloads.value - track.mediaId
            }
        }
    }

    fun artworkCacheBytes(): Long = ArtworkCache.sizeBytes(getApplication())

    fun clearArtworkCache() {
        ArtworkCache.clear(getApplication())
        coil.Coil.imageLoader(getApplication()).memoryCache?.clear()
    }
}
