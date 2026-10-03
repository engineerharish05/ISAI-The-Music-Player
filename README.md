# ISAI — The Music Player

ISAI is a mobile-first music-player interface built around the uploaded ISAI identity and a modern glass/Apple-style aesthetic.

## Current build

- SimpMusic-inspired music-player navigation and interaction patterns
- ISAI logo and branding
- Home, Explore, Library, Playlists and Settings
- Mini player and full-screen player
- Local audio import through the browser
- Search and genre filtering
- Favorites / liked songs
- Listening history
- Queue controls
- Shuffle and repeat
- Playback speed
- Sleep timer
- Equalizer interface
- Lyrics interface
- Light / dark theme
- Local browser persistence for preferences, favorites, history and playlists
- Responsive mobile and desktop layout

## Licensing

Original ISAI code is intended to use the custom **ISAI Custom Open Source License 1.0**. The native Android player implementation is independently implemented for ISAI. See [LICENSE-ISAI.md](LICENSE-ISAI.md) and [NOTICE-SIMPMUSIC.md](NOTICE-SIMPMUSIC.md).

> The custom ISAI license does not override third-party licenses.

## Important scope

This repository is currently a browser-based ISAI prototype. It does not include SimpMusic's YouTube Music extraction/backend, account system, Android Auto integration, online lyrics providers, SponsorBlock, or other service-specific infrastructure.

The UI and interaction model are implemented separately for ISAI rather than copying SimpMusic's source code.

## Reference

SimpMusic and other music players may be consulted as public UX references; ISAI's implementation is independently written.

## Run

Open index.html in a modern browser. For best results, serve the folder through a small local HTTP server.

## Project

Private repository: engineerharish05/ISAI-The-Music-Player

## Build

Android debug builds are validated by GitHub Actions with Node.js syntax checking before Capacitor/Gradle packaging.
