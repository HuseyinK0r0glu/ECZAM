# CLAUDE.md

Operating guide for Claude Code (and any AI-assisted agent) working in this
repository. Read this first, then treat [`ECZAM_PROJECT_BRIEF.md`](ECZAM_PROJECT_BRIEF.md)
as the authoritative product/technical source of truth and the [`docs/`](docs/)
suite as the elaborated specification.

> **One rule above all:** Do not deviate from the brief or the docs without an
> explicit instruction. When code and docs disagree, fix the mismatch — don't
> silently diverge.

---

## 1. What ECZAM is

ECZAM is a **smart medication management app** (Flutter mobile client + Spring
Boot backend; originally specced as a PWA) that makes
medication safe, organized, and accessible — especially for elderly and chronic-
condition patients and their caregivers. It manages the full medication lifecycle
through four pillars:

1. **Medication adherence** — smart schedules + push/email reminders.
2. **Inventory management** — real-time pill/stock counts, auto-decremented on each
   logged dose, with low-stock alerts.
3. **Expiration monitoring** — proactive warnings before expiry and flags for
   expired stock.
4. **Intelligent information access** — searchable, voice-enabled (TTS) leaflets and
   a RAG-based AI assistant grounded strictly in those leaflets.

---

## 2. Tech stack

| Layer | Choice |
|---|---|
| **Backend** | Java 21, **Spring Boot 3.2+**, Spring Web, Spring Data JPA, Spring Security, Bean Validation, **Maven** |
| **Database** | **PostgreSQL** + **pgvector**; migrations via **Flyway** |
| **Frontend** | **Flutter** (Dart) — Material 3, **Provider** (state), **Dio** (HTTP + interceptors + SSE), **sqflite** (offline mirror cache), `flutter_secure_storage` (JWT) |
| **Offline** | Online-first with a SQLite **mirror cache** + an outbox (`pending_ops`) drained on reconnect; `connectivity_plus` for online/offline detection |
| **Scheduler** | Backend: Spring `@Scheduled` / Quartz (per-minute tick). Client reminders: **`flutter_local_notifications`** (exact local alarms, offline-capable) |
| **AI** | Anthropic API `claude-sonnet-4-6` (assistant, streamed via SSE-over-POST); OpenAI `text-embedding-3-small` or a local model (embeddings); **`flutter_tts`** for read-aloud |
| **Barcode** | **`mobile_scanner`** (camera) → `GET /medications/barcode/{code}` |
| **Notifications** | **Local** reminders on-device (`flutter_local_notifications`). Backend Web Push (VAPID) is **browser-only** — server-driven push to the native app would need **FCM**, which is a documented gap (not built for MVP) |

> **Stack notes:**
> 1. The brief originally suggested a Node/NestJS or Rust/Axum backend. This
>    project deliberately uses **Spring Boot + PostgreSQL** instead.
> 2. The frontend was migrated from a **React 18 + TS PWA** to **Flutter** (see
>    [`plans/flutter-migration-plan.md`](plans/flutter-migration-plan.md)). The
>    archived React app lives in `old_frontend_react/`. The REST contract, DB
>    schema, and domain logic in the brief still apply verbatim — only the client
>    framework changed. See [`docs/system-architecture.md`](docs/system-architecture.md).
> 3. The Flutter package is still internally named `medtrack`
>    (`frontend/pubspec.yaml`, all `package:medtrack/...` imports) — renaming to
>    `eczam` is optional mechanical cleanup, left as-is.

---

## 3. Repository layout

```
ECZAM/
├── ECZAM_PROJECT_BRIEF.md   # authoritative product/technical spec (source of truth)
├── CLAUDE.md                # this file
├── docs/                    # full documentation suite (see docs/README.md)
├── plans/                   # planning artifacts
├── backend/                 # Spring Boot service
├── frontend/                # Flutter app (Dart) — the active client
└── old_frontend_react/      # archived React + TS PWA (superseded by Flutter)
```

**Backend package layering** (per domain: auth, users, medications, inventory,
reminders, expiration, notifications, ai, integrations, shared):

```
controller  → HTTP handling, request validation, response shaping
service     → business logic
repository  → Spring Data JPA persistence
entity      → JPA @Entity types mapping the DB schema
dto         → request/response records + Bean Validation
mapper      → entity ↔ DTO (MapStruct)
```

**Frontend structure** (`frontend/lib/`) is layered:

```
core/        cross-cutting plumbing
  config/env.dart        API base URL from --dart-define
  api/                   Dio client, {data,meta,error} envelope, ApiException
  sync/                  connectivity + (mirror cache lives in data/)
  token_store.dart       secure JWT access + refresh storage
features/    one folder per domain (DTOs + repositories that call the backend)
  auth/  medications/  inventory/  schedules/  logs/  expiration/  ai/  profile/
data/        app_database.dart (SQLite mirror + outbox),
             medication_repository.dart (mirror),
             backend_medication_repository.dart (online-first composition)
models/      UI aggregate models (Medication, DoseLog) — String/UUID ids
services/    notification_service.dart (local notifications), photo_service.dart
state/       app_state.dart (Provider ChangeNotifier), adherence.dart
ui/          screens: cabinet/, schedule/, history/, add_med/, ai/, expiration/,
             profile/, scan/, home_shell.dart
theme/       med_theme.dart (design tokens)
```

---

## 4. Build / run / test commands

