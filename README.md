# ISAI — The Music Player

ISAI is a native Android music player built with Kotlin, Jetpack Compose and AndroidX Media3.

## Native Android implementation

The Android app is implemented natively under `native-android/`.

- Native Jetpack Compose UI
- Home, Explore, Library, Playlists and Settings
- Local MediaStore music scanning
- Media3 playback engine
- Background playback through MediaSessionService
- Android audio and notification permissions
- Room persistence foundation
- ISAI premium visual system

## Build

Run the native verification script:

```bash
cd native-android
./verify.sh
```

The verification pipeline runs unit tests, lint, debug APK assembly and release APK assembly.

## Architecture

**Native Android is the only Android application implementation.**

The legacy web-based Android wrapper has been removed. The production app is built directly with the native Android stack.

The native implementation will be extended directly for local playback, playlists, lyrics, equalizer/effects, downloads, Android Auto, widgets, analytics and online-service integrations.

## Licensing

Original ISAI source code is released under the standard **Apache License 2.0**. This recognized open-source license permits use, modification, redistribution, and commercial use subject to its terms.

ISAI branding is separate from the software license. The ISAI name, logo, artwork, and other marks are not granted as trademarks.

See [LICENSE](LICENSE) and [NOTICE-SIMPMUSIC.md](NOTICE-SIMPMUSIC.md) for details. Third-party dependencies remain under their own licenses.

## Project

Repository: engineerharish05/ISAI-The-Music-Player
