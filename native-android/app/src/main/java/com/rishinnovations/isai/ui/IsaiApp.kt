@file:OptIn(ExperimentalMaterial3Api::class)

package com.rishinnovations.isai.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rishinnovations.isai.AppMessages
import com.rishinnovations.isai.container
import com.rishinnovations.isai.logic.formatDuration
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.ui.components.Artwork
import com.rishinnovations.isai.ui.components.FullPlayer
import com.rishinnovations.isai.ui.components.MiniPlayer
import com.rishinnovations.isai.ui.components.ProvideRowState
import com.rishinnovations.isai.ui.components.SleepTimerDialog
import com.rishinnovations.isai.ui.components.SongRowActions
import com.rishinnovations.isai.ui.components.TextInputDialog
import com.rishinnovations.isai.ui.screens.AboutScreen
import com.rishinnovations.isai.ui.screens.CollectionScreen
import com.rishinnovations.isai.ui.screens.EqualizerScreen
import com.rishinnovations.isai.ui.screens.ExploreScreen
import com.rishinnovations.isai.ui.screens.HomeScreen
import com.rishinnovations.isai.ui.screens.LibraryScreen
import com.rishinnovations.isai.ui.screens.LicensesScreen
import com.rishinnovations.isai.ui.screens.PlaylistDetailScreen
import com.rishinnovations.isai.ui.screens.PlaylistsScreen
import com.rishinnovations.isai.ui.screens.PrivacyScreen
import com.rishinnovations.isai.ui.screens.SearchScreen
import com.rishinnovations.isai.ui.screens.SettingsScreen
import com.rishinnovations.isai.ui.screens.StatsScreen
import com.rishinnovations.isai.ui.theme.IsaiTheme
import com.rishinnovations.isai.ui.theme.LocalReduceMotion
import com.rishinnovations.isai.ui.theme.isai
import java.util.Locale

private data class TabSpec(val label: String, val icon: ImageVector)

private val tabSpecs = listOf(
    TabSpec("Home", Icons.Filled.Home),
    TabSpec("Explore", Icons.Filled.Explore),
    TabSpec("Library", Icons.Filled.LibraryMusic),
    TabSpec("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    TabSpec("Settings", Icons.Filled.Settings)
)

@Composable
fun IsaiApp(player: PlayerViewModel, app: AppViewModel) {
    val settings by app.settings.collectAsStateWithLifecycle()
    IsaiTheme(settings.themeMode, settings.reduceMotion) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            IsaiShell(player, app)
        }
    }
}