```bash
# Backend — one-command stack (Postgres+pgvector + API) via Docker Compose:
cd backend && cp .env.example .env        # fill in JWT_SECRET, AI keys, …
cd backend && docker compose up --build   # API on :8080, context path /api/v1
cd backend && docker compose up -d db      # just the DB (run the API from your IDE)

# Or run the API directly against a local DB on :5432 (db/user/pass eczam/eczam/eczam):
cd backend && ./mvnw spring-boot:run      # run API on :8080
cd backend && ./mvnw verify               # unit + integration tests (Testcontainers)
cd backend && ./mvnw clean package        # build jar

# Frontend (Flutter) — point API_BASE_URL at the backend for your target
cd frontend && flutter pub get
cd frontend && flutter analyze
cd frontend && flutter test               # unit + widget tests
# Android emulator → host backend (10.0.2.2 maps to the host loopback):
cd frontend && flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
# iOS simulator: use http://localhost:8080/api/v1 ; device: use the host LAN IP.
cd frontend && flutter build apk --dart-define=API_BASE_URL=https://api.eczam.app/api/v1
```

> The default `API_BASE_URL` is `http://10.0.2.2:8080/api/v1` (Android emulator).
> The Android manifest enables `usesCleartextTraffic` for local HTTP dev — use
> HTTPS in production. See `frontend/RUNNING.md` for the local toolchain paths.

---

## 5. Core conventions

- **API:** REST, versioned under `/api/v1`. Every response uses the envelope
  `{ data, meta, error }`. List endpoints use **cursor-based pagination**.
- **Validation:** validate every endpoint; on failure return **422** with
  field-level error details.
- **Auth:** all protected endpoints require `Authorization: Bearer <JWT>`.
  Passwords hashed with **bcrypt**.
- **Persistence:** UUID primary keys, `created_at` / `updated_at` timestamps,
  **parameterized queries only** (JPA/prepared statements — never string
  interpolation).
- **Errors:** a global exception handler maps exceptions to the envelope's `error`.
- **Dates/times:** store `TIMESTAMPTZ`; client renders in user locale.

See [`docs/api-specification.md`](docs/api-specification.md) and
[`docs/database-design.md`](docs/database-design.md) for the full contract.

---

## 6. Domain model (at a glance)

Seven tables: `users`, `medications` (global catalog), `user_medications`
(personal inventory), `medication_schedules`, `medication_logs` (immutable),
`push_subscriptions`, and `leaflet_chunks` (pgvector embeddings). Key invariant:
**logging a dose decrements the matching `user_medications.quantity`**. Full schema
in [`docs/database-design.md`](docs/database-design.md).

Notable behaviors to preserve:
- **Per-box inventory** — `user_medications` carries GS1 `batch`/`serial_number`
  (+ `expiration_date`); one physical box = one row (5-column UNIQUE). A scanned
  serial can't be added twice.
- **Idempotent dose logging** — `POST /medication-logs` accepts an optional
  `clientRequestId`; a replay with the same key returns the original result and
  does **not** decrement again (the Flutter sync engine sends a stable key so the
  offline outbox flush is safe).
- **GTIN join key** — `medications.gtin` (canonical 14-digit) is the scan lookup
  key; `barcode` is the raw source value.
- **RAG grounding gate** — `eczam.ai.min-score` (default 0.30) is the retrieval
  threshold; below it the assistant declines. Truncated leaflets append a caveat.

---

## 7. AI assistant guardrails

The assistant answers **only** from retrieved leaflet chunks (RAG). It must:

- never provide general medical advice or speculate beyond retrieved passages;
- say so clearly and suggest consulting a pharmacist/physician when it can't answer
  from the passages;
- cite which leaflet section the answer came from;
- respond in the same language the user wrote in.

Pipeline and system prompt: [`docs/system-architecture.md`](docs/system-architecture.md) §AI.

---

## 8. Hard constraints

- **MVP scope only** — build the 8 core features in the brief's Phase 1–6 order.
- **Out of scope (do NOT build for MVP):** multi-user/caregiver accounts,
  interaction detection, OCR box recognition, prescription import, refill
  recommendations, national DB integrations beyond OpenFDA, microphone voice input
  (TTS output only). See [`docs/mvp-definition.md`](docs/mvp-definition.md).
- **Accessibility:** WCAG 2.1 AA-equivalent; fully functional on a 375px-wide
  screen; respect the OS font-scale setting; visible focus/semantics on
  interactive widgets; TTS reachable via a labelled control (`flutter_tts`).
- **Compliance:** **KVKK** (Turkey) — health data is special-category personal data.
  Tokens live in the platform keystore/keychain (`flutter_secure_storage`).
  See [`docs/security-requirements.md`](docs/security-requirements.md).
- **Performance:** p95 < 300ms for non-AI endpoints; AI time-to-first-token < 2s.
- **Push gap:** the backend's Web Push (VAPID) is browser-only; the native app
  uses **local notifications** for reminders. Server-driven push would need FCM —
  out of scope for MVP, tracked as a known gap.

---

## 9. Where to look

- Product spec & non-negotiables → [`ECZAM_PROJECT_BRIEF.md`](ECZAM_PROJECT_BRIEF.md)
- Documentation index & reading order → [`docs/README.md`](docs/README.md)
- When adding a feature: find its `FR-###` / `US-###` / `UC-###` in the docs, keep
  IDs consistent, and update the traceability matrix in `docs/README.md`.
