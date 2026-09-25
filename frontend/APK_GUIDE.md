# Rebuilding and reinstalling the ECZAM APK

Quick reference for building a fresh debug APK (e.g. after the backend's LAN IP
changes, or after code changes) and for removing an old install from the
phone. For first-time toolchain setup, see `RUNNING.md`.

---

## 1. When you need to rebuild

- **Your computer's LAN IP changed** (different network, reconnected WiFi,
  new DHCP lease) — the API URL is baked into the APK at build time, so a
  changed IP means the installed app can no longer reach the backend and
  login/everything fails with a network error.
- **You changed Dart/Flutter code** and want the update on your phone.
- **You changed the backend's exposed port** (currently `8090`, see
  `backend/docker-compose.override.yml`).

If the app suddenly can't reach the server and nothing else changed, check
your computer's current LAN IP first (Step 2) — it's the most common cause.

---

## 2. Rebuild and reinstall

### Step 1 — Make sure the backend is running

```bash
cd ~/Desktop/ECZAM/backend
docker compose up -d
curl http://localhost:8090/api/v1/actuator/health   # expect {"status":"UP"}
```

### Step 2 — Get your computer's current LAN IP

```bash
hostname -I
```

Take the address that's on the **same network as your phone** (usually the
first one, but double check if you're on VPN/multiple networks — the wrong
one silently produces the same "can't reach server" symptom).

### Step 3 — Connect the phone via USB

Plug it in, accept the "Allow USB debugging?" prompt if it appears, then
confirm:

```bash
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
adb devices
```

Must show your device as `device` (not empty, not `unauthorized`).

### Step 4 — Build

```bash
export JAVA_HOME=~/.jdks/corretto-18.0.2
export ANDROID_HOME=~/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$HOME/development/flutter/bin:$PATH"

cd ~/Desktop/ECZAM/frontend
flutter build apk --debug --dart-define=API_BASE_URL=http://<LAN_IP>:8090/api/v1
```

Replace `<LAN_IP>` with the address from Step 2. Output:
`build/app/outputs/flutter-apk/app-debug.apk`

### Step 5 — Install (replaces the old one automatically)

```bash
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

`-r` reinstalls over the existing app **in place** — same app icon, same
package, login session and local data preserved where possible. You normally
do **not** need to manually uninstall first; only do that if Step 5 fails
(see Troubleshooting).

---

## 3. Deleting the old APK directly from the phone

No computer/cable needed for this — do it entirely on the phone:

**Option A — from the home screen or app drawer:**
1. Long-press the **ECZAM** icon.
2. Tap **App info** (ⓘ) or **Uninstall** if it's offered directly in the
   popup menu.
3. Confirm **Uninstall**.

**Option B — from Settings:**
1. **Settings → Apps** (sometimes "Apps → See all apps").
2. Find and tap **ECZAM**.
3. Tap **Uninstall**, confirm.

Either way removes the app and its local data (SQLite mirror cache, cached
JWT) from the phone. It does **not** touch the real backend/database on your
computer — those are separate.

**From the computer instead**, if the phone is plugged in:

```bash
adb uninstall dev.canzorlu.medtrack
```

(This is the app's package/application ID — `frontend/android/app/build.gradle.kts`
is the source of truth if it's ever renamed.)

---

## 4. Troubleshooting

| Symptom | Fix |
|---|---|
| `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or a signature error | Uninstall first (Section 3, either method), then run Step 5 again as a fresh install |
| App installs but login says it can't reach the server | Re-check Step 2 — the LAN IP almost always changed; also confirm phone and computer are on the *same* WiFi network |
| `adb devices` shows nothing | Try a different USB cable (some are charge-only) or re-toggle USB debugging in Developer options |
| `adb devices` shows `no permissions (missing udev rules? user is in the plugdev group)` | Linux host-machine issue, not the phone. Find the vendor ID with `lsusb` (e.g. `04e8` for Samsung), then:<br>`echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="<VENDOR_ID>", MODE="0666", GROUP="plugdev"' \| sudo tee /etc/udev/rules.d/51-android.rules`<br>`sudo udevadm control --reload-rules && sudo udevadm trigger`<br>then unplug/replug and `adb kill-server && adb start-server` |
| Rebuilt but phone still shows old behavior | Uninstall the old app first (Section 3) — very old Flutter/Gradle caches can occasionally serve a stale build otherwise; also confirm the `flutter build apk` command actually completed with `✓ Built ...` |
