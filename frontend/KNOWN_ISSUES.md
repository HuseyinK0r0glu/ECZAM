# Known build issues

## "plugins that apply Kotlin Gradle Plugin (KGP)" warning

**Seen when:** running `flutter build apk` or `flutter run` on Android.

```
WARNING: Your app uses the following plugins that apply Kotlin Gradle Plugin (KGP):
flutter_timezone, flutter_tts, mobile_scanner
Future versions of Flutter will fail to build if your app uses plugins that apply KGP.
```

**Current impact:** none. It's a warning, not an error — the build completes
and the APK is produced normally (`✓ Built build/app/outputs/flutter-apk/app-debug.apk`).
Safe to ignore for day-to-day development.

**Cause:** Flutter is moving to a model where the Flutter Gradle plugin
supplies Kotlin support itself ("Built-in Kotlin"), instead of expecting each
plugin to apply the Kotlin Gradle Plugin in its own `android/build.gradle`.
The three plugins below still use the old pattern:

| Plugin | Version pinned (`pubspec.yaml`) | Version resolved (`pubspec.lock`) |
|---|---|---|
| `flutter_timezone` | `^5.1.0` | 5.1.0 |
| `flutter_tts` | `^4.2.0` | 4.2.5 |
| `mobile_scanner` | `^5.2.3` | 5.2.3 (7.2.0 available) |

A future stable Flutter release will turn this warning into a hard build
failure until each plugin migrates.

**Fix, when it's actually needed:**

1. Check each plugin's changelog/GitHub issues for a release that mentions
   "Built-in Kotlin" support:
   - https://pub.dev/packages/flutter_timezone/changelog
   - https://pub.dev/packages/flutter_tts/changelog
   - https://pub.dev/packages/mobile_scanner/changelog
2. Bump the version constraint in `pubspec.yaml` for the fixed plugin(s) and
   run `flutter pub get`.
3. Re-run `flutter build apk --debug` — the warning for that plugin should
   disappear.
4. Regression-test the features each plugin backs before shipping the bump:
   - `flutter_timezone` → dose reminder scheduling (times must still fire in
     the correct local timezone)
   - `flutter_tts` → AI assistant "read aloud" control
   - `mobile_scanner` → barcode scan → `GET /medications/barcode/{code}` flow
5. If no fixed version exists yet when the warning becomes a hard error,
   check https://docs.flutter.dev/release/breaking-changes/migrate-to-built-in-kotlin/for-app-developers
   for interim workarounds, or file/track an issue against the plugin.

**Don't** try to "fix" this by editing the plugins' generated
`android/build.gradle` files under `~/.pub-cache` — those are regenerated on
every `flutter pub get` and any local edit is silently lost.
