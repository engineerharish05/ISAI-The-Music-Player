package com.rishinnovations.isai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Gold = Color(0xFFC6A15B)
private val Black = Color(0xFF0A0A09)
private val Surface = Color(0xFF171714)

@Composable
fun IsaiApp(vm: IsaiViewModel = viewModel()) {
    val songs by vm.songs.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.connect()
        vm.scan()
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Black, surface = Surface)) {
        Scaffold(
            containerColor = Black,
            bottomBar = {
                NavigationBar(containerColor = Surface) {
                    val tabs = listOf(
                        "Home" to Icons.Filled.Home,
                        "Explore" to Icons.Filled.Search,
                        "Library" to Icons.Filled.LibraryMusic,
                        "Playlists" to Icons.Filled.MoreHoriz
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
        ) { padding ->
            when (tab) {
                0 -> Home(songs, vm, padding)
                1 -> Explore(songs, vm, padding)
                2 -> Library(songs, vm, padding)
                else -> EmptyPage("Playlists", padding)
            }
        }
    }
}

@Composable
private fun Home(songs: List<Song>, vm: IsaiViewModel, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text("ISAI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Gold)
        Text("THE MUSIC PLAYER", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Your music.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (songs.isEmpty()) "Scan your device for local music."
                    else "${songs.size} local songs ready to play.",
                    color = Color.LightGray
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    enabled = songs.isNotEmpty(),
                    onClick = { songs.firstOrNull()?.let(vm::play) }
                ) {
                    Text("Listen now")
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("Recently available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        if (songs.isEmpty()) {
            EmptyState("No local music found")
        } else {
            SongList(songs.take(8), vm)
        }
    }
}

@Composable
private fun Explore(songs: List<Song>, vm: IsaiViewModel, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Explore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Online search will be added here") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )
        Spacer(Modifier.height(20.dp))
        Text("On device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        SongList(songs, vm)
    }
}

@Composable
private fun Library(songs: List<Song>, vm: IsaiViewModel, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${songs.size} songs", color = Gold)
        Spacer(Modifier.height(12.dp))
        SongList(songs, vm)
    }
}

@Composable
private fun SongList(songs: List<Song>, vm: IsaiViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(songs, key = { it.id }) { song ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(18.dp))
                    .clickable { vm.play(song) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).background(Gold.copy(alpha = .18f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Gold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${song.artist} · ${song.album}",
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Filled.FavoriteBorder, contentDescription = "Favorite", tint = Gold)
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.LightGray)
    }
}

@Composable
private fun EmptyPage(title: String, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
