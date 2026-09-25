# Running ECZAM on this machine

Toolchain is installed locally under the home directory (no `sudo`, not on
`PATH` by default):

| Component | Location |
|-----------|----------|
| Flutter SDK 3.44.5 (stable) | `~/development/flutter` |
| Android SDK (platform 34 + 36, build-tools 28.0.3/34.0.0, platform-tools) | `~/Android/Sdk` |
| JDK for Android tooling | `~/.jdks/corretto-18.0.2` |

Export these once per shell (or add to your `~/.bashrc`):

```bash
export JAVA_HOME=~/.jdks/corretto-18.0.2
export ANDROID_HOME=~/Android/Sdk
export ANDROID_SDK_ROOT=~/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$HOME/development/flutter/bin:$PATH"
```

Run `flutter doctor` to confirm — Android toolchain and Chrome should be ✓.
(The Linux desktop toolchain shows ✗ — that's fine, we're not targeting it.)

---

## 1. Start the backend

The backend runs via Docker Compose (Postgres+pgvector + the Spring Boot API).

```bash
cd backend
cp .env.example .env      # already done — .env has a generated JWT_SECRET
docker compose up -d      # starts db + backend
```

**Port note:** this machine already had other services on 5432 and 8080, so
`backend/docker-compose.override.yml` (gitignored, local-only) remaps the
host-exposed ports:

- API → `http://localhost:8090/api/v1` (container-internal port is still 8080)
- Postgres → `localhost:5433` (container-internal port is still 5432)

If your machine doesn't have that conflict, delete the override file and the
API reverts to the documented `:8080`.

Verify it's up:

```bash
curl http://localhost:8090/api/v1/actuator/health   # {"status":"UP"}
```

Swagger UI: `http://localhost:8090/api/v1/swagger-ui.html`

To seed the medication catalog (optional — enables barcode lookup /
search against real data), see `plans/medications-schema-plan.md`; the
uncompressed dataset lives at the repo root (`medicine.json`,
`medicine_data_set.sql`).

AI assistant features (RAG chat) need `ANTHROPIC_API_KEY` / `OPENAI_API_KEY`
filled in in `backend/.env`, then `docker compose up -d --build backend` to
pick them up. Everything else (auth, inventory, schedules, dose logging,
expiration) works without them.

---

## 2. Run the app on your phone

### One-time phone setup

1. On your Android phone: **Settings → About phone → tap "Build number" 7
   times** to unlock Developer Options.
2. **Settings → Developer options → enable "USB debugging"**.
3. Plug the phone into this computer with a USB cable.
4. A prompt appears on the phone asking to trust this computer — accept it.

### Verify the connection

```bash
adb devices
# should list your phone, e.g.:  ABCD1234    device
```

If it shows `unauthorized`, check the phone screen for the trust prompt.

If it shows `no permissions (missing udev rules? user is in the plugdev
group)` — this is a Linux host-machine issue, not the phone. Find the
phone's USB vendor ID with `lsusb` (look for your phone's manufacturer,
e.g. `04e8` for Samsung), then:

```bash
echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="<VENDOR_ID>", MODE="0666", GROUP="plugdev"' | sudo tee /etc/udev/rules.d/51-android.rules
sudo udevadm control --reload-rules
sudo udevadm trigger
```

Unplug and replug the phone, then refresh adb:

```bash
adb kill-server
adb start-server
adb devices   # should now show "device"
```

### Point the app at the backend

The phone can't reach `localhost:8090` on your computer directly — use
`adb reverse` to tunnel it over the USB cable (works regardless of WiFi):

```bash
adb reverse tcp:8090 tcp:8090
```

### Run it

```bash
cd frontend
flutter pub get   # already done once; safe to re-run
flutter run --dart-define=API_BASE_URL=http://localhost:8090/api/v1
```

This installs a debug build on the phone, attaches a hot-reload session, and
opens the app. Press `r` in the terminal to hot-reload after code changes,
`R` for a full hot-restart, `q` to quit.

**Alternative — WiFi instead of USB tunnel:** if your phone and computer are
on the same WiFi network, skip `adb reverse` and instead use this machine's
LAN IP (find it with `hostname -I`, e.g. `192.168.1.221`):

```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.221:8090/api/v1
```

### Building an installable APK instead of a live debug session

```bash
flutter build apk --debug --dart-define=API_BASE_URL=http://192.168.1.221:8090/api/v1
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

(Use your LAN IP here, not `localhost` — a standalone APK isn't tunneled by
`adb reverse` after this session ends. For a real release build see the
signing notes below.)

---

## 3. Other run targets (quick checks, not the phone)

```bash
flutter test          # unit + widget tests
flutter analyze        # static analysis
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8090/api/v1   # web preview
```

Chrome/web works for a quick UI look but several packages (sqflite offline
mirror, mobile_scanner, flutter_local_notifications) don't behave the same as
on-device — treat it as a preview, not a substitute for running on the phone.

---

## Release builds (signing)

Release signing config (keystore, `key.properties`) isn't set up on this
machine yet — only debug builds are covered above. See
`docs/system-architecture.md` and the Flutter docs on
[signing the app](https://docs.flutter.dev/deployment/android#signing-the-app)
if/when you need a release `.apk`/`.aab`.
