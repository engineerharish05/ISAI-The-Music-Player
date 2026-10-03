# ISAI — Third-Party and License Notice

## ISAI source

The native Android player, local music catalog, playback coordinator, queue model, and ISAI UI in this repository are original ISAI project code and are licensed under the **ISAI Custom Open Source License 1.0**.

See [LICENSE-ISAI.md](LICENSE-ISAI.md).

## Independent implementation

ISAI may use public music-player applications as feature and UX references. The current native implementation does not intentionally copy SimpMusic source code.

The player uses Android MediaStore and AndroidX Media3/ExoPlayer APIs as technical dependencies. Those dependencies remain under their own licenses.

## Branding

The ISAI name, logo, visual identity, and original branding are not granted as trademarks by the ISAI software license.

## Free/open-source references used during the independent implementation

The project was checked against permissively licensed music-player implementations for feature and architecture ideas. No GPL source was copied into the current native player.

- Resonate — MIT License: https://github.com/kimhabkhun/resonate
- Rhythm — MIT License: https://github.com/Aortax213/Music

These repositories were used as public references for concepts such as Compose + Media3 playback, persistent library state, playlists, and player lifecycle. The ISAI native implementation remains independently written.
