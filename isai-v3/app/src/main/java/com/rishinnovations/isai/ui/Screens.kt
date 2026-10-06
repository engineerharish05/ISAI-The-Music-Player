package com.rishinnovations.isai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rishinnovations.isai.data.MusicTrack
import com.rishinnovations.isai.ui.theme.Gold

@Composable
fun HomeScreen(
    tracks: List<MusicTrack>,
    modifier: Modifier = Modifier,
    onPlay: (MusicTrack) -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Good evening", style = MaterialTheme.typography.headlineMedium)
        Text("ISAI", style = MaterialTheme.typography.displaySmall, color = Gold)
        Text(
            if (tracks.isEmpty()) "No local music found yet." else "${tracks.size} local tracks found.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Quick picks", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tracks.take(10), key = { it.id }) { track ->
                Button(onClick = { onPlay(track) }) { Text(track.title.take(18)) }
            }
        }
    }
}

@Composable
fun SearchScreen(
    tracks: List<MusicTrack>,
    modifier: Modifier = Modifier,
    onPlay: (MusicTrack) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(tracks, query) {
        if (query.isBlank()) tracks else tracks.filter {
            it.title.contains(query, true) ||
                it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
    }

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            singleLine = true,
            placeholder = { Text("Songs, artists, albums") }
        )
        LazyColumn(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(results, key = { it.id }) { track -> TrackRow(track, onPlay) }
        }
    }
}

@Composable
fun LibraryScreen(
    tracks: List<MusicTrack>,
    modifier: Modifier = Modifier,
    onPlay: (MusicTrack) -> Unit
) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${tracks.size} songs",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tracks, key = { it.id }) { track -> TrackRow(track, onPlay) }
        }
    }
}

@Composable
private fun TrackRow(track: MusicTrack, onPlay: (MusicTrack) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1)
            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        TextButton(onClick = { onPlay(track) }) { Text("Play") }
    }
}
