package com.rishinnovations.isai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

private val IsaiGold = Color(0xFFD6A85A)
private val IsaiGoldSoft = Color(0xFFF0C982)
private val IsaiBlack = Color(0xFF070707)
private val IsaiSurface = Color(0xFF11110F)
private val IsaiSurface2 = Color(0xFF191815)
private val IsaiText = Color(0xFFF5F1E8)
private val IsaiMuted = Color(0xFF9B978F)

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

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = IsaiGold,
            secondary = IsaiGoldSoft,
            background = IsaiBlack,
            surface = IsaiSurface,
            onBackground = IsaiText,
            onSurface = IsaiText
        )
    ) {
        Scaffold(
            containerColor = IsaiBlack,
            bottomBar = {
                Column {
                    player.currentSong?.let {
                        IsaiMiniPlayer(it, player, { showPlayer = true }, vm::togglePlayPause, vm::next)
                    }
                    IsaiBottomBar(tab, { tab = it })
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "isai-navigation"
            ) { destination ->
                when (destination) {
                    0 -> IsaiHome(songs, player, vm, favorites, padding) { showPlayer = true }
                    1 -> IsaiExplore(filtered, search, { search = it }, vm, favorites, padding)
                    2 -> IsaiLibrary(songs, vm, favorites, padding)
                    3 -> IsaiPlaylists(padding)
                    else -> IsaiSettings(padding)
                }
            }
        }

        if (showPlayer && player.currentSong != null) {
            IsaiNowPlaying(player, vm, { showPlayer = false }, favorites)
        }
    }
}

@Composable
private fun IsaiHome(songs: List<Song>, player: PlayerState, vm: IsaiViewModel, favorites: MutableList<Long>, padding: PaddingValues, openPlayer: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ISAI", color = IsaiGoldSoft, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Good evening", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                }
                IsaiLogoMark()
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = IsaiSurface, tonalElevation = 4.dp) {
                Box(Modifier.background(Brush.linearGradient(listOf(IsaiSurface2, Color(0xFF0B0A08))))) {
                    Column(Modifier.padding(22.dp)) {
                        Text("YOUR MUSIC", color = IsaiGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(if (songs.isEmpty()) "Start your library" else "Made for your ears", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(if (songs.isEmpty()) "Scan your device and let ISAI organize your music." else "${songs.size} songs ready to play", color = IsaiMuted)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = songs.isNotEmpty(),
                                onClick = vm::playAll,
                                colors = ButtonDefaults.buttonColors(containerColor = IsaiGold, contentColor = Color.Black),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Play all", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(enabled = player.currentSong != null, onClick = openPlayer, shape = RoundedCornerShape(16.dp)) {
                                Text("Now playing")
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Recently added", "View all") }
        if (songs.isEmpty()) {
            item { EmptyCard("No local music yet", "Allow audio access and refresh your library.") }
        } else {
            items(songs.take(8), key = { it.id }) { song ->
                IsaiSongRow(song, song.id in favorites, player.currentSong?.id == song.id, { favorites.toggle(song.id) }) { vm.play(song) }
            }
        }
    }
}

@Composable
private fun IsaiExplore(songs: List<Song>, search: String, onSearch: (String) -> Unit, vm: IsaiViewModel, favorites: MutableList<Long>, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text("Explore", Modifier.padding(start = 20.dp, top = 24.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Find something worth listening to.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = IsaiMuted)
        OutlinedTextField(
            value = search, onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            placeholder = { Text("Songs, artists or albums") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton({ onSearch("") }) { Icon(Icons.Filled.Close, null) } },
            singleLine = true, shape = RoundedCornerShape(18.dp)
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (songs.isEmpty()) item { EmptyCard("Nothing found", "Try a different title, artist or album.") }
            else items(songs, key = { it.id }) { song ->
                IsaiSongRow(song, song.id in favorites, false, { favorites.toggle(song.id) }) { vm.play(song) }
            }
        }
    }
}

@Composable
private fun IsaiLibrary(songs: List<Song>, vm: IsaiViewModel, favorites: MutableList<Long>, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text("Library", Modifier.padding(start = 20.dp, top = 24.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${songs.size} songs on this device", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = IsaiGold)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (songs.isEmpty()) item { EmptyCard("Your library is empty", "Music stored on this device will appear here.") }
            items(songs, key = { it.id }) { song ->
                IsaiSongRow(song, song.id in favorites, false, { favorites.toggle(song.id) }) { vm.play(song) }
            }
        }
    }
}

@Composable
private fun IsaiPlaylists(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Playlists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Curate your sound.", color = IsaiMuted)
        Spacer(Modifier.height(18.dp))
        EmptyCard("No playlists yet", "Create playlists from your library.")
    }
}

@Composable
private fun IsaiSettings(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        SettingsRow(Icons.Filled.Equalizer, "Equalizer", "Audio effects and presets")
        SettingsRow(Icons.Filled.Bedtime, "Sleep timer", "Stop playback automatically")
        SettingsRow(Icons.Filled.Analytics, "Listening analytics", "Your listening history")
        SettingsRow(Icons.Filled.Download, "Downloads", "Offline music")
        SettingsRow(Icons.Filled.Info, "About ISAI", "Open-source music player")
    }
}

@Composable
private fun IsaiBottomBar(tab: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        "Home" to Icons.Filled.Home,
        "Explore" to Icons.Filled.Explore,
        "Library" to Icons.Filled.LibraryMusic,
        "Playlists" to Icons.Filled.QueueMusic,
        "Settings" to Icons.Filled.Settings
    )
    NavigationBar(containerColor = IsaiSurface, tonalElevation = 0.dp) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = tab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.second, null) },
                label = { Text(item.first) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IsaiGold,
                    selectedTextColor = IsaiGold,
                    indicatorColor = IsaiGold.copy(alpha = .12f),
                    unselectedIconColor = IsaiMuted,
                    unselectedTextColor = IsaiMuted
                )
            )
        }
    }
}

