package com.rishinnovations.isai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rishinnovations.isai.logic.SearchEngine
import com.rishinnovations.isai.logic.formatDuration
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.ui.LocalActions
import com.rishinnovations.isai.ui.Route
import com.rishinnovations.isai.ui.components.Artwork
import com.rishinnovations.isai.ui.components.EmptyState
import com.rishinnovations.isai.ui.components.IconTile
import com.rishinnovations.isai.ui.components.MediaCard
import com.rishinnovations.isai.ui.components.SectionHeader
import com.rishinnovations.isai.ui.components.songItems
import kotlinx.coroutines.delay

/** One screen for album, artist, genre and the built-in lists (favorites, recent, recommendations...). */
@Composable
fun CollectionScreen(route: Route) {
    val a = LocalActions.current
    val library by a.app.library.collectAsStateWithLifecycle()
    val stats by a.app.stats.collectAsStateWithLifecycle()
    val favoriteIds by a.app.favorites.collectAsStateWithLifecycle()
    val recs by a.app.recommendations.collectAsStateWithLifecycle()

    data class Content(val title: String, val subtitle: String, val songs: List<Song>, val emptyText: String = "Nothing here yet.")

    val content: Content = when (route) {
        is Route.Album -> library.album(route.key)?.let {
            Content(it.title, "${it.artist}${if (it.year > 0) " · ${it.year}" else ""}", it.songs)
        } ?: Content("Album", "", emptyList(), "This album is no longer in your library.")
        is Route.Artist -> library.artist(route.name)?.let { Content(it.name, "${it.songs.size} songs · ${it.albumCount} albums", it.songs) }
            ?: Content(route.name, "", emptyList(), "No songs by this artist.")
        is Route.Genre -> library.genre(route.name)?.let { Content(it.name, "${it.songs.size} songs", it.songs) }
            ?: Content(route.name, "", emptyList(), "No songs in this genre.")
        is Route.Collection -> {
            val kind = route.kind
            when {
                kind == "favorites" -> Content("Favorites", "Songs you hearted", a.app.favoriteSongs(library, favoriteIds), "Tap the heart on any song to keep it here.")
                kind == "recent" -> Content("Recently played", "Most recent first", a.app.recentlyPlayed(library, stats), "Songs you listen to appear here.")
                kind == "most" -> Content("Most played", "From your listening history", a.app.mostPlayed(library, stats), "Play some songs and they'll be ranked here.")
                kind == "added" -> Content("Recently added", "Newest first", library.recentlyAdded)
                kind.startsWith("rec:") -> {
                    val section = recs.firstOrNull { it.id == kind.removePrefix("rec:") }
                    Content(section?.title ?: "Recommended", section?.reason ?: "", section?.songs ?: emptyList(), "Recommendations refresh as you listen.")
                }
                kind.startsWith("decade:") -> {
                    val decade = kind.removePrefix("decade:").toIntOrNull() ?: 0
                    Content("${decade}s", "Songs tagged with a year in this decade", library.songs.filter { it.year / 10 * 10 == decade }.sortedBy { it.year })
                }
                else -> Content("Songs", "", emptyList())
            }
        }
        else -> Content("", "", emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenBar(content.title, onBack = { a.nav.pop() })
        if (content.subtitle.isNotBlank()) {
            Text(content.subtitle, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (content.songs.isEmpty()) {
            EmptyState(Icons.Filled.MusicNote, "Nothing to show", content.emptyText)
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "${content.songs.size} songs · ${formatDuration(content.songs.sumOf { it.durationMs })}",
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PlayShuffleRow(content.songs)
            }
            songItems(content.songs, onPlayAt = { a.play(content.songs, it) }, keyPrefix = "c")
        }
    }
}

@Composable
fun SearchScreen() {
    val a = LocalActions.current
    val library by a.app.library.collectAsStateWithLifecycle()
    val playlists by a.app.playlists.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf(query) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(150)
        debounced = query
    }
    val results by remember(library, playlists) {
        derivedStateOf { SearchEngine.search(debounced, library, playlists) }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenBar("Search", onBack = { a.nav.pop() })
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).focusRequester(focus),
            placeholder = { Text("Songs, albums, artists, genres, playlists") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )
        when {
            debounced.isBlank() -> EmptyState(Icons.Filled.Search, "Search your music", "Find anything in your library. Capitalization and accents don't matter.")
            results.isEmpty -> EmptyState(Icons.Filled.Search, "No matches for “$debounced”", "Check the spelling, or try fewer words. Search covers music on this device only.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (results.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(results.artists.take(5), key = { "ar-${it.name}" }) { artist ->
                        Row(Modifier.fillMaxWidth().clickable { a.nav.push(Route.Artist(artist.name)) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(artist.songs.first(), Modifier.size(48.dp), CircleShape)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${artist.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(results.albums.take(12), key = { "al-${it.key}" }) { album ->
                                MediaCard(album.representative, album.title, album.artist, onClick = { a.nav.push(Route.Album(album.key)) })
                            }
                        }
                    }
                }
                if (results.genres.isNotEmpty()) {
                    item { SectionHeader("Genres") }
                    items(results.genres.take(5), key = { "ge-${it.name}" }) { genre ->
                        Text(
                            "${genre.name} · ${genre.songs.size} songs",
                            Modifier.fillMaxWidth().clickable { a.nav.push(Route.Genre(genre.name)) }.padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                if (results.playlists.isNotEmpty()) {
                    item { SectionHeader("Playlists") }
                    items(results.playlists.take(5), key = { "pl-${it.id}" }) { playlist ->
                        Row(Modifier.fillMaxWidth().clickable { a.nav.push(Route.Playlist(playlist.id)) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.AutoMirrored.Filled.PlaylistPlay, Modifier.size(48.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("${playlist.name} · ${playlist.songIds.size} songs", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                if (results.songs.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    songItems(results.songs, onPlayAt = { a.play(results.songs, it) }, keyPrefix = "s")
                }
            }
        }
    }
}
