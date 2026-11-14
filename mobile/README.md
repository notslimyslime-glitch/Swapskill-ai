# SkillSwap AI Android app

This folder contains the Capacitor Android wrapper. It reuses the current root `index.html`; the live website and its GitHub Pages deployment are not changed by Android builds.

## Requirements

- Node.js 22 or newer
- Android Studio with an Android SDK installed
- A physical Android device with USB debugging enabled, or an Android emulator

## First-time setup

From this folder:

```bash
npm install
npm run copy:web
npx cap add android
npx cap sync android
npx cap open android
```

In Android Studio, wait for Gradle sync, choose an emulator or connected phone, then press Run.

## Updating the app after website changes

From this folder, run:

```bash
npm run android:sync
```

This copies the latest root website into `www/` and syncs it into the Android project. It does not publish the website or release an APK.

## Notes

- The Android project is generated locally by `npx cap add android`; commit the generated `android/` folder after setup so the native project is versioned.
- Supabase still uses the existing project. Use test accounts while developing, and avoid running destructive database changes against real user data.
- This is the initial wrapper scaffold, not a tested APK. Device testing and Android-specific polish come next.
