package com.rishinnovations.isai

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class IsaiViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository(app.contentResolver)
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val controllerExecutor = Executors.newSingleThreadExecutor()
    private var controller: MediaController? = null

    fun scan() {
        viewModelScope.launch {
            _songs.value = repo.scan()
        }
    }

    fun connect() {
        if (controller != null) return

        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )

        val future = MediaController.Builder(getApplication(), token)
            .buildAsync()

        future.addListener(
            {
                runCatching { controller = future.get() }
            },
            controllerExecutor
        )
    }

    fun play(song: Song) {
        controller?.let { c ->
            val item = MediaItem.Builder()
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()

            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        controllerExecutor.shutdown()
        super.onCleared()
    }
}
