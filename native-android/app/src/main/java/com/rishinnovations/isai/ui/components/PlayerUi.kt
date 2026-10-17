@file:OptIn(ExperimentalMaterial3Api::class)

package com.rishinnovations.isai.ui.components

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rishinnovations.isai.AppMessages
import com.rishinnovations.isai.container
import com.rishinnovations.isai.data.library.activeIndex
import com.rishinnovations.isai.logic.Lyrics
import com.rishinnovations.isai.logic.SleepTimerMachine
import com.rishinnovations.isai.logic.formatDuration
import com.rishinnovations.isai.model.RepeatMode
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.playback.PlaybackBridge
import com.rishinnovations.isai.playback.SleepTimerController
import com.rishinnovations.isai.ui.PlayerUiState
import com.rishinnovations.isai.ui.PlayerViewModel
import com.rishinnovations.isai.ui.theme.LocalReduceMotion
import com.rishinnovations.isai.ui.theme.isai
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private fun Modifier.swipeToSkip(onNext: () -> Unit, onPrevious: () -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total > 120f) onPrevious() else if (total < -120f) onNext()
        },
        onHorizontalDrag = { _, delta -> total += delta }
    )
}

/** Persistent mini-player. Tap or swipe up to expand, swipe sideways to skip; buttons offer the same actions. */
@Composable
fun MiniPlayer(state: PlayerUiState, position: StateFlow<Long>, vm: PlayerViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val positionMs by position.collectAsStateWithLifecycle()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -50f) onOpen() }, onVerticalDrag = { _, d -> total += d })
            }
            .swipeToSkip(vm::next, vm::previous),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Open player", onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(state.song, Modifier.size(44.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = vm::togglePlayPause) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (state.isPlaying) "Pause" else "Play")
                }
                IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, contentDescription = "Next track") }
            }
            val fraction = if (state.durationMs > 0) (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.isai.gold,
                trackColor = Color.Transparent,
                drawStopIndicator = {}
            )
        }
    }
}

