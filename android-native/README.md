# Native Android app

This directory contains the native Kotlin + Jetpack Compose Android application.
It is intentionally separate from the existing web app and Capacitor prototype.

## Build
Open this directory in Android Studio and run the `app` configuration, or run:

```bash
gradle assembleDebug
```

The first native milestone establishes the app identity, theme, navigation shell, and screen transitions. Backend-backed authentication and each production feature are being ported in follow-up milestones.
