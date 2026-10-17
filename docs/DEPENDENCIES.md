# Dependencies

All runtime dependencies are Apache License 2.0. Versions are pinned in `native-android/app/build.gradle.kts`.

| Library | Version | Purpose |
|---|---|---|
| AndroidX Core KTX, Core SplashScreen | see build file | platform APIs, splash |
| AndroidX Activity Compose, Lifecycle | 2.8.7 (lifecycle) | activity and state |
| Jetpack Compose BOM, Material 3, Material Icons Extended | BOM 2025.04.01, M3 1.4.0 | UI |
| AndroidX Media3 ExoPlayer + Session | 1.6.1 | playback engine, media session, notification |
| Guava (Android flavor) | 33.4.0 | ListenableFuture used by Media3 |
| AndroidX Room | 2.7.1 | favorites, playlists, history, imported files |
| AndroidX DataStore Preferences | 1.1.4 | settings and playback state |
| kotlinx.coroutines | 1.9.0 | concurrency |
| Coil | 2.7.0 | artwork loading |

Network access uses only `java.net.HttpURLConnection` and `org.json` (Android platform); no extra libraries.

Test only: org.json 20240303 (JSON license), JUnit 4.13.2 (EPL 1.0), kotlinx-coroutines-test.

Build tooling: Android Gradle Plugin 8.9.2, Kotlin 2.1.20, KSP 2.1.20-2.0.1.

Each library keeps its own license and notices; see the projects' repositories for full texts. No code from other music players is copied (see `NOTICE-SIMPMUSIC.md`).
