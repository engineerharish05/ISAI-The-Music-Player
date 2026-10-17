# ISAI — The Music Experience

*Your Day, Your moments, Your Isai.*
A refined music experience designed around the songs you love.

ISAI is an open-source, offline-first Android music player written natively in Kotlin with Jetpack Compose and AndroidX Media3. It plays the music already on your device. It has no ads, no accounts and no tracking. An optional, off-by-default *Online music* feature can stream and save free Creative Commons / public-domain music from archive.org (and Jamendo, if you build with a free client id).

![ISAI emblem](assets/isai-play-store-icon-512.png)

## Features

Status is stated honestly: see [docs/FEATURES.md](docs/FEATURES.md) for exactly what is verified and what is not.

- Home, Explore, Library, Playlists and Settings tabs, with search one tap away
- Local library from Android MediaStore, plus files and folders you pick yourself
- Background playback with a media notification, lock-screen controls, queue, shuffle and repeat
- Favorites, playlists (create, rename, reorder, remove), listening statistics
- Lyrics from embedded MP3 (ID3) / FLAC tags or an attached `.lrc` / `.txt` file; synced lines highlight as the song plays
- Equalizer, bass boost and loudness using the device's own audio effects
- Fade between tracks, playback speed, sleep timer (countdown or end of song)
- Real-time visualizer driven by the audio that is actually playing
- Offline, rule-based recommendations that explain why each section is shown
- Home-screen widget and Android Auto browse support
- Dark-first golden theme with a light theme, and a reduce-motion option

Optional online music (off by default): search archive.org and Jamendo, stream, and save tracks to your library. Not included by design: YouTube/Spotify or any service that forbids third-party access, online lyrics, accounts, cloud sync. See [docs/FEATURES.md](docs/FEATURES.md).

## Editions

One codebase builds two apps (Gradle product flavors):

- **isai**: the offline-first player. Online music is off until you enable it in Settings.
- **stream** ("ISAI Stream", application id `com.rishinnovations.isai.stream`): the same player, online-first. It starts on the open-music search and has online sources enabled by default. It installs alongside the main app. It is not a YouTube Music client: it only uses open catalogues that permit listening (archive.org and, if you supply a free client id, Jamendo).

## Build

Requirements: JDK 17, Android SDK (platform 35), Gradle 8.11+.

```bash
cd native-android
gradle testDebugUnitTest assembleDebug lintIsaiDebug lintStreamDebug assembleRelease
```

- Debug APKs: `native-android/app/build/outputs/apk/isai/debug/` and `.../stream/debug/`
- Release APK is unsigned unless you provide a keystore through the environment variables `ISAI_KEYSTORE_PATH`, `ISAI_KEYSTORE_PASSWORD`, `ISAI_KEY_ALIAS`, `ISAI_KEY_PASSWORD`. No signing secrets are stored in this repository.

GitHub Actions (`.github/workflows/native-android.yml`) runs the unit tests, debug build, lint and release build on every push and pull request and uploads the APKs and reports as artifacts.

## Install

Download `app-debug.apk` from the latest workflow run's artifacts, enable "install unknown apps" for your file manager or browser, and open the file. On first launch allow access to music, or use **Add music** to pick files or a folder.

## Project layout

```
native-android/app/src/main/java/com/rishinnovations/isai/
  model/      plain data types
  logic/      pure Kotlin: lyrics parsers, search, sorting, recommendations, playback math (unit tested)
  data/       Room database, DataStore settings, MediaStore + file import, artwork, lyrics
  playback/   Media3 MediaLibraryService, queue persistence, audio effects, sleep timer, Android Auto tree
  widget/     home-screen widget
  ui/         Compose screens, components, theme, navigation
docs/         features and verification status, privacy, dependencies
```

## Documentation

- [Feature and verification status](docs/FEATURES.md)
- [Privacy](docs/PRIVACY.md)
- [Dependencies and licenses](docs/DEPENDENCIES.md)
- [Contributing](CONTRIBUTING.md)

## License

Source code: Apache License 2.0 ([LICENSE](LICENSE)). The ISAI name and emblem are covered by [LICENSE-ISAI.md](LICENSE-ISAI.md). Third-party notes: [NOTICE-SIMPMUSIC.md](NOTICE-SIMPMUSIC.md).
