@file:OptIn(ExperimentalMaterial3Api::class)

package com.rishinnovations.isai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rishinnovations.isai.logic.formatDuration
import com.rishinnovations.isai.logic.sortedWith
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.model.SortField
import com.rishinnovations.isai.model.SortOrder
import com.rishinnovations.isai.ui.LocalActions
import com.rishinnovations.isai.ui.Route
import com.rishinnovations.isai.ui.Tabs
import com.rishinnovations.isai.ui.components.Artwork
import com.rishinnovations.isai.ui.components.ConfirmDialog
import com.rishinnovations.isai.ui.components.EmptyState
import com.rishinnovations.isai.ui.components.IconTile
import com.rishinnovations.isai.ui.components.MediaCard
import com.rishinnovations.isai.ui.components.ScreenTitle
import com.rishinnovations.isai.ui.components.SongRow
import com.rishinnovations.isai.ui.components.TextInputDialog
import com.rishinnovations.isai.ui.components.songItems
import com.rishinnovations.isai.ui.theme.isai

private val libraryTabs = listOf("Songs", "Albums", "Artists", "Genres", "Favorites", "Recent", "Added")

@Composable
fun PlayShuffleRow(songs: List<Song>) {
    val a = LocalActions.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { a.play(songs, 0) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Play")
        }
        OutlinedButton(onClick = { a.shuffle(songs) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Shuffle, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Shuffle")
        }
    }
}

@Composable
fun LibraryScreen() {
    val a = LocalActions.current
    val library by a.app.library.collectAsStateWithLifecycle()
    val settings by a.app.settings.collectAsStateWithLifecycle()
    val stats by a.app.stats.collectAsStateWithLifecycle()
    val favoriteIds by a.app.favorites.collectAsStateWithLifecycle()
    val scanning by a.app.scanning.collectAsStateWithLifecycle()
    val tab = a.nav.libraryTab.coerceIn(0, libraryTabs.lastIndex)
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Library", subtitle = "${library.songs.size} songs",
            actions = {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort songs") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortField.entries.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.label) },
                                leadingIcon = { if (settings.sortField == field) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                                onClick = { a.app.setSort(field, settings.sortOrder); sortMenu = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (settings.sortOrder == SortOrder.ASCENDING) "Ascending" else "Descending") },
                            leadingIcon = { Icon(if (settings.sortOrder == SortOrder.ASCENDING) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, contentDescription = null) },
                            onClick = {
                                a.app.setSort(settings.sortField, if (settings.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING)
                                sortMenu = false
                            }
                        )
                    }
                }
                IconButton(onClick = { a.nav.push(Route.Search) }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                IconButton(onClick = a.app::refreshLibrary, enabled = !scanning) { Icon(Icons.Filled.Refresh, contentDescription = if (scanning) "Scanning" else "Rescan music") }
            }
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = Color.Transparent, divider = {}) {
            libraryTabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { a.nav.libraryTab = index }, text = { Text(title) })
            }
        }
        if (library.songs.isEmpty()) {
            EmptyState(
                Icons.Filled.MusicNote, "Your library is empty",
                "Allow access to device music or add files and folders. New songs show up automatically.",
                actionLabel = "Add music", onAction = a.openAddMusic
            )
            return@Column
        }
        when (tab) {
            0 -> {
                val sorted = remember(library, settings.sortField, settings.sortOrder) { library.songs.sortedWith(settings.sortField, settings.sortOrder) }
                SongListWithActions(sorted, "Sorted by ${settings.sortField.label.lowercase()}")
            }
            1 -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(library.albums, key = { it.key }) { album ->
                    MediaCard(album.representative, album.title, "${album.artist} · ${album.songs.size}", onClick = { a.nav.push(Route.Album(album.key)) }, size = 150.dp)
                }
            }
            2 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(library.artists, key = { it.name }) { artist ->
                    Row(
                        Modifier.fillMaxWidth().clickable { a.nav.push(Route.Artist(artist.name)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(artist.songs.first(), Modifier.size(52.dp), CircleShape)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${artist.songs.size} songs · ${artist.albumCount} albums", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            3 -> if (library.genres.isEmpty()) {
                EmptyState(Icons.Filled.MusicNote, "No genre information", "None of your files carry genre tags.")
            } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(library.genres, key = { it.name }) { genre ->
                    Row(Modifier.fillMaxWidth().clickable { a.nav.push(Route.Genre(genre.name)) }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(genre.name, style = MaterialTheme.typography.titleSmall)
                            Text("${genre.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            4 -> {
                val favorites = remember(library, favoriteIds) { a.app.favoriteSongs(library, favoriteIds) }
                if (favorites.isEmpty()) EmptyState(Icons.Filled.Favorite, "No favorites yet", "Tap the heart on any song to keep it here.")
                else SongListWithActions(favorites, "Most recently added first")
            }
            5 -> {
                val recent = remember(library, stats) { a.app.recentlyPlayed(library, stats) }
                if (recent.isEmpty()) EmptyState(Icons.Filled.MusicNote, "Nothing played yet", "Songs you listen to appear here. A song counts after you've heard a few seconds of it.")
                else SongListWithActions(recent, "Most recent first")
            }
            else -> SongListWithActions(remember(library) { library.recentlyAdded }, "Newest first")
        }
    }
}

@Composable
private fun SongListWithActions(songs: List<Song>, caption: String) {
    val a = LocalActions.current
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PlayShuffleRow(songs) }
        item { Text(caption, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        songItems(songs, onPlayAt = { a.play(songs, it) })
    }
}

@Composable
fun PlaylistsScreen() {
    val a = LocalActions.current
    val library by a.app.library.collectAsStateWithLifecycle()
    val playlists by a.app.playlists.collectAsStateWithLifecycle()
    val favorites by a.app.favorites.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<com.rishinnovations.isai.model.Playlist?>(null) }
    var deleting by remember { mutableStateOf<com.rishinnovations.isai.model.Playlist?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Playlists", subtitle = "${playlists.size} playlists", actions = {
            IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "New playlist") }
        })
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clickable { a.nav.push(Route.Collection("favorites")) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Favorite, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Favorites", style = MaterialTheme.typography.titleSmall)
                        Text("${favorites.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (playlists.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Filled.PlaylistPlay, "No playlists yet",
                        "Group songs into playlists. Add songs from the ⋮ menu on any song.",
                        actionLabel = "Create a playlist", onAction = { creating = true }
                    )
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                var menu by remember { mutableStateOf(false) }
                val cover = playlist.songIds.firstNotNullOfOrNull { library.byId[it] }
                Row(Modifier.fillMaxWidth().clickable { a.nav.push(Route.Playlist(playlist.id)) }.padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (cover != null) Artwork(cover, Modifier.size(52.dp)) else IconTile(Icons.AutoMirrored.Filled.PlaylistPlay, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.songIds.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${playlist.name}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; renaming = playlist })
                            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; deleting = playlist })
                        }
                    }
                }
            }
        }
    }
    if (creating) TextInputDialog("New playlist", "", "Create", "Playlist name", onConfirm = { a.app.createPlaylist(it) }, onDismiss = { creating = false })
    renaming?.let { p -> TextInputDialog("Rename playlist", p.name, "Rename", "Playlist name", onConfirm = { a.app.renamePlaylist(p.id, it) }, onDismiss = { renaming = null }) }
    deleting?.let { p ->
        ConfirmDialog("Delete \"${p.name}\"?", "The playlist is removed. Your songs are not deleted.", "Delete", onConfirm = { a.app.deletePlaylist(p.id) }, onDismiss = { deleting = null })
    }
}

