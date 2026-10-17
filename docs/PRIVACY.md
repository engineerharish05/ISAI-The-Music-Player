# ISAI Privacy

- ISAI declares **no INTERNET permission**. It cannot transmit data off your device.
- No ads, analytics, crash reporters, tracking or accounts.
- The library comes from Android's media index (with your permission to read audio) and from files or folders you choose with the system picker. Picked items are referenced in place; nothing is copied or uploaded.
- Favorites, playlists, imported-file references, settings and listening history are stored only in the app's private storage. Backups are disabled (`allowBackup=false`), so they are not copied to cloud backups. Uninstalling removes everything.
- Listening history can be cleared in Settings → Your data.
- Artwork thumbnails and attached lyrics files are cached in the app's private storage; the artwork cache can be cleared in Settings.
- The "Send feedback" and "Source code" links open your browser; that traffic is between your browser and GitHub, not ISAI.

Permissions: read audio (`READ_MEDIA_AUDIO`, or `READ_EXTERNAL_STORAGE` on Android 12 and below), notifications (Android 13+, for the playback notification), foreground service (media playback), wake lock (keep playing with the screen off).
