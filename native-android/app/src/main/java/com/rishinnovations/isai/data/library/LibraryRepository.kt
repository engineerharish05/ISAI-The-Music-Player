package com.rishinnovations.isai.data.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.rishinnovations.isai.data.db.ImportedSongEntity
import com.rishinnovations.isai.data.db.IsaiDatabase
import com.rishinnovations.isai.data.settings.SettingsRepository
import com.rishinnovations.isai.model.Library
import com.rishinnovations.isai.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single source of truth for the song catalogue: MediaStore audio plus files the user imported
 * through the system picker. Favorites and playlists store only song ids and are resolved here.
 */
class LibraryRepository(
    private val context: Context,
    db: IsaiDatabase,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val mediaStoreSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private val _permission = MutableStateFlow(hasAudioPermission())
    private val _scanning = MutableStateFlow(false)

    /** True after the first query has finished, whether or not it found anything. */
    val loaded: StateFlow<Boolean> = _loaded
    val permissionGranted: StateFlow<Boolean> = _permission
    val scanning: StateFlow<Boolean> = _scanning

    val library: StateFlow<Library> = combine(mediaStoreSongs, db.imports().observeSongs()) { device, imported ->
        Library(merge(device, imported))
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, Library.EMPTY)

    private var refreshJob: Job? = null
    private var debounceJob: Job? = null

    init {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    // MediaStore notifies many times during a scan; coalesce into one refresh.
                    debounceJob?.cancel()
                    debounceJob = scope.launch {
                        delay(1500)
                        refresh()
                    }
                }
            }
        )
        scope.launch {
            settings.settings.map { it.includeNonMusic }.distinctUntilChanged().collect { refresh() }
        }
    }

    fun snapshot(): Library = library.value

    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            _scanning.value = true
            try {
                val granted = hasAudioPermission()
                _permission.value = granted
                val includeNonMusic = settings.current().includeNonMusic
                mediaStoreSongs.value = if (granted) {
                    withContext(Dispatchers.IO) { runCatching { queryMediaStore(includeNonMusic) }.getOrDefault(emptyList()) }
                } else emptyList()
                _loaded.value = true
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun merge(device: List<Song>, imported: List<ImportedSongEntity>): List<Song> {
        if (imported.isEmpty()) return device
        // A file that is both indexed by MediaStore and imported should appear once.
        val seen = HashSet<String>(device.size)
        device.forEach { seen += signature(it.title, it.artist, it.durationMs) }
        val extra = imported.mapNotNull { e ->
            if (!seen.add(signature(e.title, e.artist, e.durationMs))) null else e.toSong()
        }
        return device + extra
    }

    private fun signature(title: String, artist: String, durationMs: Long) = "${title.lowercase()}|${artist.lowercase()}|${durationMs / 1000}"

    private fun ImportedSongEntity.toSong() = Song(
        id = -id, title = title, artist = artist, album = album, albumId = -1L, genre = genre,
        durationMs = durationMs, trackNumber = trackNumber, year = year, dateAddedSec = dateAddedSec,
        sizeBytes = sizeBytes, mimeType = mimeType, uriString = uri, isImported = true
    )

    private fun queryMediaStore(includeNonMusic: Boolean): List<Song> {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = ArrayList<String>().apply {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= 30) add(MediaStore.Audio.Media.GENRE)
        }
        val selection = buildString {
            append("${MediaStore.Audio.Media.DURATION} > 0")
            if (!includeNonMusic) append(" AND ${MediaStore.Audio.Media.IS_MUSIC} != 0")
        }
        val legacyGenres = if (Build.VERSION.SDK_INT < 30) loadLegacyGenres() else emptyMap()
        val songs = ArrayList<Song>()
        resolver.query(collection, columns.toTypedArray(), selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iGenre = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val title = c.getString(iTitle).clean() ?: c.getString(iName).clean()?.substringBeforeLast('.') ?: "Unknown title"
                songs += Song(
                    id = id,
                    title = title,
                    artist = c.getString(iArtist).cleanUnknown() ?: "Unknown artist",
                    album = c.getString(iAlbum).cleanUnknown() ?: "Unknown album",
                    albumId = c.getLong(iAlbumId),
                    genre = (if (iGenre >= 0) c.getString(iGenre) else legacyGenres[id]).clean(),
                    durationMs = c.getLong(iDuration),
                    trackNumber = c.getInt(iTrack) % 1000,
                    year = c.getInt(iYear),
                    dateAddedSec = c.getLong(iAdded),
                    sizeBytes = c.getLong(iSize),
                    mimeType = c.getString(iMime) ?: "audio/*",
                    uriString = android.content.ContentUris.withAppendedId(collection, id).toString()
                )
            }
        }
        return songs
    }

    @Suppress("DEPRECATION")
    private fun loadLegacyGenres(): Map<Long, String> {
        val result = HashMap<Long, String>()
        runCatching {
            val genresUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            context.contentResolver.query(genresUri, arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null)?.use { g ->
                while (g.moveToNext()) {
                    val genreId = g.getLong(0)
                    val name = g.getString(1).clean() ?: continue
                    val members = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    context.contentResolver.query(members, arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null)?.use { m ->
                        while (m.moveToNext()) result[m.getLong(0)] = name
                    }
                }
            }
        }
        return result
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    private fun String?.cleanUnknown(): String? = clean()?.takeUnless { it.equals("<unknown>", true) }

    fun uriFor(song: Song): Uri = song.uri
}
