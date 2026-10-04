# ISAI — The Music Player

ISAI is a native Android music player built with Kotlin, Jetpack Compose and AndroidX Media3.

## Native Android implementation

The Android app lives entirely under `native-android/`.

- Native Jetpack Compose UI
- Home, Explore, Library, Playlists and Settings
- Local MediaStore music scanning
- Media3 playback engine
- Background playback through MediaSessionService
- Android audio and notification permissions
- Room persistence foundation
- ISAI premium dark/gold visual system
- Debug and release APK verification through GitHub Actions

## Build

Run the native verification script from `native-android/`:

```bash
cd native-android
./verify.sh
```

The verification pipeline runs unit tests, lint, a debug APK build and a release APK build.

## Architecture direction

**Native Android is the only Android application implementation.**

The previous Capacitor/web-wrapper implementation has been removed. ISAI will not use Capacitor, Cordova, a WebView-based Android shell, or a generated Capacitor Android project for the production app.

The native implementation will be extended directly for local playback, playlists, lyrics, equalizer/effects, downloads, Android Auto, widgets, analytics and online-service integrations.

## Licensing

Original ISAI code is intended to use the custom **ISAI Custom Open Source License 1.0**. See [LICENSE-ISAI.md](LICENSE-ISAI.md) and [NOTICE-SIMPMUSIC.md](NOTICE-SIMPMUSIC.md).

> The custom ISAI license does not override third-party licenses.

## Project

Private repository: engineerharish05/ISAI-The-Music-Player
