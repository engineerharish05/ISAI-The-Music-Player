# Contributing

1. Fork, branch, and keep changes focused.
2. Run `gradle testDebugUnitTest lintDebug assembleDebug` in `native-android/` before opening a PR; CI runs the same.
3. Put logic that does not need Android types in `logic/` and add JVM unit tests.
4. Do not add the INTERNET permission, analytics, ads or committed secrets. Features must work offline.
5. Never describe a feature as working unless it has been run. Update `docs/FEATURES.md` honestly.

Report bugs at https://github.com/engineerharish05/isai-the-music-player/issues.
