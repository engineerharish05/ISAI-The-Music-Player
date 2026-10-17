package com.rishinnovations.isai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rishinnovations.isai.MainActivity
import com.rishinnovations.isai.R
import com.rishinnovations.isai.container
import com.rishinnovations.isai.data.library.ArtworkCache
import com.rishinnovations.isai.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget. Its buttons connect to the real playback session, and the playback service
 * pushes every track or play-state change back here, so the widget mirrors the app.
 */
class IsaiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetIds.toList())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != ACTION_PLAY_PAUSE && action != ACTION_NEXT && action != ACTION_PREVIOUS) return

        val pending = goAsync()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                when (action) {
                    ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    ACTION_NEXT -> controller.seekToNext()
                    ACTION_PREVIOUS -> controller.seekToPrevious()
                }
                // Give the command time to reach the session before disconnecting.
                Handler(Looper.getMainLooper()).postDelayed({
                    controller.release()
                    pending.finish()
                }, 800)
            } catch (_: Exception) {
                pending.finish()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.rishinnovations.isai.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.rishinnovations.isai.widget.NEXT"
        const val ACTION_PREVIOUS = "com.rishinnovations.isai.widget.PREVIOUS"
        private const val PREFS = "widget_state"

        /** Called by the playback service whenever the current track or play state changes. */
        fun push(context: Context, title: String?, artist: String?, songId: Long?, playing: Boolean) {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("artist", artist)
                .putLong("songId", songId ?: -1L).putBoolean("playing", playing).apply()
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, IsaiWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(app, ids.toList())
        }

        private fun render(context: Context, ids: List<Int>) {
            val app = context.applicationContext
            // Artwork decoding happens off the main thread.
            app.container.scope.launch(Dispatchers.IO) {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val title = prefs.getString("title", null)
                val artist = prefs.getString("artist", null)
                val songId = prefs.getLong("songId", -1L)
                val playing = prefs.getBoolean("playing", false)
                val art = if (songId != -1L) {
                    (ArtworkCache.cachedFile(app, songId) ?: ArtworkCache.fileFor(app, songId))
                        ?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
                } else null

                val views = RemoteViews(app.packageName, R.layout.widget_isai)
                views.setTextViewText(R.id.widget_title, title ?: app.getString(R.string.widget_idle_title))
                views.setTextViewText(R.id.widget_artist, if (title != null) artist ?: "" else app.getString(R.string.widget_idle_subtitle))
                views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                if (art != null) views.setImageViewBitmap(R.id.widget_art, art) else views.setImageViewResource(R.id.widget_art, R.drawable.isai_emblem)
                views.setOnClickPendingIntent(R.id.widget_play, broadcast(app, ACTION_PLAY_PAUSE, 1))
                views.setOnClickPendingIntent(R.id.widget_next, broadcast(app, ACTION_NEXT, 2))
                views.setOnClickPendingIntent(R.id.widget_prev, broadcast(app, ACTION_PREVIOUS, 3))
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(app, 4, Intent(app, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                val manager = AppWidgetManager.getInstance(app)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, IsaiWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
