package com.rishinnovations.isai.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rishinnovations.isai.playback.PlaybackLauncher

@Composable
fun ISAIApp(vm: MusicViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsState()
    var selected by remember { mutableIntStateOf(0) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.scan() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val labels = listOf("Home", "Search", "Library", "Downloads", "Profile")
    val icons = listOf(
        Icons.Default.Home,
        Icons.Default.Search,
        Icons.Default.Album,
        Icons.Default.Download,
        Icons.Default.Person
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            0 -> HomeScreen(
                tracks = tracks,
                modifier = Modifier.padding(padding),
                onPlay = { PlaybackLauncher.play(vm.getApplication(), it) }
            )
            1 -> SearchScreen(
                tracks = tracks,
                modifier = Modifier.padding(padding),
                onPlay = { PlaybackLauncher.play(vm.getApplication(), it) }
            )
            2 -> LibraryScreen(
                tracks = tracks,
                modifier = Modifier.padding(padding),
                onPlay = { PlaybackLauncher.play(vm.getApplication(), it) }
            )
            3 -> PlaceholderScreen("Downloads", "Offline download management is the next layer.")
            else -> PlaceholderScreen("Profile", "Account, connected services and preferences.")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title)
        Text(message)
    }
}
