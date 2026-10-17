# Feature and verification status

"Verified" means checked by an automated step that has actually run (GitHub Actions: unit tests, debug build, lint, release build). **No feature has been run on a physical device or emulator yet**, because the development sandbox has none. Everything that depends on Android runtime behaviour is therefore "implemented, not verified on a device".

## Implemented and verified by CI
- The whole app compiles (debug and release) and passes Android lint.
- Unit-tested logic (JVM): LRC parser, ID3v2 USLT/SYLT parser, FLAC Vorbis-comment parser, play-count and listening rules, fade maths, sleep-timer state machine, FFT spectrum analyzer, queue index maths, text normalisation, sorting, library grouping, search ranking, recommendation rules.
- No INTERNET permission in the manifest; no secrets in the repository.

## Implemented, not verified on a device
- MediaStore library scan, live refresh, file/folder import via the system picker
- Playback in a Media3 `MediaLibraryService`: queue, shuffle, repeat, background playback, notification and lock-screen controls, queue restore (paused) after restart
- Audio-focus, headset and error-skip behaviour (Media3 defaults plus a consecutive-error guard)
- Favorites, playlists (including reorder and removing unavailable songs), listening statistics, history clearing
- Lyrics display and attaching `.lrc` / `.txt` files; embedded lyrics work only for MP3 (ID3) and FLAC
- Equalizer, bass boost, loudness (only offered when the device supports them)
- Fade between tracks (a fade out/in, **not** an overlapping crossfade), playback speed, sleep timer including end-of-song
- Visualizer driven by the real PCM stream
- Artwork loading and disk cache, including artwork for the notification, widget and Android Auto
- Home-screen widget (play/pause/next/previous)
- Android Auto media browsing (Songs, Albums, Artists, Favorites, Playlists, Most played, Recent) and voice-search intent declaration; **not testable without a head unit or the Desktop Head Unit**, and voice search is only declared, not handled
- Offline recommendations
- Dark/light/system theme, reduce motion, splash screen with the ISAI emblem, adaptive launcher icon

## Optional online music (v0.3, implemented, not verified on a device)
- Off by default; enable in Settings. Searches archive.org (CC / public-domain audio) and Jamendo (only when built with `JAMENDO_CLIENT_ID`).
- Streams in the same player; "save" downloads the file into app storage and adds it to the library.
- Online queue items are not restored after an app restart. Network code has not been run against the live services (the build sandbox has no access); only the JSON parsers are unit tested.

## Not implemented
- YouTube/Spotify or other services that forbid third-party extraction (by design), online lyrics
- Tag/metadata editing
- Overlapping crossfade and gapless-specific tuning beyond Media3 defaults
- Instrumented (on-device) UI tests
- A signed release build (the release APK is unsigned unless you supply your own keystore)
- Voice-search handling for Android Auto / assistants

## Known limitations
- Genre data on Android 10 and below uses legacy tables and may be incomplete.
- Imported (picker) songs that move or are deleted show as "Unavailable" in playlists until removed.
