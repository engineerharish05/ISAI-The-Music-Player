package com.rishinnovations.isai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

private val Gold = Color(0xFFC6A15B)
private val Black = Color(0xFF0A0A09)
private val Surface = Color(0xFF171714)
private val Surface2 = Color(0xFF22221D)

@Composable
fun IsaiApp(vm: IsaiViewModel = viewModel()) {
    val songs by vm.songs.collectAsState()
    val player by vm.playerState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val favorites = remember { mutableStateListOf<Long>() }

    LaunchedEffect(Unit) { vm.connect(); vm.scan() }

    val filtered = remember(songs, search) {
        if (search.isBlank()) songs else songs.filter {
            it.title.contains(search, true) || it.artist.contains(search, true) || it.album.contains(search, true)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Black, surface = Surface)) {
        Scaffold(
            containerColor = Black,
            bottomBar = {
                Column {
                    player.currentSong?.let {
                        MiniPlayer(it, player, { showPlayer = true }, vm::togglePlayPause, vm::next)
                    }
                    NavigationBar(containerColor = Surface) {
                        val tabs = listOf(
                            "Home" to Icons.Filled.Home,
                            "Search" to Icons.Filled.Search,
                            "Library" to Icons.Filled.LibraryMusic,
                            "Queue" to Icons.Filled.QueueMusic
                        )
                        tabs.forEachIndexed { i, item ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(item.second, contentDescription = item.first) },
                                label = { Text(item.first) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> Home(songs, player, vm, favorites, padding) { showPlayer = true }
                1 -> SearchPage(filtered, search, { search = it }, vm, favorites, padding)
                2 -> Library(songs, vm, favorites, padding)
                else -> QueuePage(player, vm, padding)
            }
        }

        if (showPlayer && player.currentSong != null) {
            FullPlayer(player, vm, { showPlayer = false }, favorites)
        }
    }
}

@Composable
private fun Home(songs: List<Song>, player: PlayerState, vm: IsaiViewModel, favorites: MutableList<Long>, padding: PaddingValues, openPlayer: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("ISAI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Gold)
            Text("THE MUSIC PLAYER", style = MaterialTheme.typography.labelLarge)
        }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("Your music.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(if (songs.isEmpty()) "Scan your device for local music." else "@{songs.size} songs on this device", color = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = songs.isNotEmpty(), onClick = vm::playAll) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play all")
                        }
                        OutlinedButton(enabled = player.currentSong != null, onClick = openPlayer) { Text("Now playing") }
                    }
                }
            }
        }
        item { Text("Recently available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (songs.isEmpty()) item { EmptyState("No local music found") }
        else items(songs.take(12), key = { it.id }) { song ->
            SongRow(song, song.id in favorites, { favorites.toggle(song.id) }) { vm.play(song) }
        }
    }
}

@Composable
private fun SearchPage(songs: List<Song>, search: String, onSearch: (String) -> Unit, vm: IsaiViewModel, favorites: MutableList<Long>, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Songs, artists or albums") },
            leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(songs, key = { it.id }) { song ->
                SongRow(song, song.id in favorites, { favorites.toggle(song.id) }) { vm.play(song) }
            }
        }
    }
}

@Composable
private fun Library(songs: List<Song>, vm: IsaiViewModel, favorites: MutableList<Long>, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("@{songs.size} songs", color = Gold)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(songs, key = { it.id }) { song ->
                SongRow(song, song.id in favorites, { favorites.toggle(song.id) }) { vm.play(song) }
            }
        }
    }
}

@Composable
private fun QueuePage(player: PlayerState, vm: IsaiViewModel, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Queue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (player.queue.isEmpty()) EmptyState("Nothing queued")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(player.queue, key = { it.id }) { song ->
                SongRow(song, active = player.currentSong?.id == song.id, favorite = false, onFavorite = {}, onClick = { vm.play(song) })
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit, active: Boolean = false) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (active) Gold.copy(alpha = .13f) else Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = song.artworkUri, contentDescription = null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@{song.artist} · @{song.album}", color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = if (favorite) Gold else Color.LightGray)
        }
    }
}

@Composable
private fun MiniPlayer(song: Song, state: PlayerState, onClick: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Surface2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = song.artworkUri, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) }
        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, null) }
    }
}

@Composable
private fun FullPlayer(state: PlayerState, vm: IsaiViewModel, onDismiss: () -> Unit, favorites: MutableList<Long>) {
    val song = state.currentSong ?: return
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Black) {
            Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
                    Text("Now Playing", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    IconButton(onClick = {}) { Icon(Icons.Filled.MoreHoriz, "More") }
                }
                Spacer(Modifier.height(28.dp))
                AsyncImage(
                    model = song.artworkUri, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = Color.LightGray)
                    }
                    IconButton(onClick = { favorites.toggle(song.id) }) {
                        Icon(if (song.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = Gold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = state.positionMs.coerceAtMost(state.durationMs).toFloat(),
                    onValueChange = { vm.seekTo(it.toLong()) },
                    valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.positionMs), color = Color.Gray)
                    Text(formatTime(state.durationMs), color = Color.Gray)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffle) Gold else Color.White) }
                    IconButton(onClick = vm::previous) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                    FilledIconButton(onClick = vm::togglePlayPause, modifier = Modifier.size(72.dp)) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, "Next") }
                    IconButton(onClick = vm::cycleRepeat) {
                        Icon(if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", tint = if (state.repeatMode == RepeatMode.OFF) Color.White else Gold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.queue, key = { it.id }) { item ->
                        SongRow(item, active = item.id == song.id, favorite = false, onFavorite = {}, onClick = { vm.play(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text(text, color = Color.LightGray) }
}

private fun MutableList<Long>.toggle(id: Long) {
    if (contains(id)) remove(id) else add(id)
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}
