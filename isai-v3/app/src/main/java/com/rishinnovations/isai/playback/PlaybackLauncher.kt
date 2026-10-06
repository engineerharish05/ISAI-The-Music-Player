package com.rishinnovations.isai.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rishinnovations.isai.data.MusicTrack

object PlaybackLauncher {
    fun play(context: Context, track: MusicTrack) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY
            putExtra(PlaybackService.EXTRA_URI, track.uri)
            putExtra(PlaybackService.EXTRA_TITLE, track.title)
            putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