@Composable
fun PlaylistDetailScreen(playlistId: Long) {
    val a = LocalActions.current
    val library by a.app.library.collectAsStateWithLifecycle()
    val playlists by a.app.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    var editing by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    if (playlist == null) {
        Column(Modifier.fillMaxSize()) {
            SubScreenBar("Playlist", onBack = { a.nav.pop() })
            EmptyState(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlist not found", "It may have been deleted.", actionLabel = "Go back", onAction = { a.nav.pop() })
        }
        return
    }
    val resolved = remember(playlist, library) { playlist.songIds.map { id -> id to library.byId[id] } }
    val playable = remember(resolved) { resolved.mapNotNull { it.second } }
    val missing = resolved.count { it.second == null }
    val totalMs = playable.sumOf { it.durationMs }

    Column(Modifier.fillMaxSize()) {
        SubScreenBar(playlist.name, onBack = { a.nav.pop() }) {
            IconButton(onClick = { editing = !editing }) { Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = if (editing) "Finish editing" else "Edit playlist") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; deleting = true })
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "${playlist.songIds.size} songs · ${formatDuration(totalMs)}",
                    Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PlayShuffleRow(playable)
            }
            if (missing > 0) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$missing song(s) are no longer on this device.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { a.app.removeFromPlaylist(playlist.id, resolved.filter { it.second == null }.map { it.first }) }) { Text("Remove") }
                    }
                }
            }
            if (playlist.songIds.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.MusicNote, "This playlist is empty", "Use the ⋮ menu on any song and choose “Add to playlist”.")
                }
            }
            itemsIndexed(resolved, key = { index, pair -> "${pair.first}-$index" }) { index, (songId, song) ->
                if (song == null) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Unavailable song", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { a.app.removeFromPlaylist(playlist.id, listOf(songId)) }) { Icon(Icons.Filled.Close, contentDescription = "Remove unavailable song") }
                    }
                } else {
                    val playIndex = playable.indexOfFirst { it.id == song.id }
                    SongRow(song, onClick = { a.play(playable, playIndex.coerceAtLeast(0)) }, showFavorite = !editing, trailing = if (editing) {
                        {
                            IconButton(onClick = { a.app.movePlaylistSong(playlist.id, index, index - 1) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move ${song.title} up") }
                            IconButton(onClick = { a.app.movePlaylistSong(playlist.id, index, index + 1) }, enabled = index < resolved.lastIndex) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move ${song.title} down") }
                            IconButton(onClick = { a.app.removeFromPlaylist(playlist.id, listOf(song.id)) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${song.title} from playlist") }
                        }
                    } else null)
                }
            }
        }
    }
    if (renaming) TextInputDialog("Rename playlist", playlist.name, "Rename", "Playlist name", onConfirm = { a.app.renamePlaylist(playlist.id, it) }, onDismiss = { renaming = false })
    if (deleting) ConfirmDialog("Delete \"${playlist.name}\"?", "The playlist is removed. Your songs are not deleted.", "Delete", onConfirm = { a.app.deletePlaylist(playlist.id); a.nav.pop() }, onDismiss = { deleting = false })
}

/** Top bar for screens pushed above the tabs. */
@Composable
fun SubScreenBar(title: String, onBack: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, Modifier.weight(1f).padding(horizontal = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}
