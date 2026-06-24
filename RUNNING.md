# Running MedTrack on this machine

The Android toolchain is installed locally (no sudo, not on PATH):

| Component | Location |
|-----------|----------|
| Flutter SDK 3.44.2 | `~/development/flutter` |
| JDK 17 (Gradle needs it; system Java is 25) | `~/development/jdk-17.0.19+10` |
| Android SDK | `~/Android/Sdk` |
| Release keystore | `~/development/medtrack-keys/medtrack-release.jks` |
| AVD (Android 14, Pixel 6, x86_64) | `medtrack_pixel` |

Set these for any build/run command:

```bash
export JAVA_HOME=~/development/jdk-17.0.19+10
export ANDROID_HOME=~/Android/Sdk ANDROID_SDK_ROOT=~/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
FLUTTER=~/development/flutter/bin/flutter
```

## Build

```bash
$FLUTTER test          # 27 tests, all pass
$FLUTTER analyze       # clean
$FLUTTER build apk --debug      # → build/app/outputs/flutter-apk/app-debug.apk
$FLUTTER build apk --release    # → app-release.apk (R8-minified, signed, ~50 MB)
$FLUTTER build appbundle --release  # → build/app/outputs/bundle/release/app-release.aab
```

The `.aab` is what you upload to the Play Console. It is signed with the same
upload keystore; Play App Signing then re-signs with the Google-managed app
key. Verify: `jarsigner -verify build/app/outputs/bundle/release/app-release.aab`.

The release APK is signed with the keystore via `android/key.properties`
(passwords there; keep the .jks safe — losing it means you can't ship updates).
Verify signing: `~/Android/Sdk/build-tools/34.0.0/apksigner verify --print-certs <apk>`.

## Emulator (headless) — IMPORTANT gotchas discovered on this host

1. **Launch the emulator as a background task that stays alive.** A foreground
   `nohup ... &` gets reaped when the shell call returns. Run the bare
   `emulator` command as a persistent background process.
2. **Disable guest Vulkan** — the host SwiftShader Vulkan (Subzero) path
   crashes; GLES compositing is more stable:
   ```bash
   ~/Android/Sdk/emulator/emulator -avd medtrack_pixel \
     -no-window -no-audio -no-boot-anim -memory 2048 \
     -gpu swiftshader_indirect -feature -Vulkan -no-snapshot -accel on
   ```
3. **Software GPU can't sustain the cabinet's continuous LED animation** — the
   headless SwiftShader renderer crashes after ~30–60 s of animated rendering.
   The app itself is fine: the home screen renders correctly (see the captured
   screenshots) and the add-medication flow is covered by the widget test
   `empty cabinet shows hint, add flow places med on shelf`. On a real device
   or a hardware-accelerated (host-GPU) emulator this is a non-issue.

Install + launch:

```bash
adb install -r build/app/outputs/flutter-apk/app-release.apk
adb shell pm grant dev.canzorlu.medtrack android.permission.POST_NOTIFICATIONS
adb shell monkey -p dev.canzorlu.medtrack -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > shot.png
```
