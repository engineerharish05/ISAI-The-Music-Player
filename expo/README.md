# ISAI Expo Android

Native Android implementation of ISAI using Expo SDK 55 and Expo Router.

## QA workflow
Every feature is validated incrementally through Expo/GitHub CI before the next feature is accepted.

## Development
```bash
npm install
npx expo-doctor
npm run typecheck
npx expo run:android
```

## EAS builds
```bash
eas build --platform android --profile preview
eas build --platform android --profile production
```

The preview profile produces an installable Android APK; production produces an Android App Bundle.
