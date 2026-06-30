# Migrate ECZAM frontend from React to Flutter + wire to Spring Boot backend

## Context

ECZAM currently has a committed **React 18 + TypeScript PWA** in `frontend/` and a
mature **Spring Boot** API in `backend/`. The user has decided to drop React and
adopt the Flutter app that already exists at `ECZAM-flutter-mvp-v1/`.

That Flutter app (`medtrack`) is a **polished but fully-offline, SQLite-only**
adherence tracker: no HTTP client, no auth, no networking of any kind. Its data
model is a single flat `Medication` (name, dose, `MedKind`, `reminderMinutes`,
photo) + `DoseLog`. The backend, by contrast, splits data into **global catalog →
`user_medications` (inventory, quantity, expiry) → `medication_schedules` →
`medication_logs`**, uses **UUID** ids, requires **JWT** on nearly every endpoint,
and wraps every response in `{ data, meta, error }`.

The "backend code in the Flutter part for testing" is the **local SQLite layer**
(`lib/data/`) plus **pure-Dart test fakes** (`FakeMedicationRepository`, etc.) —
there is no embedded mock server. We keep SQLite (repurposed as an offline cache),
keep the Dart fakes for tests, but make the **Spring Boot backend the source of
truth**.

**Goal (per user decisions):**
1. Rename `frontend/` → `old_frontend_react/`; promote `ECZAM-flutter-mvp-v1/` → `frontend/`.
2. **Full MVP build-out**: wire every needed backend feature (auth, catalog,
   inventory, schedules, logging, expiration, AI assistant) into Flutter.
3. **Online-first with offline cache**: when online, read/write the backend and
   mirror responses into SQLite; when offline, read from SQLite and queue writes,
   flushing the queue on reconnect.
4. **Configurable base URL** via `--dart-define`.
5. Update CLAUDE.md, the brief, `docs/`, and `plans/` to reflect Flutter.
6. Test everything that could break.

> This is a large migration. The plan is phased so each phase compiles and is
> testable on its own. Phases 0–4 are foundation; phase 5 is feature wiring;
> phases 6–8 are backend touch-ups, docs, and verification.

---

## Phase 0 — Folder restructure (do first, in git)

