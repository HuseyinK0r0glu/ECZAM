/// Runtime configuration injected at build time via `--dart-define`.
///
/// The ECZAM Spring Boot backend serves everything under the `/api/v1` context
/// path on port 8080. Point [apiBaseUrl] at it for the target you're running:
///
/// * Android emulator → host machine: `http://10.0.2.2:8080/api/v1` (default)
/// * iOS simulator    → host machine: `http://localhost:8080/api/v1`
/// * Physical device  → host LAN IP:  `http://192.168.x.y:8080/api/v1`
/// * Production:                       `https://api.eczam.app/api/v1`
///
/// ```bash
/// flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
/// flutter build apk --dart-define=API_BASE_URL=https://api.eczam.app/api/v1
/// ```
library;

const String apiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080/api/v1',
);
