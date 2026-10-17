@file:OptIn(ExperimentalMaterial3Api::class)

package com.rishinnovations.isai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rishinnovations.isai.R
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.ui.LocalActions
import com.rishinnovations.isai.ui.Route
import com.rishinnovations.isai.ui.Tabs
import com.rishinnovations.isai.ui.components.EmptyState
import com.rishinnovations.isai.ui.components.IconTile
import com.rishinnovations.isai.ui.components.MediaCard
import com.rishinnovations.isai.ui.components.ScreenTitle
import com.rishinnovations.isai.ui.components.SectionHeader
import com.rishinnovations.isai.ui.theme.isai
import java.time.LocalTime

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
private fun SongRail(title: String, songs: List<Song>, subtitle: String? = null, onSeeAll: (() -> Unit)? = null) {
    val a = LocalActions.current
    if (songs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, actionLabel = if (onSeeAll != null) "See all" else null, onAction = onSeeAll)
        if (subtitle != null) {
            Text(subtitle, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(songs.size, key = { "$title-${songs[it].id}-$it" }) { index ->
                val song = songs[index]
                MediaCard(song, song.title, song.artist, onClick = { a.play(songs, index) })
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val a = LocalActions.current
    val library by a.app.library.collectAsStateWithLifecycle()
    val loaded by a.app.libraryLoaded.collectAsStateWithLifecycle()
    val permission by a.app.permissionGranted.collectAsStateWithLifecycle()
    val stats by a.app.stats.collectAsStateWithLifecycle()
    val favoriteIds by a.app.favorites.collectAsStateWithLifecycle()
    val playlists by a.app.playlists.collectAsStateWithLifecycle()
    val recommendations by a.app.recommendations.collectAsStateWithLifecycle()

    val recent = remember(library, stats) { a.app.recentlyPlayed(library, stats).take(20) }
    val added = remember(library) { library.recentlyAdded.take(20) }
    val favorites = remember(library, favoriteIds) { a.app.favoriteSongs(library, favoriteIds).take(20) }
    val most = remember(library, stats) { a.app.mostPlayed(library, stats).take(20) }
    val recentAlbums = remember(library, recent) {
        recent.map { it.albumKey }.distinct().mapNotNull { library.album(it) }.take(12)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.isai_emblem), contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                Spacer(Modifier.width(10.dp))
                Text("ISAI", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.isai.gold, modifier = Modifier.weight(1f))
                IconButton(onClick = { a.nav.push(Route.Search) }) { Icon(Icons.Filled.Search, contentDescription = "Search your music") }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(greeting(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.isai.gold)
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.brand_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = a::listenNow) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_listen_now))
                    }
                    OutlinedButton(onClick = a.openAddMusic) { Text(stringResource(R.string.action_add_music)) }
                }
            }
        }

        if (loaded && !permission && library.songs.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.LibraryMusic, "Allow access to your music",
                    "ISAI reads the audio files on this device. Nothing is uploaded. You can also add individual files or folders without granting this permission.",
                    actionLabel = "Allow access", onAction = a.requestMusicPermission,
                    secondaryLabel = "Add files or a folder", onSecondary = a.openAddMusic
                )
            }
        } else if (loaded && library.songs.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.MusicNote, "No music found yet",
                    "Copy songs to this phone, or pick files and folders to add. They will appear here automatically.",
                    actionLabel = "Add music", onAction = a.openAddMusic
                )
            }
        } else {
            item {
                Surface(
                    onClick = { a.nav.openLibrary(0) },
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.LibraryMusic, Modifier.size(48.dp), CircleShape)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your library", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${library.songs.size} songs · ${library.albums.size} albums · ${library.artists.size} artists",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            item { SongRail("Recently played", recent, onSeeAll = { a.nav.push(Route.Collection("recent")) }) }
            if (recentAlbums.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Recently played albums")
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(recentAlbums, key = { it.key }) { album ->
                                MediaCard(album.representative, album.title, album.artist, onClick = { a.nav.push(Route.Album(album.key)) })
                            }
                        }
                    }
                }
            }
            recommendations.take(2).forEach { section ->
                item(key = "rec-${section.id}") {
                    SongRail(section.title, section.songs, subtitle = section.reason, onSeeAll = { a.nav.push(Route.Collection("rec:${section.id}")) })
                }
            }
            item { SongRail("Recently added", added, onSeeAll = { a.nav.push(Route.Collection("added")) }) }
            item { SongRail("Favorites", favorites, onSeeAll = { a.nav.push(Route.Collection("favorites")) }) }
            item { SongRail("Frequently played", most, onSeeAll = { a.nav.push(Route.Collection("most")) }) }
            if (playlists.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Your playlists", actionLabel = "See all", onAction = { a.nav.selectTab(Tabs.PLAYLISTS) })
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(playlists, key = { it.id }) { playlist ->
                                val cover = playlist.songIds.firstNotNullOfOrNull { library.byId[it] }
                                MediaCard(cover, playlist.name, "${playlist.songIds.size} songs", onClick = { a.nav.push(Route.Playlist(playlist.id)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreScreen() {
    val a = LocalActions.current
    val library by a.app.library.collectAsStateWithLifecycle()
    val recommendations by a.app.recommendations.collectAsStateWithLifecycle()
    val playlists by a.app.playlists.collectAsStateWithLifecycle()
    val decades = remember(library) { library.songs.map { it.year / 10 * 10 }.filter { it >= 1900 }.distinct().sortedDescending() }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { ScreenTitle("Explore", subtitle = "Everything here comes from your own library") }
        item {
            Surface(
                onClick = { a.nav.push(Route.Search) },
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Search songs, artists, albums…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (library.songs.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.MusicNote, "Nothing to explore yet",
                    "Add music and Explore will organise it by albums, artists, genres and listening history.",
                    actionLabel = "Add music", onAction = a.openAddMusic
                )
            }
            return@LazyColumn
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Collections")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { CollectionTile("Shuffle everything", "${library.songs.size} songs", Icons.Filled.Shuffle) { a.shuffle(library.songs) } }
                    item { CollectionTile("Fresh additions", "Recently added", Icons.Filled.NewReleases) { a.nav.push(Route.Collection("added")) } }
                    item { CollectionTile("Most played", "From your history", Icons.Filled.TrendingUp) { a.nav.push(Route.Collection("most")) } }
                    item { CollectionTile("Favorites", "Songs you hearted", Icons.Filled.Favorite) { a.nav.push(Route.Collection("favorites")) } }
                    items(recommendations, key = { it.id }) { section ->
                        CollectionTile(section.title, "${section.songs.size} songs", Icons.Filled.MusicNote) { a.nav.push(Route.Collection("rec:${section.id}")) }
                    }
                }
            }
        }
        if (library.genres.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Genres")
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(library.genres, key = { it.name }) { genre ->
                            AssistChip(onClick = { a.nav.push(Route.Genre(genre.name)) }, label = { Text("${genre.name} · ${genre.songs.size}") })
                        }
                    }
                }
            }
        }
        if (decades.size > 1) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Decades")
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(decades, key = { it }) { decade ->
                            AssistChip(onClick = { a.nav.push(Route.Collection("decade:$decade")) }, label = { Text("${decade}s") })
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Albums", actionLabel = "See all", onAction = { a.nav.openLibrary(1) })
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(library.albums.take(20), key = { it.key }) { album ->
                        MediaCard(album.representative, album.title, album.artist, onClick = { a.nav.push(Route.Album(album.key)) })
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Artists", actionLabel = "See all", onAction = { a.nav.openLibrary(2) })
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(library.artists.take(20), key = { it.name }) { artist ->
                        MediaCard(artist.songs.first(), artist.name, "${artist.songs.size} songs", onClick = { a.nav.push(Route.Artist(artist.name)) }, shape = CircleShape, size = 110.dp)
                    }
                }
            }
        }
        item {
            Surface(
                onClick = { a.nav.selectTab(Tabs.PLAYLISTS) },
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.AutoMirrored.Filled.PlaylistPlay, Modifier.size(48.dp), CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Playlists", style = MaterialTheme.typography.titleMedium)
                        Text(if (playlists.isEmpty()) "Create your first playlist" else "${playlists.size} playlists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun CollectionTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(150.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconTile(icon, Modifier.size(40.dp), CircleShape)
            Spacer(Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