- `git mv frontend old_frontend_react`
- `git mv ECZAM-flutter-mvp-v1 frontend`
- The Flutter package is internally named `medtrack` (see `frontend/pubspec.yaml:1`
  and every `import 'package:medtrack/...'`). **Leave the package name as-is** for
  now — renaming it touches ~18 files of imports and the Android/iOS bundle ids for
  no functional gain. Note it as optional cleanup. (If the user wants it renamed to
  `eczam`, that's a mechanical find/replace across `lib/`, `test/`, `pubspec.yaml`,
  `android/app/build.gradle`, and iOS plist — call it out, don't bundle it.)
- Verify the move didn't break asset paths (`frontend/assets/`) or
  `frontend/RUNNING.md` references.

---

## Phase 1 — Networking + config foundation

New folder: `frontend/lib/core/`.

- **Add deps** to `frontend/pubspec.yaml`:
  - `dio` (HTTP + interceptors + streamed responses for SSE)
  - `flutter_secure_storage` (JWT access + refresh tokens)
  - `connectivity_plus` (online/offline detection for the sync layer)
  - `flutter_tts` (AI assistant TTS — replaces the brief's Web Speech API)
  - `mobile_scanner` (barcode lookup — replaces `html5-qrcode`)
- **`lib/core/config/env.dart`** — base URL from `--dart-define`:
  ```dart
  const apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api/v1', // Android emulator → host
  );
  ```
  Document run commands with `--dart-define=API_BASE_URL=...` (emulator
  `10.0.2.2`, device LAN IP, prod URL).
- **`lib/core/api/api_client.dart`** — Dio instance with `baseUrl = apiBaseUrl`,
  JSON headers, and interceptors:
  - **Auth interceptor**: attach `Authorization: Bearer <accessToken>`.
  - **Refresh interceptor**: on `401` with code `UNAUTHENTICATED`/`TOKEN_INVALID`,
    call `POST /auth/refresh` with the stored refresh token, persist the new pair,
    retry once; on failure, clear tokens and bounce to login.
- **`lib/core/api/api_envelope.dart`** — generic `ApiResponse<T>` parsing
  `{ data, meta, error }`; `ApiError { code, message, fields }`; `Meta { nextCursor, limit }`.
  Throw a typed `ApiException(ApiError, statusCode)` so the UI can map `422`
  field errors and `INSUFFICIENT_STOCK`, `EMAIL_TAKEN`, etc.
- **`lib/core/token_store.dart`** — secure read/write/clear of access+refresh tokens.

---

## Phase 2 — Auth (mandatory; no auth UI exists today)

New feature folder: `frontend/lib/features/auth/`.

- **DTOs** matching `AuthController`: `RegisterRequest{email,password,displayName}`,
  `LoginRequest{email,password}`, `AuthResponse{user,accessToken,refreshToken}`,
  `UserProfile`.
- **`auth_repository.dart`** — `register`, `login`, `refresh`, `logout`; persists
  tokens via `TokenStore`.
- **`auth_state.dart`** (`ChangeNotifier`, matching the app's existing Provider
  pattern) — holds auth status + current user; exposed at the top of the widget
  tree in `lib/main.dart` (add a `MultiProvider`).
- **Screens**: `login_screen.dart`, `register_screen.dart` (validate inline; show
  backend `422` `fields` and `INVALID_CREDENTIALS`/`EMAIL_TAKEN` messages).
- **Gate**: `lib/main.dart` shows auth screens when unauthenticated, `HomeShell`
  when authenticated. On boot, if a refresh token exists, try `/auth/refresh`
  before deciding.
- Password policy is enforced server-side (≥8, upper/lower/digit/special) — surface
  `WEAK_PASSWORD` on register.

> Out of scope for MVP wiring unless asked: Google login, 2FA, email verification,
> password reset, session management screens. Backend supports them; leave as
> documented follow-ups.

---

## Phase 3 — Domain model remap + DTOs

This is the heart of the migration. Replace the single flat `Medication` concept
with the backend's four entities, while preserving the existing UI's "a medicine
with reminder times" mental model as an **aggregate view**.

New: `frontend/lib/features/medications/`, `.../inventory/`, `.../schedules/`,
`.../logs/`, `.../expiration/`, `.../ai/` — each with `*_dto.dart` +
`*_repository.dart`.

Key DTOs (mirror backend field names exactly):
- `MedicationView` / `MedicationDetail` (catalog: `id,name,genericName,manufacturer,barcode,form,strength,...`)
- `InventoryItem` (`id,medicationId,medicationName,strength,form,quantity,unit,expirationDate,notes,lowStock,expiryStatus`)
- `ScheduleView` (`id,userMedicationId,medicationName,dosageAmount,frequencyType,frequencyValue,scheduledTimes[],daysOfWeek[],active,startsOn,endsOn`)
- `LogView` / `LogResult{log,newQuantity,lowStock}`

**Model-mapping decisions (call out clearly; these are lossy in both directions):**
- **IDs**: switch `Medication`/`DoseLog` from `int` to `String` (UUID). SQLite cache
  columns become `TEXT`. This touches `lib/models/medication.dart`,
  `lib/models/dose_log.dart`, `lib/state/app_state.dart`, the notification service
  (uses `med.id` for notification ids — derive a stable `int` via `id.hashCode`),
  and all `*.fromMap`/`toMap`.
- **`reminderMinutes` ↔ `scheduledTimes`**: convert minutes-since-midnight
  (`480`) ↔ `"HH:mm"` (`"08:00"`). A med's reminder times become a `DAILY`
  schedule with `scheduledTimes`. Add helpers in the schedules feature.
- **Add-med flow** (`lib/ui/add_med/add_med_sheet.dart`) now performs a 3-call
  orchestration when online: (1) find-or-create catalog medication
  (`GET /medications?q=` then `POST /medications` if absent), (2)
  `POST /user-medications` (needs new fields: **quantity, unit, expirationDate** —
  add to the form), (3) `POST /user-medications/{id}/schedules` from the reminder
  times. Encapsulate in an `AddMedicationUseCase`.
- **Dose logging gap**: Flutter `DoseStatus` has `taken/skipped/snoozed`; backend
  `medication_logs` only records **taken** doses (with `quantityUsed`, decrements
  inventory). Decision: only `taken` syncs to `POST /medication-logs`;
  `skipped`/`snoozed` remain **local-only** (kept in SQLite, used for adherence
  history). Document this. Handle `422 INSUFFICIENT_STOCK` in the UI.
- **Adherence** (`lib/state/adherence.dart`) keeps working off local logs; backend
  `GET /medication-logs` backfills the cache.

---

## Phase 4 — Offline-first sync layer

New: `frontend/lib/core/sync/`.

- **Repurpose SQLite** (`lib/data/app_database.dart`) as a **mirror cache**, not the
  source of truth. Bump schema: add columns for UUID ids, inventory fields
  (quantity, unit, expiration, lowStock, expiryStatus), schedule fields, and a
  `sync_state`/`updated_at` per row. Add an **outbox** table
  (`pending_ops`: id, entity, op, payload-json, created_at) for queued offline
  writes.
- **`connectivity.dart`** — wraps `connectivity_plus`, exposes an online/offline
  stream.
- **`sync_engine.dart`** — the read/write policy the user specified:
  - **Read (online)**: call backend → return data → upsert into SQLite mirror.
  - **Read (offline)**: read from SQLite mirror.
  - **Write (online)**: call backend → on success mirror into SQLite.
  - **Write (offline)**: write to SQLite optimistically + enqueue into `pending_ops`.
  - **On reconnect**: drain `pending_ops` in order (POST/PATCH/DELETE), reconcile
    server responses (real UUIDs replace any temp ids), surface conflicts.
- Repositories from Phase 3 call the sync engine instead of Dio or SQLite directly,
  so each feature is offline-aware uniformly.
- **Notifications stay local**: `flutter_local_notifications` already handles
  reminders offline — keep it, reschedule from synced schedules in
  `AppState.init()`.

> Keep this incremental: get **online-only** read/write working for one feature
> end-to-end first, then layer the outbox/offline-fallback on top. Don't build the
> full conflict engine before anything works.

---

## Phase 5 — Feature wiring (screen by screen)

Existing screens live under `lib/ui/` (cabinet, schedule, history, add_med).
Rework each onto the new repositories, and add new screens for the build-out.

- **Cabinet / inventory** (`lib/ui/cabinet/`): list = `GET /user-medications`;
  show `quantity`, `lowStock`, `expiryStatus` badges. Delete →
  `DELETE /user-medications/{id}`. Quantity edits → `PATCH /user-medications/{id}`.
- **Add / edit med** (`lib/ui/add_med/`): the 3-call orchestration from Phase 3;
  add quantity/unit/expiry inputs; optional **barcode lookup**
  (`GET /medications/barcode/{code}` via `mobile_scanner`) to prefill catalog data.
- **Schedule** (`lib/ui/schedule/`): from `GET /schedules`; pause/resume/delete via
  `POST /schedules/{id}/pause|resume`, `DELETE /schedules/{id}`.
- **History / logging** (`lib/ui/history/`, `action_panel.dart`): "taken" →
  `POST /medication-logs` (handle `LogResult.newQuantity`/`lowStock`,
  `INSUFFICIENT_STOCK`); history backfill via `GET /medication-logs`.
- **Expiration** (new `lib/features/expiration/` + a screen/section):
  `GET /expiration/expiring-soon`, `GET /expiration/expired`.
- **AI assistant** (new `lib/ui/ai/`): `POST /ai/chat` is **SSE over POST**. Use
  Dio `ResponseType.stream`, parse `event: token|citation|done`, stream tokens into
  a chat bubble, render citations, and on `grounded:false` show the
  "consult a pharmacist" message. Add `flutter_tts` "read aloud" button (TTS
  output only — mic input is out of scope per brief). Respect the same-language
  guardrail (send user text as-is).
- **Profile / preferences** (small): `GET/PATCH /users/me`,
  `PATCH /users/me/preferences` (low-stock threshold, expiry warning days) — feeds
  the badges above.

**Push notifications — explicit constraint:** the backend sends **Web Push (VAPID),
which is browser-only**. A native Flutter app would need **FCM**, which the backend
does not implement. Decision: **keep local notifications for reminders** (already
working) and treat **server-driven push as a documented gap** for the native app.
Flag this to the user; do not silently build half an FCM pipeline.

---

## Phase 6 — Backend touch-ups (minimal)

The backend is the source of truth and should change as little as possible.

- **CORS**: native mobile ignores CORS, so no change is needed for emulator/device.
  Only if the app is ever run as **Flutter web** does the dev origin matter
  (`eczam.cors.allowed-origin` / `FRONTEND_URL`, default `http://localhost:5173`).
  Note it; don't change unless web is targeted.
- Confirm the backend boots: needs **PostgreSQL + pgvector** (default
  `jdbc:postgresql://localhost:5432/eczam`, user/pass `eczam`/`eczam`), Flyway
  migrations V1–V6 apply, port `8080`, context path `/api/v1`. Provide a
  `docker run` Postgres snippet in docs if not already present.
- For AI to work, `ANTHROPIC`/embedding keys must be set; otherwise the assistant
  screen should degrade gracefully (show an error, not crash).
- No new endpoints required — the existing surface covers the full MVP.

---

## Phase 7 — Docs / CLAUDE.md / brief / plans updates

Update the React/PWA references to Flutter. Representative edits (not exhaustive):

- **`CLAUDE.md`**: tech-stack table row (React→Flutter: Dio, Provider,
  `flutter_local_notifications`, `flutter_tts`, `mobile_scanner`, secure storage,
  SQLite cache); repo-layout comment (`frontend/` = Flutter); frontend folder
  structure (`lib/features`, `lib/core`, `lib/ui`); build/run commands
  (`flutter pub get` / `flutter run --dart-define=...` / `flutter test`);
  note `old_frontend_react/` is archived; soften "PWA" framing.
- **`ECZAM_PROJECT_BRIEF.md`**: §4 Frontend Architecture (stack, directory tree,
  PWA→native, `useTTS`→`flutter_tts`), phase checklists, `FRONTEND_URL` note,
  TTS section.
- **`docs/`** (heaviest: `system-architecture.md` §6 + diagrams + ADR-8 PWA-vs-native,
  `test-plan.md` Vitest→`flutter test`, `mvp-definition.md` phases,
  `README.md` stack line + traceability; lighter: PRD, NFR installability,
  functional-requirements FR-PWA, use-cases diagram participants,
  security-requirements CORS/deps). Keep FR/US/UC IDs stable; update the
  traceability matrix.
- **`plans/`**: `phase-1-foundation.md` and `phase-6-pwa-polish.md` are almost
  entirely React/PWA — rewrite their Frontend sections for Flutter; phases 2–5
  swap React component/hook snippets for Flutter equivalents.
- Pattern to apply everywhere: npm→flutter commands; TanStack/Zustand→Provider;
  React Router→in-app navigation; Tailwind→Material/Theme; Axios→Dio; Web Speech→
  `flutter_tts`; html5-qrcode→`mobile_scanner`; service worker/VAPID web push→
  local notifications (+ FCM noted as a gap).

---

## Phase 8 — Verification (test everything that could break)

Work bottom-up; verify each phase before moving on.

1. **Backend up**: start Postgres, `cd backend && ./mvnw spring-boot:run`; smoke
   `POST /api/v1/auth/register` + `/auth/login` with `curl` to confirm envelope +
   tokens. Run `./mvnw test` (Testcontainers) to confirm nothing regressed.
2. **Flutter compiles**: `cd frontend && flutter pub get && flutter analyze` after
   each phase. The existing **27 unit/widget tests** must keep passing
   (`flutter test`) — update fakes (`FakeMedicationRepository`) for the new
   String-id / repository shapes; they're the safety net for the model remap.
3. **Auth e2e**: run app (`flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1`
   on Android emulator), register → land on `HomeShell`; kill/reopen → token
   refresh keeps you in; bad creds → `INVALID_CREDENTIALS` message.
4. **Per-feature e2e** against the live backend: add med (catalog+inventory+schedule
   created — verify via `GET`s), log a taken dose (quantity decrements;
   `INSUFFICIENT_STOCK` path), schedule pause/resume, expiration lists, barcode
   lookup, AI chat streams tokens + citations + `grounded:false` path.
5. **Offline path**: airplane-mode the emulator — reads serve from SQLite, a write
   enqueues; re-enable network — outbox drains and server reflects the change.
6. **New tests**: add unit tests for the envelope parser, the
   minutes↔`"HH:mm"` mapping, the refresh-interceptor retry, and the sync engine's
   online/offline branching (using a fake Dio + in-memory SQLite via
   `sqflite_common_ffi`, already a dev dep).
7. **Static/docs sanity**: grep the repo for lingering `5173`, `vite`, `npm`,
   `useTTS`, `service worker` outside `old_frontend_react/` to confirm docs were
   updated.

---

## Open items to confirm during execution (not blockers)
- Rename Flutter package `medtrack` → `eczam`? (optional, mechanical) — left as-is.
- Server-driven push: accept "local notifications only, FCM is a gap" for MVP?
- AI assistant requires backend AI keys to demo; OK to ship the screen with a
  graceful "unavailable" state if keys are absent?
