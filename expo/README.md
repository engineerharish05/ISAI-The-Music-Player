# ISAI Expo Android

This is the native Android implementation of ISAI using Expo SDK 55 and Expo Router.

## Functional scope

- Android MediaStore music scanning
- Local song library
- Favorites and persistent history
- Local playlists
- Queue state
- Shuffle and repeat
- Full-screen player
- Background playback
- Android media notification and lock-screen controls
- Expo development-build/EAS-ready native configuration

## Development build

From this directory:

```bash
npm install
npx expo install
npx expo-doctor
npx expo run:android
```

For an EAS Android build:

```bash
eas build --platform android --profile preview
```

SDK 55+ uses React Native's New Architecture, so this implementation intentionally avoids legacy native modules.
