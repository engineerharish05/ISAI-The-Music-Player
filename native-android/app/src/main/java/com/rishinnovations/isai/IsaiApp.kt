package com.rishinnovations.isai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import kotlinx.coroutines.delay

private val Gold = Color(0xFFD2AE62)
private val Black = Color(0xFF090908)
private val Surface = Color(0xFF171613)
private val Surface2 = Color(0xFF211F1A)

@Composable
fun IsaiApp(vm: IsaiViewModel = viewModel()) {
    val songs by vm.songs.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val player by vm.player.collectAsState()
    val error by vm.error.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var nowPlaying by remember { mutableStateOf(false) }
    var playlistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.connect()
        vm.scan()
        while (true) { delay(700); vm.tick() }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, background = Black, surface = Surface,
        onPrimary = Black, onBackground = Color.White, onSurface = Color.White
    )) {
        val current = songs.firstOrNull { it.id == player.currentSongId }

        if (nowPlaying && current != null) {
            NowPlaying(
                song = current, state = player, isFavorite = favorites.contains(current.id),
                onBack = { nowPlaying = false },
                onFavorite = { vm.toggleFavorite(current.id) },
                onPlayPause = vm::togglePlayPause, onPrevious = vm::previous, onNext = vm::next,
                onSeek = vm::seekTo, onShuffle = vm::toggleShuffle, onRepeat = vm::cycleRepeat
            )
            return@MaterialTheme
        }

        Scaffold(
            containerColor = Black,
            bottomBar = {
                Column {
                    if (current != null) MiniPlayer(
                        song = current, playing = player.isPlaying, onClick = { nowPlaying = true },
                        onPlayPause = vm::togglePlayPause, onNext = vm::next
                    )
                    NavigationBar(containerColor = Surface) {
                        val tabs = listOf(
                            "Home" to Icons.Filled.Home, "Search" to Icons.Filled.Search,
                            "Library" to Icons.Filled.LibraryMusic, "Playlists" to Icons.Filled.QueueMusic
                        )
                        tabs.forEachIndexed { i, item ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(item.second, null) }, label = { Text(item.first) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(songs, favorites, vm, padding)
                1 -> SearchScreen(songs, favorites, search, { search = it }, vm, padding)
                2 -> LibraryScreen(songs, favorites, vm, padding)
                else -> PlaylistScreen(songs, playlists, vm, padding, { playlistDialog = true })
            }
        }

        if (error != null) {
            AlertDialog(
                onDismissRequest = vm::clearError, title = { Text("ISAI") },
                text = { Text(error ?: "") },
                confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }
            )
        }

        if (playlistDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { playlistDialog = false }, title = { Text("New playlist") },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Playlist name") }) },
                confirmButton = {
                    TextButton(onClick = { vm.createPlaylist(name); playlistDialog = false }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { playlistDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun HomeScreen(songs: List<Song>, favorites: Set<Long>, vm: IsaiViewModel, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("ISAI", color = Gold, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text("THE MUSIC PLAYER", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
        Spacer(Modifier.height(22.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(Modifier.padding(24.dp)) {
                Text("Your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (songs.isEmpty()) "Grant audio access and scan your device." else songs.size.toString() + " songs ready to play", color = Color.LightGray)
                Spacer(Modifier.height(18.dp))
                Button(enabled = songs.isNotEmpty(), onClick = { vm.playAll(songs) }, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play all")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Recently available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (songs.isEmpty()) EmptyState("No local music found") else SongList(songs.take(10), favorites, vm)
    }
}

@Composable
private fun SearchScreen(
    songs: List<Song>, favorites: Set<Long>, query: String, onQuery: (String) -> Unit,
    vm: IsaiViewModel, padding: PaddingValues
) {
    val filtered = remember(songs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) songs else songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
    }
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Songs, artists or albums") }
        )
        Spacer(Modifier.height(14.dp))
        Text(filtered.size.toString() + " results", color = Gold)
        Spacer(Modifier.height(8.dp))
        SongList(filtered, favorites, vm)
    }
}

@Composable
private fun LibraryScreen(songs: List<Song>, favorites: Set<Long>, vm: IsaiViewModel, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(songs.size.toString() + " songs  •  " + favorites.size.toString() + " favorites", color = Gold)
        Spacer(Modifier.height(12.dp))
        if (songs.isEmpty()) EmptyState("Your device library is empty") else SongList(songs, favorites, vm)
    }
}

@Composable
private fun PlaylistScreen(
    songs: List<Song>, playlists: Map<String, Set<Long>>, vm: IsaiViewModel,
    padding: PaddingValues, onCreate: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Playlists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(playlists.size.toString() + " playlists", color = Gold)
            }
            FilledTonalButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("New")
            }
        }
        Spacer(Modifier.height(16.dp))
        if (playlists.isEmpty()) EmptyState("Create a playlist to organize your music") else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                playlists.forEach { (name, ids) ->
                    item(key = name) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.QueueMusic, null, tint = Gold, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold)
                                    Text(ids.size.toString() + " songs", color = Color.LightGray)
                                }
                                val list = ids.mapNotNull { id -> songs.firstOrNull { it.id == id } }
                                IconButton(enabled = list.isNotEmpty(), onClick = { vm.playAll(list) }) { Icon(Icons.Filled.PlayArrow, null, tint = Gold) }
                                IconButton(onClick = { vm.deletePlaylist(name) }) { Icon(Icons.Filled.DeleteOutline, null) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongList(songs: List<Song>, favorites: Set<Long>, vm: IsaiViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
            SongRow(song, favorites.contains(song.id), { vm.play(song) }, { vm.toggleFavorite(song.id) })
        }
    }
}

@Composable
private fun SongRow(song: Song, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).background(Gold.copy(alpha = .16f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, null, tint = Gold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist + " • " + song.album, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatTime(song.durationMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (favorite) Gold else Color.Gray)
        }
    }
}

@Composable
private fun MiniPlayer(song: Song, playing: Boolean, onClick: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Surface2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(Gold.copy(alpha = .15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null, tint = Gold) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) }
        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, null) }
    }
}

@Composable
private fun NowPlaying(
    song: Song, state: PlayerUiState, isFavorite: Boolean, onBack: () -> Unit, onFavorite: () -> Unit,
    onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit,
    onShuffle: () -> Unit, onRepeat: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, null) }
            Text("NOW PLAYING", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Gold)
            IconButton(onClick = onFavorite) { Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = Gold) }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF2A261D), RoundedCornerShape(34.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, null, tint = Gold, modifier = Modifier.size(120.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = Color.LightGray, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Slider(
            value = if (state.durationMs > 0) state.positionMs.toFloat().coerceIn(0f, state.durationMs.toFloat()) else 0f,
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(state.positionMs), color = Color.Gray)
            Text(formatTime(state.durationMs), color = Color.Gray)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onShuffle) { Icon(Icons.Filled.Shuffle, null, tint = if (state.shuffle) Gold else Color.Gray) }
            IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, null, modifier = Modifier.size(34.dp)) }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(68.dp)) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, modifier = Modifier.size(34.dp)) }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(34.dp)) }
            IconButton(onClick = onRepeat) {
                Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null, tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) Gold else Color.Gray)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.LibraryMusic, null, tint = Color.Gray, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp)); Text(text, color = Color.Gray)
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
