package com.rishinnovations.isai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rishinnovations.isai.logic.formatListeningTime
import com.rishinnovations.isai.model.ThemeMode
import com.rishinnovations.isai.playback.PlaybackBridge
import com.rishinnovations.isai.ui.LocalActions
import com.rishinnovations.isai.ui.Route
import com.rishinnovations.isai.ui.components.BoldLabel
import com.rishinnovations.isai.ui.components.ConfirmDialog
import com.rishinnovations.isai.ui.components.EmblemTile
import com.rishinnovations.isai.ui.components.EmptyState
import com.rishinnovations.isai.ui.components.ScreenTitle
import com.rishinnovations.isai.ui.theme.isai
import androidx.compose.material.icons.filled.GraphicEq
import java.util.Locale

const val REPO_URL = "https://github.com/engineerharish05/isai-the-music-player"

private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        BoldLabel(title, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        content()
    }
}

@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(title: String, description: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen() {
    val a = LocalActions.current
    val context = LocalContext.current
    val settings by a.app.settings.collectAsStateWithLifecycle()
    val library by a.app.library.collectAsStateWithLifecycle()
    val folders by a.app.importedFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val permission by a.app.permissionGranted.collectAsStateWithLifecycle()
    val busy by a.app.importBusy.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf(a.app.artworkCacheBytes()) }
    var cacheCleared by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenTitle("Settings")

        Group("Appearance") {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = settings.themeMode == mode, onClick = { a.app.setTheme(mode) }, label = { Text(mode.label) })
                }
            }
            SwitchRow("Visualizer", "Animated spectrum on the player, driven by the audio that is actually playing.", settings.visualizerEnabled, a.app::setVisualizer)
            SwitchRow("Reduce motion", "Turns off animations such as the visualizer and screen transitions.", settings.reduceMotion, a.app::setReduceMotion)
        }

        Group("Playback") {
            Text("Playback speed", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed ->
                    FilterChip(
                        selected = settings.playbackSpeed == speed, onClick = { a.app.setSpeed(speed) },
                        label = { Text(if (speed == 1f) "Normal" else String.format(Locale.US, "%.2g×", speed)) }
                    )
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    if (settings.fadeSeconds == 0) "Fade between tracks: off" else "Fade between tracks: ${settings.fadeSeconds} s",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Fades the end of a song out and the next one in. Songs do not overlap.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var value by remember(settings.fadeSeconds) { mutableStateOf(settings.fadeSeconds.toFloat()) }
                Slider(
                    value = value, onValueChange = { value = it }, valueRange = 0f..12f, steps = 11,
                    onValueChangeFinished = { a.app.setFadeSeconds(value.toInt()) },
                    modifier = Modifier.semantics { contentDescription = "Fade between tracks in seconds" }
                )
            }
            SwitchRow("Shuffle new queues", "Start every new list in shuffled order.", settings.shuffleNewQueues, a.app::setShuffleNewQueues)
            SwitchRow("Fade out for sleep timer", "Lower the volume gently before the sleep timer stops playback.", settings.sleepFadeOut, a.app::setSleepFadeOut)
            LinkRow("Sleep timer", "Stop playback after a set time or at the end of the song", onClick = a.showSleepTimer)
            LinkRow("Equalizer and effects", "Uses your device's audio effects", onClick = { a.nav.push(Route.Equalizer) })
        }

        Group("Library") {
            Text(
                if (!permission) "Device music access is off. You can still add files and folders yourself."
                else "${library.songs.size} songs available",
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!permission) Button(onClick = a.requestMusicPermission) { Text("Allow access") }
                OutlinedButton(onClick = a.openAddMusic) { Text("Add music") }
                OutlinedButton(onClick = a.app::refreshLibrary) { Text("Rescan") }
            }
            if (busy != null) Text(busy ?: "", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.isai.gold)
            SwitchRow("Include non-music audio", "Show ringtones, notifications, alarms and recordings that Android marks as non-music.", settings.includeNonMusic, a.app::setIncludeNonMusic)
            if (folders.isNotEmpty()) {
                Text("Added folders", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                folders.forEach { folder ->
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { a.app.removeFolder(folder.treeUri) }) { Icon(Icons.Filled.Close, contentDescription = "Remove folder ${folder.name}") }
                    }
                }
                Text("Removing a folder only removes it from ISAI. Your files are untouched.", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Group("Online music (optional)") {
            SwitchRow(
                "Enable online sources",
                "Off by default. When on, ISAI contacts archive.org (and Jamendo, if configured) only when you search or play online music. Local playback never uses the network.",
                settings.onlineEnabled, a.app::setOnlineEnabled
            )
            if (settings.onlineEnabled) LinkRow("Find free music online", "Creative Commons and public-domain catalogues", onClick = { a.nav.push(Route.Online) })
        }

        Group("Your data") {
            LinkRow("Listening statistics", "Based on what you actually listened to", onClick = { a.nav.push(Route.Stats) })
            LinkRow("Clear listening history", "Removes statistics and recommendations based on them. Favorites and playlists are kept.", onClick = { confirmClear = true })
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Artwork cache", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (cacheCleared) "Cleared" else String.format(Locale.US, "%.1f MB used", cacheBytes / 1_048_576.0),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { a.app.clearArtworkCache(); cacheBytes = 0; cacheCleared = true }) { Text("Clear") }
            }
        }

        Group("About") {
            LinkRow("About ISAI", onClick = { a.nav.push(Route.About) })
            LinkRow("Privacy", "Nothing leaves your device", onClick = { a.nav.push(Route.Privacy) })
            LinkRow("Open-source licenses", onClick = { a.nav.push(Route.Licenses) })
            LinkRow("Send feedback or report a problem", "Opens GitHub in your browser", onClick = { openLink(context, "$REPO_URL/issues") })
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            "Clear listening history?", "Play counts, recently played and listening time are erased. This cannot be undone.", "Clear",
            onConfirm = { a.app.clearHistory() }, onDismiss = { confirmClear = false }
        )
    }
}