@Composable
private fun IsaiSongRow(song: Song, favorite: Boolean, active: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (active) IsaiGold.copy(alpha = .10f) else IsaiSurface)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(song.artworkUri, null, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} · ${song.album}", color = IsaiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (favorite) IsaiGold else IsaiMuted)
        }
    }
}

@Composable
private fun IsaiMiniPlayer(song: Song, state: PlayerState, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(IsaiSurface2).clickable(onClick = onOpen)) {
        if (state.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) },
                Modifier.fillMaxWidth().height(2.dp), color = IsaiGold, trackColor = Color.Transparent
            )
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.artworkUri, null, Modifier.size(46.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = IsaiMuted, maxLines = 1)
            }
            IconButton(onClick = onPlayPause) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, null) }
        }
    }
}

@Composable
private fun IsaiNowPlaying(state: PlayerState, vm: IsaiViewModel, onDismiss: () -> Unit, favorites: MutableList<Long>) {
    val song = state.currentSong ?: return
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = IsaiBlack) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.KeyboardArrowDown, "Close") }
                    Text("NOW PLAYING", Modifier.weight(1f), textAlign = TextAlign.Center, color = IsaiGold, fontWeight = FontWeight.Bold)
                    IconButton(onClick = {}) { Icon(Icons.Filled.MoreHoriz, "More") }
                }
                Spacer(Modifier.height(24.dp))
                AsyncImage(song.artworkUri, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = IsaiMuted)
                    }
                    IconButton(onClick = { favorites.toggle(song.id) }) {
                        Icon(if (song.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = IsaiGold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Slider(
                    value = state.positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(1L)).toFloat(),
                    onValueChange = { vm.seekTo(it.toLong()) },
                    valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = IsaiGold, activeTrackColor = IsaiGold)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.positionMs), color = IsaiMuted)
                    Text(formatTime(state.durationMs), color = IsaiMuted)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffle) IsaiGold else IsaiText) }
                    IconButton(onClick = vm::previous) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                    FilledIconButton(
                        onClick = vm::togglePlayPause, modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = IsaiGold, contentColor = Color.Black)
                    ) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(36.dp)) }
                    IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, "Next") }
                    IconButton(onClick = vm::cycleRepeat) {
                        Icon(if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", tint = if (state.repeatMode == RepeatMode.OFF) IsaiText else IsaiGold)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PlayerTool(Icons.Filled.Lyrics, "Lyrics")
                    PlayerTool(Icons.Filled.Bedtime, "Sleep")
                    PlayerTool(Icons.Filled.Equalizer, "Effects")
                    PlayerTool(Icons.Filled.QueueMusic, "Queue")
                }
                Spacer(Modifier.height(18.dp))
                Text("UP NEXT", color = IsaiGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.queue, key = { it.id }) { item ->
                        IsaiSongRow(item, false, item.id == song.id, {}, { vm.play(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTool(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = IsaiSurface2) { Icon(icon, null, Modifier.padding(12.dp), tint = IsaiGold) }
        Spacer(Modifier.height(5.dp))
        Text(label, color = IsaiMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionHeader(title: String, action: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(action, color = IsaiGold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = IsaiSurface) {
        Column(Modifier.padding(22.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = IsaiMuted)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = IsaiGold.copy(alpha = .12f)) { Icon(icon, null, Modifier.padding(11.dp), tint = IsaiGold) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = IsaiMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = IsaiMuted)
    }
}

@Composable
private fun IsaiLogoMark() {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = IsaiGold.copy(alpha = .10f),
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("இ", color = IsaiGoldSoft, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun MutableList<Long>.toggle(id: Long) { if (contains(id)) remove(id) else add(id) }

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}
