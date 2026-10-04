package com.rishinnovations.isai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

private val Gold = Color(0xFFC6A15B)
private val Black = Color(0xFF090908)
private val Surface = Color(0xFF151513)
private val Surface2 = Color(0xFF1E1E1A)
private val Muted = Color(0xFFA7A7A0)

@Composable
fun IsaiApp(permissionRefresh: Int = 0, vm: IsaiViewModel = viewModel()) {
    val songs by vm.songs.collectAsState()
    val player by vm.playerState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val favorites = remember { mutableStateListOf<Long>() }

    LaunchedEffect(permissionRefresh) {
        vm.connect()
        vm.scan()
    }

    val filtered = remember(songs, search) {
        if (search.isBlank()) songs else songs.filter {
            it.title.contains(search, true) ||
                it.artist.contains(search, true) ||
                it.album.contains(search, true)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            secondary = Gold,
            background = Black,
            surface = Surface
        )
    ) {
        Scaffold(
            containerColor = Black,
            bottomBar = {
                Column {
                    player.currentSong?.let { song ->
                        MiniPlayer(song, player, { showPlayer = true }, vm::togglePlayPause, vm::next)
                    }
                    NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
                        NavigationItem(0, tab, Icons.Filled.Home, "Home") { tab = it }
                        NavigationItem(1, tab, Icons.Filled.Search, "Search") { tab = it }
                        NavigationItem(2, tab, Icons.Filled.LibraryMusic, "Library") { tab = it }
                        NavigationItem(3, tab, Icons.Filled.QueueMusic, "Queue") { tab = it }
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> Home(songs, player, vm, favorites, padding, { showPlayer = true }, { tab = 2 })
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
@Composable
private fun NavigationItem(
    index: Int,
    selectedIndex: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onSelect: (Int) -> Unit
) {
    NavigationBarItem(
        selected = selectedIndex == index,
        onClick = { onSelect(index) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}

@Composable
private fun Home(
    songs: List<Song>,
    player: PlayerState,
    vm: IsaiViewModel,
    favorites: MutableList<Long>,
    padding: PaddingValues,
    openPlayer: () -> Unit,
    openLibrary: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ISAI", style = MaterialTheme.typography.labelLarge, color = Gold, fontWeight = FontWeight.Bold)
                Text(
                    "Your day.\nYour moments.\nYour Isai.",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text("A refined music experience designed around the songs you love.", color = Muted, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(if (songs.isEmpty()) "No local music detected yet" else "${songs.size} songs available on this device", color = Muted)
                        }
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Gold.copy(alpha = .14f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.MusicNote, null, tint = Gold) }
                    }
                    Button(
                        onClick = vm::playAll,
                        enabled = songs.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play all")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = openLibrary, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Library") }
                        OutlinedButton(onClick = vm::scan, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Filled.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Scan")
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recently available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (songs.isNotEmpty()) Text("${songs.size}", color = Gold, fontWeight = FontWeight.Bold)
            }
        }
        if (songs.isEmpty()) {
            item { EmptyState("Allow music access, then tap Scan.") }
        } else {
            items(songs.take(12), key = { it.id }) { song ->
                SongRow(song, song.id in favorites, { favorites.toggle(song.id) }, { vm.play(song) })
            }
        }
        player.currentSong?.let { current ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = openPlayer),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface2)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(current.artworkUri, null, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Now playing", color = Gold, style = MaterialTheme.typography.labelMedium)
                            Text(current.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Filled.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPage(
    songs: List<Song>, search: String, onSearch: (String) -> Unit,
    vm: IsaiViewModel, favorites: MutableList<Long>, padding: PaddingValues
) {
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 22.dp)) {
        Text("Search", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Find a song, artist or album.", color = Muted)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your music") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Close, "Clear search") }
            },
            singleLine = true, shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(18.dp))
        if (songs.isEmpty()) {
            EmptyState(if (search.isBlank()) "Your local songs will appear here." else "No matching songs.")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SongRow(song, song.id in favorites, { favorites.toggle(song.id) }, { vm.play(song) })
                }
            }
        }
    }
}

@Composable
private fun Library(songs: List<Song>, vm: IsaiViewModel, favorites: MutableList<Long>, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("${songs.size} songs", color = Gold, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = vm::scan) { Icon(Icons.Filled.Refresh, "Scan library") }
        }
        Spacer(Modifier.height(12.dp))
        if (songs.isEmpty()) {
            EmptyState("No local songs found. Tap refresh to scan again.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(songs, key = { it.id }) { song ->
                    SongRow(song, song.id in favorites, { favorites.toggle(song.id) }, { vm.play(song) })
                }
            }
        }
    }
}

@Composable
private fun QueuePage(player: PlayerState, vm: IsaiViewModel, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 22.dp)) {
        Text("Queue", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            if (player.queue.isEmpty()) "Nothing is queued."
            else "${player.queue.size} tracks · ${player.queueIndex + 1} current",
            color = Muted
        )
        Spacer(Modifier.height(16.dp))
        if (player.queue.isEmpty()) {
            EmptyState("Play a song or choose Play all to build the queue.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(player.queue, key = { it.id }) { song ->
                    SongRow(song, player.currentSong?.id == song.id, {}, { vm.play(song) })
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit, active: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (active) Gold.copy(alpha = .13f) else Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(song.artworkUri, null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} · ${song.album}", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = if (favorite) Gold else Muted)
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song, state: PlayerState, onClick: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Surface2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(song.artworkUri, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause") }
        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Next") }
    }
}

@Composable
private fun FullPlayer(state: PlayerState, vm: IsaiViewModel, onDismiss: () -> Unit, favorites: MutableList<Long>) {
    val song = state.currentSong ?: return
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Black) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(320.dp).background(
                        Brush.verticalGradient(listOf(Gold.copy(alpha = .22f), Black.copy(alpha = 0f)))
                    )
                )
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.KeyboardArrowDown, "Close player") }
                        Text("NOW PLAYING", Modifier.weight(1f), textAlign = TextAlign.Center, color = Gold, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {}) { Icon(Icons.Filled.MoreHoriz, "More") }
                    }
                    Spacer(Modifier.height(14.dp))
                    AsyncImage(song.artworkUri, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { favorites.toggle(song.id) }) {
                            Icon(if (song.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = Gold)
                        }
                    }
                    Slider(
                        value = state.positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(1L)).toFloat(),
                        onValueChange = { vm.seekTo(it.toLong()) },
                        valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(state.positionMs), color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(formatTime(state.durationMs), color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffle) Gold else Color.White) }
                        IconButton(onClick = vm::previous) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                        FilledIconButton(onClick = vm::togglePlayPause, modifier = Modifier.size(68.dp), shape = CircleShape) {
                            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, "Next") }
                        IconButton(onClick = vm::cycleRepeat) {
                            Icon(if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", tint = if (state.repeatMode == RepeatMode.OFF) Color.White else Gold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("UP NEXT", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(state.queue, key = { it.id }) { item ->
                            SongRow(item, item.id == song.id, {}, { vm.play(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, textAlign = TextAlign.Center)
    }
}

private fun MutableList<Long>.toggle(id: Long) {
    if (contains(id)) remove(id) else add(id)
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}