@Composable
fun EqualizerScreen() {
    val a = LocalActions.current
    val settings by a.app.settings.collectAsStateWithLifecycle()
    val info by PlaybackBridge.effectsInfo.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SubScreenBar("Equalizer and effects", onBack = { a.nav.pop() })
        if (!info.sessionActive) {
            EmptyState(
                Icons.Filled.GraphicEq, "Start a song first",
                "Android attaches audio effects to an active playback session. Play any song, then come back to adjust them."
            )
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (info.eqSupported) {
                SwitchRow("Equalizer", "${info.bandCount} bands provided by your device", settings.eqEnabled, a.app::setEqEnabled)
                if (info.presets.isNotEmpty()) {
                    var menu by remember { mutableStateOf(false) }
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        OutlinedButton(onClick = { menu = true }) {
                            Text(info.presets.getOrNull(settings.eqPreset) ?: "Custom")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            info.presets.forEachIndexed { index, name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { a.app.setEqPreset(index); menu = false })
                            }
                        }
                    }
                }
                val levels = remember(info.bandCount, settings.eqPreset, settings.eqLevels) {
                    mutableStateListOf<Float>().apply {
                        repeat(info.bandCount) { add((settings.eqLevels.getOrNull(it) ?: 0).toFloat()) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                for (band in 0 until info.bandCount) {
                    val hz = info.centerFreqsHz.getOrNull(band) ?: 0
                    val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Row {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(String.format(Locale.US, "%+.1f dB", levels[band] / 100f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.isai.gold)
                        }
                        Slider(
                            value = levels[band], onValueChange = { levels[band] = it },
                            valueRange = info.minLevelMb.toFloat()..info.maxLevelMb.toFloat(),
                            onValueChangeFinished = { a.app.setEqLevels(levels.map { it.toInt() }) },
                            enabled = settings.eqEnabled,
                            modifier = Modifier.semantics { contentDescription = "$label gain" }
                        )
                    }
                }
            } else {
                Text("This device does not offer an equalizer.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (info.bassSupported) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Bass boost: ${settings.bassStrength / 10}%", style = MaterialTheme.typography.bodyLarge)
                    var v by remember(settings.bassStrength) { mutableStateOf(settings.bassStrength.toFloat()) }
                    Slider(value = v, onValueChange = { v = it }, valueRange = 0f..1000f, onValueChangeFinished = { a.app.setBass(v.toInt()) })
                }
            }
            if (info.loudnessSupported) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Loudness boost: +${settings.loudnessGainMb / 100f} dB", style = MaterialTheme.typography.bodyLarge)
                    var v by remember(settings.loudnessGainMb) { mutableStateOf(settings.loudnessGainMb.toFloat()) }
                    Slider(value = v, onValueChange = { v = it }, valueRange = 0f..1000f, onValueChangeFinished = { a.app.setLoudness(v.toInt()) })
                    Text("Raising loudness can cause distortion on loud songs. Lower it if you hear crackling.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = { a.app.resetEffects(info.bandCount) }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { Text("Reset all effects") }
        }
    }
}