@Composable
private fun IsaiShell(player: PlayerViewModel, app: AppViewModel) {
    val context = LocalContext.current
    val container = remember { context.container }
    val nav = androidx.compose.runtime.saveable.rememberSaveable(saver = NavState.Saver) { NavState() }
    val settings by app.settings.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()
    val favoriteSet by app.favoriteSet.collectAsStateWithLifecycle()
    val permission by app.permissionGranted.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    val snackbar = remember { SnackbarHostState() }

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showAddMusic by rememberSaveable { mutableStateOf(false) }
    var showSleep by rememberSaveable { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    var playlistTarget by remember { mutableStateOf<List<Long>?>(null) }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }

    // ---- launchers ----
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.refreshLibrary() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) app.importFiles(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) app.importFolder(uri)
    }

    val actions = remember(nav, player, app) {
        Actions(
            nav = nav, player = player, app = app,
            openPlayer = { showPlayer = true },
            openAddMusic = { showAddMusic = true },
            openSongMenu = { menuSong = it },
            addToPlaylist = { playlistTarget = it },
            showSleepTimer = { showSleep = true },
            requestMusicPermission = {
                val granted = ContextCompat.checkSelfPermission(context, audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val activity = context as? android.app.Activity
                // After two denials Android stops showing the dialog; send the user to system settings instead.
                if (!granted && activity != null && !activity.shouldShowRequestPermissionRationale(audioPermission) && askedAudio(context)) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                } else {
                    markAskedAudio(context)
                    permissionLauncher.launch(audioPermission)
                }
            },
            openAppSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
            ensureNotificationPermission = {
                if (Build.VERSION.SDK_INT >= 33 && !askedNotifications &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    askedNotifications = true
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }

    // Picking up a permission granted from system settings while the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (container.library.hasAudioPermission() != container.library.permissionGranted.value) app.refreshLibrary()
    }
    LaunchedEffect(Unit) { AppMessages.flow.collect { snackbar.showSnackbar(it) } }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && nav.stack.isNotEmpty()) { nav.pop() }
    BackHandler(enabled = !showPlayer && nav.stack.isEmpty() && nav.tab != Tabs.HOME) { nav.selectTab(Tabs.HOME) }

    ProvideRowState(
        favorites = favoriteSet,
        nowPlayingId = playerState.mediaId,
        actions = SongRowActions(toggleFavorite = { app.toggleFavorite(it.id) }, openMenu = { menuSong = it })
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalActions provides actions) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        Column {
                            if (playerState.hasMedia) {
                                MiniPlayer(playerState, player.positionMs, player, onOpen = { showPlayer = true })
                            }
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                tabSpecs.forEachIndexed { index, spec ->
                                    NavigationBarItem(
                                        selected = nav.tab == index,
                                        onClick = { nav.selectTab(index) },
                                        icon = { Icon(spec.icon, contentDescription = null) },
                                        label = { Text(spec.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        ScreenHost(nav)
                    }
                }

                AnimatedVisibility(
                    visible = showPlayer,
                    enter = if (reduceMotion) fadeIn(tween(0)) else slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
                    exit = if (reduceMotion) fadeOut(tween(0)) else slideOutVertically(tween(240)) { it } + fadeOut(tween(200))
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        FullPlayer(
                            state = playerState, position = player.positionMs, vm = player,
                            favorites = favoriteSet, visualizerEnabled = settings.visualizerEnabled,
                            onCollapse = { showPlayer = false },
                            onToggleFavorite = { app.toggleFavorite(it.id) },
                            onOpenMenu = { menuSong = it },
                            onOpenEqualizer = { showPlayer = false; nav.push(Route.Equalizer) },
                            onOpenSleepTimer = { showSleep = true }
                        )
                    }
                }
            }
        }
    }

    // ---- sheets and dialogs ----
    menuSong?.let { song ->
        SongMenuSheet(
            song = song, isFavorite = song.id in favoriteSet,
            onDismiss = { menuSong = null },
            onPlayNext = { player.playNext(song); AppMessages.post("Will play next") },
            onAddToQueue = { player.addToQueue(song); AppMessages.post("Added to queue") },
            onAddToPlaylist = { playlistTarget = listOf(song.id) },
            onToggleFavorite = { app.toggleFavorite(song.id) },
            onGoToAlbum = { showPlayer = false; nav.push(Route.Album(song.albumKey)) },
            onGoToArtist = { showPlayer = false; nav.push(Route.Artist(song.artist)) },
            onDetails = { detailsSong = song }
        )
    }
    detailsSong?.let { SongDetailsDialog(it) { detailsSong = null } }
    playlistTarget?.let { ids -> PlaylistPicker(app, ids) { playlistTarget = null } }
    if (showSleep) SleepTimerDialog(container.sleepTimer) { showSleep = false }
    if (showAddMusic) {
        AddMusicSheet(
            permissionGranted = permission,
            onAllowAccess = { showAddMusic = false; actions.requestMusicPermission() },
            onPickFiles = { showAddMusic = false; filePicker.launch(arrayOf("audio/*", "application/ogg")) },
            onPickFolder = { showAddMusic = false; folderPicker.launch(null) },
            onDismiss = { showAddMusic = false }
        )
    }
}

private const val PREFS = "isai_ui"

private fun askedAudio(context: android.content.Context) =
    context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean("asked_audio", false)

private fun markAskedAudio(context: android.content.Context) {
    context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean("asked_audio", true).apply()
}