/** Audio-reactive bars driven by the real PCM stream. Falls back to a still baseline when idle. */
@Composable
fun VisualizerBars(active: Boolean, modifier: Modifier = Modifier) {
    DisposableEffect(active) {
        PlaybackBridge.spectrumEnabled.set(active)
        onDispose { PlaybackBridge.spectrumEnabled.set(false) }
    }
    val levels by PlaybackBridge.spectrum.collectAsStateWithLifecycle()
    val gold = MaterialTheme.isai.gold
    val lavender = MaterialTheme.isai.lavender
    Canvas(modifier) {
        val count = levels.size
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val level = if (active) levels[i] else 0f
            val h = (size.height * level).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(lavender, gold), startY = size.height - h, endY = size.height),
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

@Composable
fun FullPlayer(
    state: PlayerUiState,
    position: StateFlow<Long>,
    vm: PlayerViewModel,
    favorites: Set<Long>,
    visualizerEnabled: Boolean,
    onCollapse: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenMenu: (Song) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit
) {
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val song = state.song

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Selective glass: a blurred copy of the artwork sits behind translucent panels (API 31+ only).
        if (Build.VERSION.SDK_INT >= 31 && !reduceMotion) {
            Artwork(song, Modifier.fillMaxSize().blur(70.dp).alpha(0.55f), RectangleShape)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.55f), MaterialTheme.colorScheme.background.copy(alpha = 0.92f)))))

        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val artSize = minOf(maxWidth - 48.dp, maxHeight * 0.38f).coerceAtLeast(160.dp)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close player") }
                    Text("NOW PLAYING", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.isai.gold)
                    IconButton(onClick = { song?.let(onOpenMenu) }, enabled = song != null) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                }
                Spacer(Modifier.height(8.dp))

                if (showLyrics) {
                    Surface(
                        Modifier.fillMaxWidth().height(artSize),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.isai.glass,
                        border = BorderStroke(1.dp, MaterialTheme.isai.glassBorder)
                    ) { LyricsPanel(song, position, onSeek = vm::seekTo) }
                } else {
                    Artwork(song, Modifier.size(artSize).swipeToSkip(vm::next, vm::previous), RoundedCornerShape(24.dp))
                }
                Spacer(Modifier.height(16.dp))

                if (visualizerEnabled && !reduceMotion && !showLyrics) {
                    VisualizerBars(active = state.isPlaying, modifier = Modifier.fillMaxWidth().height(28.dp))
                    Spacer(Modifier.height(10.dp))
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(state.artist, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val isFavorite = song != null && song.id in favorites
                    IconButton(onClick = { song?.let(onToggleFavorite) }, enabled = song != null) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) MaterialTheme.isai.gold else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                SeekBar(state.durationMs, position, vm::seekTo)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::toggleShuffle) {
                        Icon(
                            Icons.Filled.Shuffle, contentDescription = if (state.shuffle) "Shuffle on" else "Shuffle off",
                            tint = if (state.shuffle) MaterialTheme.isai.gold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(36.dp)) }
                    FilledIconButton(
                        onClick = vm::togglePlayPause, modifier = Modifier.size(72.dp), shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.isai.gold, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        if (state.isBuffering && !state.isPlaying) CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (state.isPlaying) "Pause" else "Play", modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, contentDescription = "Next track", modifier = Modifier.size(36.dp)) }
                    IconButton(onClick = vm::cycleRepeat) {
                        Icon(
                            if (state.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = when (state.repeat) { RepeatMode.OFF -> "Repeat off"; RepeatMode.ALL -> "Repeat all"; RepeatMode.ONE -> "Repeat one" },
                            tint = if (state.repeat == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.isai.gold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.isai.glass, border = BorderStroke(1.dp, MaterialTheme.isai.glassBorder)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { showLyrics = !showLyrics }) {
                            Icon(Icons.Filled.Lyrics, contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics", tint = if (showLyrics) MaterialTheme.isai.gold else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Open queue") }
                        IconButton(onClick = onOpenEqualizer) { Icon(Icons.Filled.Equalizer, contentDescription = "Equalizer and audio effects") }
                        IconButton(onClick = onOpenSleepTimer) { Icon(Icons.Filled.Bedtime, contentDescription = "Sleep timer") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showQueue) QueueSheet(state, vm, onDismiss = { showQueue = false })
}

@Composable
private fun SeekBar(durationMs: Long, position: StateFlow<Long>, onSeek: (Long) -> Unit) {
    val positionMs by position.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else positionMs.coerceIn(0L, safeDuration).toFloat()
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false },
            valueRange = 0f..safeDuration.toFloat(),
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.isai.gold, activeTrackColor = MaterialTheme.isai.gold),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shown.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QueueSheet(state: PlayerUiState, vm: PlayerViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (state.currentIndex > 0) listState.scrollToItem((state.currentIndex - 1).coerceAtLeast(0)) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.titleLarge)
                Text("${state.queue.size} songs" + if (state.shuffle) " · shuffle is on" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.queue.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
        }
        if (state.queue.isEmpty()) {
            Text("The queue is empty. Play a song or add songs from any list.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            itemsIndexed(state.queue, key = { index, entry -> "${entry.mediaId}-$index" }) { index, entry ->
                val active = index == state.currentIndex
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (active) MaterialTheme.isai.goldSoft else Color.Transparent)
                        .clickable { vm.playQueueIndex(index) }
                        .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(entry.song, Modifier.size(44.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) MaterialTheme.isai.gold else MaterialTheme.colorScheme.onSurface)
                        Text(if (entry.song == null) "No longer available" else entry.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.moveQueueItem(index, index - 1) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move ${entry.title} up") }
                    IconButton(onClick = { vm.moveQueueItem(index, index + 1) }, enabled = index < state.queue.lastIndex) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move ${entry.title} down") }
                    IconButton(onClick = { vm.removeFromQueue(index) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${entry.title} from queue") }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog("Clear queue?", "This stops playback and removes every song from the queue.", "Clear", onConfirm = { vm.clearQueue(); onDismiss() }, onDismiss = { confirmClear = false })
    }
}

private sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
}

@Composable
fun LyricsPanel(song: Song?, position: StateFlow<Long>, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<LyricsState>(LyricsState.Loading, song?.id, reload) {
        value = LyricsState.Loading
        value = song?.let { container.lyrics.load(it) }?.let { LyricsState.Loaded(it) } ?: LyricsState.None
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && song != null) scope.launch {
            if (container.lyrics.attach(song, uri)) reload++ else AppMessages.post("That file doesn't contain readable lyrics")
        }
    }

    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        when (val s = state) {
            LyricsState.Loading -> CircularProgressIndicator(color = MaterialTheme.isai.gold)
            LyricsState.None -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No lyrics available", style = MaterialTheme.typography.titleMedium)
                Text(
                    "ISAI looks for lyrics embedded in the file (MP3 and FLAC). You can also attach a .lrc or .txt file for this song.",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = song != null) { Text("Add lyrics file") }
            }
            is LyricsState.Loaded -> {
                val lyrics = s.lyrics
                val listState = rememberLazyListState()
                val active by remember(lyrics) { derivedStateOf { lyrics.activeIndex(position.value) } }
                LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isActive = index == active
                            Text(
                                line.text,
                                modifier = Modifier.fillMaxWidth().clickable(enabled = line.timeMs != null) { line.timeMs?.let(onSeek) },
                                style = if (isActive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) MaterialTheme.isai.gold else MaterialTheme.colorScheme.onSurface.copy(alpha = if (lyrics.synced) 0.55f else 0.9f)
                            )
                        }
                    }
                    Text(
                        (if (lyrics.synced) "Synced · " else "Not synced · ") + lyrics.source,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                    )
                    if (song != null && container.lyrics.hasAttached(song)) {
                        TextButton(onClick = { container.lyrics.removeAttached(song); reload++ }) { Text("Remove attached lyrics file") }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepTimerDialog(timer: SleepTimerController, onDismiss: () -> Unit) {
    val state by timer.state.collectAsStateWithLifecycle()
    val remaining by timer.remainingMs.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (val s = state) {
                    is SleepTimerMachine.State.Running, is SleepTimerMachine.State.Paused -> {
                        Text(formatDuration(remaining), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.isai.gold)
                        Text(if (s is SleepTimerMachine.State.Paused) "Countdown paused" else "Playback will pause when the timer ends.", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            if (s is SleepTimerMachine.State.Paused) Button(onClick = timer::resume) { Text("Resume") }
                            else OutlinedButton(onClick = timer::pause) { Text("Pause countdown") }
                            OutlinedButton(onClick = { timer.cancel(); onDismiss() }) { Text("Cancel timer") }
                        }
                    }
                    SleepTimerMachine.State.EndOfTrack -> {
                        Text("Playback will stop when the current track ends.")
                        OutlinedButton(onClick = { timer.cancel(); onDismiss() }) { Text("Cancel timer") }
                    }
                    SleepTimerMachine.State.Off -> {
                        listOf(5, 10, 15, 30, 45, 60, 90).forEach { minutes ->
                            TextButton(onClick = { timer.start(minutes * 60_000L); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                                Text("$minutes minutes", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                        }
                        TextButton(onClick = { timer.startEndOfTrack(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                            Text("End of current track", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