@Composable
fun StatsScreen() {
    val a = LocalActions.current
    val library by a.app.library.collectAsStateWithLifecycle()
    val stats by a.app.stats.collectAsStateWithLifecycle()
    val total by a.app.totalListenedMs.collectAsStateWithLifecycle()
    val played = remember(stats, library) { a.app.mostPlayed(library, stats) }
    val totalPlays = stats.values.sumOf { it.plays }
    val topArtists = remember(stats, library) {
        stats.values.mapNotNull { s -> library.byId[s.songId]?.let { it.artist to s } }
            .groupBy({ it.first }, { it.second })
            .map { (artist, list) -> Triple(artist, list.sumOf { it.plays }, list.sumOf { it.listenedMs }) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenBar("Listening statistics", onBack = { a.nav.pop() })
        if (total <= 0L && totalPlays == 0) {
            EmptyState(
                Icons.Filled.GraphicEq, "No listening history yet",
                "Statistics are built from real listening on this device. A song counts as played after about 30 seconds, or half of a short track."
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(formatListeningTime(total), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.isai.gold)
                Text("total listening time · $totalPlays plays", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                BoldLabel("Top songs")
            }
            items(played.take(10).size) { i ->
                val song = played[i]
                val s = stats[song.id]
                Row(Modifier.fillMaxWidth().clickable { a.play(played.take(10), i) }.padding(vertical = 6.dp)) {
                    Text("${i + 1}", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${s?.plays ?: 0} plays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (topArtists.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    BoldLabel("Top artists")
                }
                items(topArtists.size) { i ->
                    val (artist, plays, ms) = topArtists[i]
                    Row(Modifier.fillMaxWidth().clickable { a.nav.push(Route.Artist(artist)) }.padding(vertical = 6.dp)) {
                        Text("${i + 1}", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(artist, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text("$plays plays · ${formatListeningTime(ms)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val a = LocalActions.current
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubScreenBar("About", onBack = { a.nav.pop() })
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EmblemTile(Modifier.size(140.dp))
            Spacer(Modifier.height(16.dp))
            Text("ISAI — The Music Experience", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Your Day, Your moments, Your Isai", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.isai.gold)
            Spacer(Modifier.height(12.dp))
            Text(
                "A Refined Music Experience Designed Around The Songs You Love.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text("Version $version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Text(
            "ISAI plays the music stored on your device. It has no accounts, no ads and no internet access, and it does not stream or download music.",
            Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium
        )
        LinkRow("Source code on GitHub", REPO_URL, onClick = { openLink(context, REPO_URL) })
        LinkRow("Open-source licenses", onClick = { a.nav.push(Route.Licenses) })
        LinkRow("Privacy", onClick = { a.nav.push(Route.Privacy) })
    }
}

@Composable
fun PrivacyScreen() {
    val a = LocalActions.current
    val points = listOf(
        "ISAI has no INTERNET permission. It cannot send anything off your device, even by accident.",
        "No ads, analytics, tracking or accounts.",
        "Your library comes from Android's media index and from files and folders you choose yourself.",
        "Favorites, playlists and listening statistics are stored only on this device, in the app's private storage, and are removed when you uninstall.",
        "Listening statistics can be cleared at any time in Settings.",
        "Backups are disabled, so this data is not copied to cloud backups.",
        "Opening the GitHub feedback link uses your browser, not ISAI."
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubScreenBar("Privacy", onBack = { a.nav.pop() })
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            points.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private val licenses = listOf(
    "AndroidX (Core, Activity, Lifecycle, Compose, Room, DataStore, Media3)" to "Apache License 2.0 — The Android Open Source Project",
    "Kotlin and kotlinx.coroutines" to "Apache License 2.0 — JetBrains",
    "Coil" to "Apache License 2.0 — Coil Contributors",
    "Guava" to "Apache License 2.0 — Google",
    "Material Icons" to "Apache License 2.0 — Google",
    "ISAI app code" to "See LICENSE in the repository. The ISAI emblem and name are covered by LICENSE-ISAI.md."
)

@Composable
fun LicensesScreen() {
    val a = LocalActions.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubScreenBar("Open-source licenses", onBack = { a.nav.pop() })
        licenses.forEach { (name, license) ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinkRow("Full license texts and dependency list", "$REPO_URL/blob/main/docs/DEPENDENCIES.md", onClick = { openLink(context, "$REPO_URL/blob/main/docs/DEPENDENCIES.md") })
    }
}
