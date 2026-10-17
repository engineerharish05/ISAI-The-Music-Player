package com.rishinnovations.isai.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.rishinnovations.isai.model.Song

/** Everything a screen can ask the app shell to do. Provided once at the root through [LocalActions]. */
class Actions(
    val nav: NavState,
    val player: PlayerViewModel,
    val app: AppViewModel,
    val openPlayer: () -> Unit,
    val openAddMusic: () -> Unit,
    val openSongMenu: (Song) -> Unit,
    val addToPlaylist: (List<Long>) -> Unit,
    val showSleepTimer: () -> Unit,
    val requestMusicPermission: () -> Unit,
    val openAppSettings: () -> Unit,
    val ensureNotificationPermission: () -> Unit
) {
    fun play(songs: List<Song>, index: Int = 0) {
        ensureNotificationPermission()
        player.playSongs(songs, index)
    }

    fun playOnline(tracks: List<com.rishinnovations.isai.data.online.OnlineTrack>, index: Int) {
        ensureNotificationPermission()
        player.playOnline(tracks, index)
    }

    fun shuffle(songs: List<Song>) {
        ensureNotificationPermission()
        player.shufflePlay(songs)
    }

    /**
     * "Listen now": resume what was playing, otherwise recently played music, otherwise shuffle the
     * library, otherwise help the user add music.
     */
    fun listenNow() {
        val state = player.state.value
        val library = app.library.value
        if (state.hasMedia) {
            if (!state.isPlaying) {
                ensureNotificationPermission()
                player.togglePlayPause()
            }
            openPlayer()
            return
        }
        val recent = app.recentlyPlayed(library, app.stats.value)
        when {
            recent.isNotEmpty() -> { play(recent); openPlayer() }
            library.songs.isNotEmpty() -> { shuffle(library.songs); openPlayer() }
            else -> openAddMusic()
        }
    }
}

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions not provided") }
