package com.rishinnovations.isai.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rishinnovations.isai.data.MusicTrack
import com.rishinnovations.isai.playback.PlaybackLauncher
import com.rishinnovations.isai.ui.theme.Gold

private data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun ISAIApp(vm: MusicViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsState()
    var selected by remember { mutableIntStateOf(0) }
    var currentTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.scan()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.READ_MEDIA_AUDIO)
        else permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val navItems = listOf(
        NavItem("Home", Icons.Rounded.Home),
        NavItem("Search", Icons.Rounded.Search),
        NavItem("Library", Icons.Rounded.LibraryMusic),
        NavItem("Downloads", Icons.Rounded.Download),
        NavItem("Profile", Icons.Rounded.Person)
    )

    fun play(track: MusicTrack) {
        currentTrack = track
        showPlayer = true
        PlaybackLauncher.play(vm.getApplication(), track)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                if (expanded) GlassRail(navItems, selected) { selected = it }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (selected) {
                                0 -> HomeScreen(tracks, Modifier.fillMaxSize(), ::play)
                                1 -> SearchScreen(tracks, Modifier.fillMaxSize(), ::play)
                                2 -> LibraryScreen(tracks, Modifier.fillMaxSize(), ::play)
                                3 -> DownloadsScreen()
                                else -> ProfileScreen()
                            }
                        }
                        AnimatedVisibility(showPlayer && currentTrack != null) {
                            MiniPlayer(currentTrack!!, { play(currentTrack!!) }, { showPlayer = false })
                        }
                        if (!expanded) GlassBottomDock(navItems, selected) { selected = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassRail(items: List<NavItem>, selected: Int, onSelected: (Int) -> Unit) {
    Column(
        Modifier.width(104.dp).fillMaxHeight().padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ISAI", color = Gold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(28.dp))
        items.forEachIndexed { index, item ->
            GlassNavItem(item, selected == index) { onSelected(index) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GlassBottomDock(items: List<NavItem>, selected: Int, onSelected: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .66f)
            ))).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { index, item -> GlassNavItem(item, selected == index) { onSelected(index) } }
    }
}

@Composable
private fun GlassNavItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Gold.copy(alpha = .16f) else Color.Transparent,
        border = if (selected) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Column(Modifier.width(66.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, item.label, tint = if (selected) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniPlayer(track: MusicTrack, onPlay: () -> Unit, onOpen: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkPlaceholder(54.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                Text(track.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, "Play") }
        }
    }
}

@Composable
private fun ArtworkPlaceholder(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(
        Brush.linearGradient(listOf(Gold.copy(alpha=.9f), MaterialTheme.colorScheme.surfaceVariant))
    )) {
        Text("♪", Modifier.align(Alignment.Center), color = Color.Black, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun GlassSurface(modifier: Modifier, shape: RoundedCornerShape, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .78f))
            .then(Modifier.padding(1.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = .18f), shape)
            .padding(1.dp),
        content = content
    )
}

@Composable fun DownloadsScreen() { EmptyState("Downloads", "Offline downloads will appear here.") }
@Composable fun ProfileScreen() { EmptyState("Profile", "Account, connected services and preferences.") }

@Composable
private fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
