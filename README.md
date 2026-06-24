# MedTrack — the 3D Medicine Cabinet

Cross-platform (Android & iOS) Flutter app for tracking medication schedules,
built to match the design in `design/MedTrack Cabinet.dc.html`: a first-person
matte-white polymer cabinet with warm LED shelf lighting, volumetric medicine
objects, a holographic action panel, and a brass + button.

## Run it

```bash
flutter pub get
flutter run
```

Tests and analysis:

```bash
flutter analyze
flutter test
```

## Architecture

Unidirectional data flow; UI is separated from database and notification
logic (see `CLAUDE.md`):

```
lib/
├── models/        Medication, DoseLog (immutable value types)
├── data/          sqlite (sqflite): app_database, medication_repository
├── services/      notification_service (flutter_local_notifications +
│                  timezone), photo_service (image_picker + compression)
├── state/         AppState (ChangeNotifier via provider), adherence math
├── theme/         design tokens extracted from the HTML design
└── ui/            cabinet / schedule / history screens, add-med sheet
```

- **Storage:** all data stays on the device (SQLite + app-private photo
  files) per NFR-4. Photos are compressed on pick (max width 1280,
  JPEG quality 80) per FR-1.3.
- **Reminders:** daily zoned schedules at 8:00 / 14:00 / 21:00
  (Morning / Midday / Evening) with `DateTimeComponents.time` matching, so
  wall-clock times survive DST shifts and timezone moves (FR-2.3).
  Notifications carry Taken / Skip / Snooze 10 min actions handled even
  while the app is closed (FR-2.2); falls back to inexact alarms if the
  user revokes the exact-alarm permission.

## Native configuration (already applied)

These are the platform changes this app depends on. They are committed, but
listed step by step in case you regenerate the platform folders.

### Android (`android/app/`)

1. `src/main/AndroidManifest.xml` — permissions before `<application>`:
   - `POST_NOTIFICATIONS` (Android 13+ runtime permission, requested on
     first launch)
   - `RECEIVE_BOOT_COMPLETED` (re-register reminders after reboot, NFR-2)
   - `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM` (minute-precise reminders)
   - `VIBRATE`
2. `src/main/AndroidManifest.xml` — receivers inside `<application>`:
   - `com.dexterous.flutterlocalnotifications.ScheduledNotificationReceiver`
   - `com.dexterous.flutterlocalnotifications.ScheduledNotificationBootReceiver`
     with `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` and quickboot intents
3. `build.gradle.kts`:
   - `minSdk = 26` (SRS: Android 8.0+)
   - `isCoreLibraryDesugaringEnabled = true` plus
     `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")`
     (required by flutter_local_notifications)

No camera permission is declared: `image_picker` uses the system camera and
photo picker intents, which need none.

### iOS (`ios/Runner/`)

1. `Info.plist`:
   - `NSCameraUsageDescription` — camera capture for medicine photos
   - `NSPhotoLibraryUsageDescription` — gallery picking
2. `AppDelegate.swift`:
   - `FlutterLocalNotificationsPlugin.setPluginRegistrantCallback` so the
     background isolate handling notification actions can use plugins
   - sets `UNUserNotificationCenter.current().delegate`

> Note: the SRS targets iOS 12.0+, but current Flutter stable supports a
> higher minimum (the generated Runner targets it automatically). Building
> for iOS requires macOS/Xcode; this repo was developed on Linux, so the
> iOS side is configured but not build-verified.

## Design notes

- The cabinet is laid out in the design's fixed 348×564 coordinate space and
  scaled with `FittedBox`, so every gradient, shadow and LED strip uses the
  exact values from the HTML design.
- "By type" shelves group by container (pill bottles / jars & blisters /
  syrups & liquids); "By time of day" groups by earliest reminder slot —
  objects glide between shelves exactly like the design's mode switcher.
- Fonts: Instrument Serif (OFL) is bundled in `assets/fonts/`.
