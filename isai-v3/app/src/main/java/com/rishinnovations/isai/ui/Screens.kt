package com.rishinnovations.isai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.rishinnovations.isai.data.MusicTrack
import com.rishinnovations.isai.ui.theme.Gold

@Composable
fun HomeScreen(tracks: List<MusicTrack>, modifier: Modifier = Modifier, onPlay: (MusicTrack) -> Unit) {
    LazyColumn(modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 30.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Text("Good evening", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("What do you want to hear?", style = MaterialTheme.typography.headlineMedium)
        }
        item { HeroCard() }
        item { SectionTitle("Recently played") }
        if (tracks.isEmpty()) item { EmptyCard("Your local music will appear here after permission is granted.") }
        else item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tracks.take(8), key = { it.id }) { MusicCard(it, onPlay) }
            }
        }
        item { SectionTitle("Quick picks") }
        items(tracks.take(12), key = { it.id }) { TrackRow(it, onPlay) }
    }
}

@Composable private fun HeroCard() {
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp)).background(
        Brush.linearGradient(listOf(Gold.copy(alpha = .32f), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background))
    ).padding(24.dp)) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("ISAI", color = Gold, style = MaterialTheme.typography.labelLarge)
            Text("Your music.\nYour space.", style = MaterialTheme.typography.headlineLarge)
            Text("Local-first listening, built for Android.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun MusicCard(track: MusicTrack, onPlay: (MusicTrack) -> Unit) {
    Column(Modifier.width(142.dp)) {
        ArtworkTile(142.dp)
        Spacer(Modifier.height(8.dp))
        Text(track.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
        Text(track.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FilledTonalIconButton(onClick = { onPlay(track) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.PlayArrow, "Play") }
    }
}

@Composable
fun SearchScreen(tracks: List<MusicTrack>, modifier: Modifier = Modifier, onPlay: (MusicTrack) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(tracks, query) {
        if (query.isBlank()) tracks else tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }
    Column(modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Songs, artists, albums") }, shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) { items(results, key = { it.id }) { TrackRow(it, onPlay) } }
    }
}

@Composable
fun LibraryScreen(tracks: List<MusicTrack>, modifier: Modifier = Modifier, onPlay: (MusicTrack) -> Unit) {
    LazyColumn(modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 28.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text("Library", style = MaterialTheme.typography.headlineMedium)
            Text("\${tracks.size} songs on this device", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(true, {}, label = { Text("Songs") })
                FilterChip(false, {}, label = { Text("Albums") })
                FilterChip(false, {}, label = { Text("Artists") })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { _, track -> TrackRow(track, onPlay) }
    }
}

@Composable private fun TrackRow(track: MusicTrack, onPlay: (MusicTrack) -> Unit) {
    Surface(onClick = { onPlay(track) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .58f)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkTile(52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                Text(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • "), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onPlay(track) }) { Icon(Icons.Rounded.PlayArrow, "Play") }
            IconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, "More") }
        }
    }
}

@Composable private fun ArtworkTile(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(
        Brush.linearGradient(listOf(Gold.copy(alpha=.82f), MaterialTheme.colorScheme.surfaceVariant))
    )) { Text("♪", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.background) }
}

@Composable private fun SectionTitle(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        Text("See all", color = Gold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable private fun EmptyCard(message: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f)) {
        Text(message, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