@Composable
private fun ScreenHost(nav: NavState) {
    val route = nav.current
    if (route == null) {
        when (nav.tab) {
            Tabs.HOME -> HomeScreen()
            Tabs.EXPLORE -> ExploreScreen()
            Tabs.LIBRARY -> LibraryScreen()
            Tabs.PLAYLISTS -> PlaylistsScreen()
            else -> SettingsScreen()
        }
        return
    }
    when (route) {
        is Route.Album, is Route.Artist, is Route.Genre, is Route.Collection -> CollectionScreen(route)
        is Route.Playlist -> PlaylistDetailScreen(route.id)
        Route.Search -> SearchScreen()
        Route.Equalizer -> EqualizerScreen()
        Route.Stats -> StatsScreen()
        Route.About -> AboutScreen()
        Route.Licenses -> LicensesScreen()
        Route.Privacy -> PrivacyScreen()
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SongMenuSheet(
    song: Song, isFavorite: Boolean, onDismiss: () -> Unit,
    onPlayNext: () -> Unit, onAddToQueue: () -> Unit, onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit, onGoToAlbum: () -> Unit, onGoToArtist: () -> Unit, onDetails: () -> Unit
) {
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Artwork(song, Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            fun then(action: () -> Unit): () -> Unit = { onDismiss(); action() }
            SheetAction(Icons.Filled.PlayArrow, "Play next", then(onPlayNext))
            SheetAction(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue", then(onAddToQueue))
            SheetAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", then(onAddToPlaylist))
            SheetAction(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (isFavorite) "Remove from favorites" else "Add to favorites", then(onToggleFavorite))
            SheetAction(Icons.Filled.Album, "Go to album", then(onGoToAlbum))
            SheetAction(Icons.Filled.Person, "Go to artist", then(onGoToArtist))
            SheetAction(Icons.Filled.Info, "Details", then(onDetails))
        }
    }
}

@Composable
private fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    val rows = listOfNotNull(
        "Title" to song.title,
        "Artist" to song.artist,
        "Album" to song.album,
        song.genre?.let { "Genre" to it },
        if (song.year > 0) "Year" to song.year.toString() else null,
        if (song.trackNumber > 0) "Track" to song.trackNumber.toString() else null,
        "Duration" to formatDuration(song.durationMs),
        "Format" to song.mimeType.ifBlank { "Unknown" },
        "Size" to String.format(Locale.US, "%.1f MB", song.sizeBytes / 1_048_576.0),
        "Source" to if (song.isImported) "Added by you" else "Device library"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Song details") },
        text = {
            Column {
                rows.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k, Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun PlaylistPicker(app: AppViewModel, songIds: List<Long>, onDismiss: () -> Unit) {
    val playlists by app.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog(
            "New playlist", "", "Create", "Playlist name",
            onConfirm = { name ->
                app.createPlaylist(name, songIds) { AppMessages.post("Added to “$name”") }
            },
            onDismiss = { creating = false; onDismiss() }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn {
                item {
                    SheetAction(Icons.Filled.Add, "New playlist") { creating = true }
                }
                items(playlists, key = { it.id }) { playlist ->
                    SheetAction(Icons.AutoMirrored.Filled.PlaylistPlay, "${playlist.name} (${playlist.songIds.size})") {
                        app.addToPlaylist(playlist.id, songIds)
                        AppMessages.post("Added to “${playlist.name}”")
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddMusicSheet(
    permissionGranted: Boolean, onAllowAccess: () -> Unit, onPickFiles: () -> Unit, onPickFolder: () -> Unit, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Add music", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            Text(
                "ISAI plays music that is already on your device. Nothing is uploaded or copied.",
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (!permissionGranted) SheetAction(Icons.Filled.LibraryMusic, "Allow access to music on this device", onAllowAccess)
            SheetAction(Icons.Filled.MusicNote, "Choose audio files", onPickFiles)
            SheetAction(Icons.Filled.Folder, "Choose a folder", onPickFolder)
        }
    }
}
