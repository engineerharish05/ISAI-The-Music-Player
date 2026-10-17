package com.rishinnovations.isai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rishinnovations.isai.data.online.OnlineResult
import com.rishinnovations.isai.data.online.OnlineTrack
import com.rishinnovations.isai.logic.formatDuration
import com.rishinnovations.isai.ui.LocalActions
import com.rishinnovations.isai.ui.components.EmptyState
import androidx.compose.ui.text.input.ImeAction

private sealed interface OnlineUi {
    data object Idle : OnlineUi
    data object Loading : OnlineUi
    data class Done(val tracks: List<OnlineTrack>) : OnlineUi
    data class Error(val message: String) : OnlineUi
}

@Composable
fun OnlineScreen() {
    val a = LocalActions.current
    val settings by a.app.settings.collectAsStateWithLifecycle()
    val downloads by a.app.downloads.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var ui by remember { mutableStateOf<OnlineUi>(OnlineUi.Idle) }

    LaunchedEffect(submitted, settings.onlineEnabled) {
        if (!settings.onlineEnabled || submitted.isBlank()) return@LaunchedEffect
        ui = OnlineUi.Loading
        ui = when (val r = a.app.searchOnline(submitted)) {
            is OnlineResult.Success -> OnlineUi.Done(r.tracks)
            is OnlineResult.Failure -> OnlineUi.Error(r.message)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenBar("Online music", onBack = { a.nav.pop() })
        if (!settings.onlineEnabled) {
            EmptyState(
                Icons.Filled.CloudOff, "Online sources are off",
                "ISAI works fully offline. If you want, it can search free Creative Commons and public-domain music on archive.org (and Jamendo when configured). Nothing is contacted until you turn this on and search.",
                actionLabel = "Enable online sources", onAction = { a.app.setOnlineEnabled(true) }
            )
            return@Column
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text("Search free music") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() })
        )
        Text(
            "Open catalogues only: archive.org${if (a.app.container.online.jamendoConfigured) " and Jamendo" else ""}. Each track shows its license.",
            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (val state = ui) {
            OnlineUi.Idle -> EmptyState(Icons.Filled.Search, "Search free music", "Try an artist, an album, or a mood. Results are streamed, and you can save tracks to your library.")
            OnlineUi.Loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.padding(32.dp)); CircularProgressIndicator()
            }
            is OnlineUi.Error -> EmptyState(Icons.Filled.CloudOff, "Search failed", state.message, actionLabel = "Try again", onAction = { submitted = ""; submitted = query.trim() })
            is OnlineUi.Done -> if (state.tracks.isEmpty()) {
                EmptyState(Icons.Filled.Search, "No results", "Nothing openly licensed matched “$submitted”.")
            } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(state.tracks, key = { _, t -> t.mediaId }) { index, track ->
                    Row(
                        Modifier.fillMaxWidth().clickable { a.playOnline(state.tracks, index) }.padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${track.artist} · ${track.license}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (track.durationMs > 0) Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                        val progress = downloads[track.mediaId]
                        if (progress != null) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.width(24.dp).padding(end = 0.dp))
                            Spacer(Modifier.width(12.dp))
                        } else {
                            IconButton(onClick = { a.app.download(track) }) { Icon(Icons.Filled.Download, contentDescription = "Save ${track.title} to your library") }
                        }
                    }
                }
            }
        }
    }
}
