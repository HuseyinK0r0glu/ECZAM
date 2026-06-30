# ECZAM — Comprehensive Test Plan (implementation)

## Context

`docs/test-plan.md` is the **spec-level** strategy (pyramid, goals). This document
is the **concrete, file-level plan** to: (a) close real coverage gaps, (b) verify
the code added/changed by the React→Flutter migration and the improvements work
(per-box inventory, idempotent logging, RAG gates, sync engine), and (c) catch
the **known flaws** those changes introduced. It covers both the Spring Boot
backend and the Flutter client, every test layer, tooling, CI wiring, and
coverage gates.

Why now: the backend has a respectable suite (auth, expiration, logging, security,
rate-limit, chunk-search), and the Flutter app has 7 unit/widget suites — but the
**highest-risk, most recently changed code is untested**: the offline sync engine,
the API client's refresh-retry, dose-log idempotency, GTIN canonicalization, the
leaflet/seed parser, RAG grounding gates, per-box (batch/serial) inventory, the AI
SSE parser, and most new screens. There are **no coverage gates** in CI today.

---

## 1. Current coverage (inventory)

**Backend — `backend/src/test/java/com/eczam/`** (Testcontainers + Postgres/pgvector):
`auth/AuthServiceTest`, `auth/AuthIntegrationTest`, `auth/TotpServiceTest`,
`auth/token/RefreshTokenServiceTest`, `expiration/ExpirationIntegrationTest`,
`inventory/ExpiryStatusTest`, `logs/DoseLoggingIntegrationTest`,
`notifications/PushSubscriptionIntegrationTest`, `reminders/ScheduleIsDueTest`,
`scheduler/NotificationDedupeTest`, `shared/security/PasswordPolicyTest`,
`shared/web/HardeningIntegrationTest`, `shared/web/RateLimitFilterTest`,
`ai/LeafletChunkSearchIntegrationTest`, `AbstractIntegrationTest` (base).

**Frontend — `frontend/test/`** (flutter_test + `sqflite_common_ffi`):
`adherence_test`, `api_envelope_test`, `cabinet_layout_test`,
`notification_time_test`, `repository_test`, `time_mapping_test`,
`widget_test` (+ `fakes.dart`).

**Biggest gaps at a glance**

| Area | Tested? | Risk |
|---|---|---|
| Flutter sync engine / `BackendMedicationRepository` | ❌ none | **Critical** — offline branching, outbox drain, reconciliation |
| Flutter `ApiClient` refresh-retry interceptor | ❌ none | **Critical** — silent auth bugs |
| Dose-log idempotency (`clientRequestId`) | ❌ none | High — double-decrement risk |
| `Gtin.canonicalize` | ❌ none | High — scan lookups silently miss |
| Seed `LeafletParser` / `SourceText` / chunking | ❌ none | High — RAG quality |
| `RagService` gating (decline / threshold / caveat) | ❌ none | High — hallucination guardrail |
| Per-box inventory (batch/serial, serial dedup) | ❌ none | High — just changed |
| AI SSE parser (`AiRepository.chat`) | ❌ none | Medium |
| Feature DTO parsing (inventory/schedule/log/profile/auth) | ❌ none | Medium |
| Screens: login/register/profile/expiration/AI/leaflet detail | ❌ none | Medium |
| Backend `MedicationService` (search/barcode/create/leaflet-search) | ❌ none | Medium |
| `CursorCodec`, `GlobalExceptionHandler` envelope | ❌ none | Medium |
| Coverage gates (JaCoCo / lcov) | ❌ none | Process |

---

## 2. Tooling

**Backend** (mostly present): JUnit 5, Spring Boot Test (`@SpringBootTest`,
`MockMvc`/`@WebMvcTest`), Testcontainers (`postgresql` + a pgvector image),
`spring-security-test`, Mockito (bundled). **Add:** JaCoCo (coverage + gate),
OWASP `dependency-check-maven` (CVE scan, non-gating first), optionally PIT
(mutation) on `*Service`, and Gatling **or** an external k6 script for load.

**Frontend** (mostly present): `flutter_test`, `sqflite_common_ffi` (in-memory
DB), `integration_test` (bundled). **Add dev deps:** `http_mock_adapter` (fake
`Dio` for `ApiClient`/repo tests), `mocktail` (lightweight mocks/fakes),
`golden_toolkit` **or** `alchemist` (golden tests), and `flutter test --coverage`
→ lcov. Existing `FakeMedicationRepository`/`FakeNotificationService` stay.

**Cross:** one shared envelope JSON fixture used by both a backend `MockMvc` test
and the Flutter `ApiResponse.fromJson` test (contract parity).

---

## 3. Coverage targets

- **Backend:** ≥ 80% line on `service`/`web`/`security`; **100%** on pure logic
  (`Gtin`, `LeafletParser`, `SourceText`, `CursorCodec`, `PasswordPolicy`,
  `ExpiryStatus`).
- **Frontend:** ≥ 70% on `core/`, `data/`, `features/`; **100%** on pure logic
  (`api_envelope`, time mapping, `ExpiryStatus`, the sync engine's
  online/offline branch selection).
- Gate CI on the line target once the P0/P1 tests land (start as a report, then
  enforce).

---

## 4. Backend test plan (by component)

### 4.1 `shared/web` & `shared/security`
- **`CursorCodec`** — `encode→decode` round-trips an `OffsetDateTime`; malformed
  cursor → graceful error (422, not 500).
- **`GlobalExceptionHandler`** — each thrown `ApiException`/validation error maps
  to the `{data:null, error:{code,message,fields}}` envelope with the right HTTP
  status; `422` carries field details; unknown exception → `INTERNAL_ERROR` 500
  (no stack leak).
- **`Inputs.uuid/uuidOrNull`** — valid, null, and malformed UUID handling.
- **`JwtAuthFilter`** — missing / expired / malformed / valid bearer →
  `UNAUTHENTICATED` vs authenticated; verifies the `@CurrentUser` resolution.
- Existing `RateLimitFilterTest`, `HardeningIntegrationTest`,
  `PasswordPolicyTest` — keep; add CORS preflight + CSP header assertions.

### 4.2 `medications` (catalog + scan)
- **`Gtin.canonicalize` (unit, exhaustive):** EAN-13 → 14 (prepend `0`), UPC-12 →
  14 (prepend `00`), 14 stays, strips non-digits, the 8 odd/non-numeric/length
  11&16 barcodes → `Optional.empty()` (search-only). Round-trips a known sample
  (`08681030190415`).
- **`MedicationService` (integration):** search by `q` (name/ingredient), cursor
  meta present; `byBarcode` resolves via canonical GTIN (and a raw barcode);
  404 → `BARCODE_NOT_FOUND`; `create` validation (422); `searchLeaflet` returns
  ranked hits; `get`/`leaflet` shape.

### 4.3 `medications/seed` + `ai/LeafletIndexer` (pure logic — high value)
- **`SourceText` placeholder detection:** the data-driven rules — `""`/`"-"` →
  not-real; `/(içerik|etken maddesi.*)?bulunamad[ıi]/i` variants → not-real;
  `length < 150` → not-real; a real ~15k leaflet → real. Same rule nulls a
  placeholder `active_ingredient`.
- **`LeafletParser.parse`:** maps the 5 numbered Turkish headings to `Section`
  ordinals 1–5; handles missing sections; the ~1% with no parseable sections →
  `unknown` fallback; `Block.charStart/charLen` provenance is correct.
- **`LeafletParser.toLeafletSections`:** lossy projection to `{dosage, side_effects,
  …}` keeps the right text per slot.
- **`LeafletIndexer` chunking:** a >1,800-char section sub-splits with ~15%
  overlap and sequential `chunk_index`; small sections stay whole; `section_ordinal`
  carried through.
- **`CatalogSeedRunner` (integration, tiny fixture JSON):** dedup on `gtin`
  (`ON CONFLICT DO NOTHING`, keep first); divergence logging when a conflicting
  row differs; idempotent re-run adds nothing; counts match (e.g. 5 rows, 1 dup
  → 4). Drive it with a 5–10 row fixture, **not** the 20k dataset.

### 4.4 `inventory` (per-box — just changed)
- **`UserMedicationService.create`:** sets `batch`/`serialNumber`; a second box of
  the same product+expiry with a **different** serial → two rows; the **same**
  serial → `409 INVENTORY_BATCH_EXISTS`; manual add (no serial) → allowed; DB
  5-column UNIQUE enforced; `toItem` returns batch/serial.
- **`update`** patches batch/serial/quantity/expiry; ownership (cross-user → 404).
- Existing `ExpiryStatusTest` — keep (boundary days).

### 4.5 `logs` (idempotency + atomicity — just changed)
- **Idempotent replay:** `POST /medication-logs` twice with the same
  `clientRequestId` → one log row, **one** decrement, second call returns the
  original result (assert quantity unchanged on the replay).
- **`INSUFFICIENT_STOCK`** when `quantityUsed > quantity` (422); immutability
  (no update endpoint); history date-range filter + limit/pagination.
- **Concurrency:** two parallel logs on the same box respect the pessimistic lock
  (`findByIdAndUserIdForUpdate`) and don't oversell (an `@Transactional` +
  threaded test, or document as a load-test assertion).

### 4.6 `reminders` / `ai` / `users` / `auth` / `notifications` / `admin`
- **Schedules:** create daily from `scheduledTimes`, pause/resume flips `active`,
  update times, ownership; existing `ScheduleIsDueTest` keep.
- **`RagService` (unit, no live key):** no API key → `DECLINE` + `grounded=false`;
  all hits below `min-score` → `DECLINE`; with mock chunks → citations emitted in
  **ordinal order**, deduped; a `truncated=true` hit injects the caveat into the
  prompt (assert via a captured prompt / a seam). Pre-gate: medication with no
  chunks → decline without calling the model.
- **Users:** `PATCH /me/preferences` validation (`@Min(0)`), `change-email`
  (wrong password → 401, taken → 409, success revokes sessions).
- **Auth (extend):** password-reset request/confirm (expiry, reuse), email
  verification (token expiry), Google login (mock verifier — valid/invalid/taken),
  2FA challenge at login, **account deletion** (PII anonymized, tokens revoked,
  KVKK), sessions list/revoke ownership. Existing rotation/lockout tests keep.
- **Notifications:** `ReminderScheduler` tick selects due schedules and dedupes
  (extend `NotificationDedupeTest`); low-stock/expiry → notification rows;
  `WebPushSender` payload (mock transport).
- **Admin:** every `/admin/**` route is `ADMIN`-only (USER → 403); lock/unlock,
  audit-logs, delete-user effects.

---

## 5. Frontend test plan (by layer)

### 5.1 `core/` (CRITICAL — untested)
- **`ApiClient` (with `http_mock_adapter`):** attaches `Authorization` when a token
  exists and skips it on auth-free paths; on `401` calls `/auth/refresh` **once**,
  persists the new pair, and **retries the original request**; refresh failure →
  `tokenStore.clear()` + `onSessionExpired()` fired + original error surfaces;
  concurrent 401s trigger a **single** refresh (the `_refreshing` guard);
  `getList`/`getOne`/`postJson` unwrap the envelope; non-2xx → typed
  `ApiException` with the right `code`/`fields`; transport error → `NETWORK_ERROR`.
- **`api_envelope` (extend the existing test):** meta parsing, missing fields,
  `ApiException.isUnauthenticated` matrix.
- **`TokenStore`:** mock `FlutterSecureStorage` — load/save/clear and the
  in-memory cache stay consistent; `hasRefreshToken`.
- **`ConnectivityService`:** maps `ConnectivityResult` lists to online/offline and
  emits transitions only on change.

### 5.2 `features/*` DTO parsing
Round-trip `fromJson` for: `InventoryItem` (incl. `batch`/`serialNumber`,
`expiryStatus`), `ScheduleView` (`reminderMinutes` from `["08:00",…]`,
`dailyBody`), `LogResult`/`LogView`, `CatalogMedication` + `CatalogMedicationDetail`
+ `LeafletSections.entries`/`isEmpty`, `LeafletSearchHit`, `AuthResult`/`AuthUser`,
`UserProfile` + `NotificationPreferences` (**snake_case** keys). Assert null-safety
and defaults.

### 5.3 `features/ai` SSE parser
`AiRepository.chat` with a fake byte stream: emits `TokenEvent`/`CitationEvent`/
`DoneEvent`; reassembles frames **split across chunk boundaries**; strips the
single leading data-space; `grounded:true/false`; ignores malformed frames;
completes on stream end.

### 5.4 `data/` sync engine (CRITICAL — untested)
- **`SqliteMedicationRepository` new methods (in-memory ffi):** `replaceServerMeds`
  upserts + prunes stale `synced` rows but **keeps `pending`** rows; outbox
  `enqueueOp`/`pendingOps`(ordered)/`deleteOp`; `markLogSynced`/`syncStateFor`;
  `unsyncedTakenLogs`; `wipe`.
- **`BackendMedicationRepository` (fake feature repos + fake `ConnectivityService`
  + in-memory mirror):**
  - **read online** → assembles `Medication` from inventory+schedules, mirrors,
    merges `kind`/`photoFile` from cache; **read offline** → serves the mirror;
    any read error → cache (never throws).
  - **insert online** → find-or-create catalog → inventory → schedule, returns the
    real id; **insert offline** → temp `local-` id + queued `create_medication`.
  - **upsertLog**: `taken` posts once with the **stable idempotency key**;
    already-`synced` taken → no re-post; `skipped`/`snoozed` → local only;
    `INSUFFICIENT_STOCK` rethrows.
  - **deleteMedication** removes schedules then inventory; local-id → mirror only.
  - **`drainOutbox`**: replays create/update/delete and unsynced taken logs;
    `_remapLogs` repoints temp-id logs to the real UUID; stops on `NETWORK_ERROR`,
    drops on `INSUFFICIENT_STOCK`/`404`.
  - **`_kindForForm`/`_formForKind`** mapping; `_logKey` ≤ 64 chars.

### 5.5 `services/` + `state/`
- `notification_service` — extend the existing id/ payload tests for `keyForId`
  bounds and the String-id payload; `cancelForMedication` no-ops on empty id.
- `AppState` (extend `fakes`): `init` `_booted` guard (second call re-syncs, no
  double listener); `signOutCleanup` wipes mirror + clears state; dose error
  propagation; `syncNow` drains then refreshes.
- `AuthState`: status transitions (`unknown→authenticated/unauthenticated`),
  `bootstrap` silent-restore success/failure, `onSessionExpired`, `describeAuthError`
  code→message matrix.

### 5.6 Widget tests (keep existing green; add new screens)
- **Keep** the 7 cabinet/schedule/history suites green after the String-id remap.
- **Add:** login & register (inline validation, backend `422`/`INVALID_CREDENTIALS`
  display), profile (preference steppers PATCH, sign-out), expiration list (fake
  `ExpirationRepository`), **leaflet detail** (fake `CatalogRepository`: sections
  render, read-aloud button present, guardrail when empty), **AI chat** (fake
  `AiRepository`: tokens stream into a bubble, citations chip, `grounded:false`
  guardrail), add-med (quantity/expiry inputs, barcode prefill via fake catalog),
  inventory chips + stock-error snackbar.

### 5.7 Golden tests
Cabinet screen, action panel (with inventory chips), leaflet card, auth scaffold —
captured at **375 px** and at **2.0× text scale** to lock the cabinet design and
catch a11y regressions. Gate with an `--update-goldens` review step.

### 5.8 i18n (once F1 lands)
- **ARB key parity test:** every key in `app_en.arb` exists in `app_tr.arb` (and
  vice-versa) — fails the build on a missing translation.
- Locale switch re-renders strings; `DateFormat`/`NumberFormat` localize; default
  follows device locale; override persists.

### 5.9 `integration_test/` (e2e)
A driver flow against the Compose backend (or a mock server): register → add med
(catalog+inventory+schedule created) → log a taken dose (quantity decrements;
`INSUFFICIENT_STOCK` path) → open leaflet/assistant → airplane-mode write →
reconnect drains. Run on an emulator in a nightly CI job (not per-PR).

---

## 6. Other tests

- **Contract:** a single `envelope.json` fixture asserted by both a backend
  `MockMvc` test and the Flutter parser test, so the `{data,meta,error}` shape
  can't drift between sides.
- **Load (Gatling or k6):** ramped scenario over `GET /user-medications`,
  `POST /medication-logs`, `POST /ai/chat`; assert **p95 < 300 ms** non-AI and
  **TTFT < 2 s** AI; run against Compose in a nightly job.
- **Security:** OWASP `dependency-check` (Maven) + `flutter pub outdated`;
  `gitleaks` secret scan in CI; an OWASP ZAP baseline scan against the Compose
  stack; **authz tests** (admin-only routes, strict cross-user isolation on every
  owned resource).
- **Accessibility:** Flutter `Semantics` finder tests (find controls by label),
  plus a manual TalkBack/VoiceOver + 200%-font checklist tied to F8.
- **Mutation (optional, P2):** PIT on backend `*Service` to grade assert quality.

---

## 7. Known flaws to verify (and likely fix) via tests

1. **Lossy un-toggle** — clearing a *synced* taken dose only deletes the local
   row; the server already decremented. Test documents it; consider a server
   "void/correction" endpoint later.
2. **`_remapLogs` date sentinel** — uses `logsSince('0000-00-00')` to scan all
   logs; fragile. Test the full offline-create drain and harden the query.
3. **Notification key collision** — `id.hashCode % 20000000` can collide across
   meds. Test bounds + within-med uniqueness (exists); document the cross-med risk.
4. **Idempotency end-to-end** — client stable key + server replay must not
   double-decrement (backend + e2e test).
5. **Per-box serial dedup** vs a legitimate second box — integration test both.
6. **GTIN canonicalization** of the 8 odd barcodes → `NULL`/search-only.
7. **RAG decline & truncation caveat** — never generate when ungrounded.
8. **`AppState` double-init / `signOutCleanup`** — re-login doesn't double-wire;
   sign-out wipes the mirror.
9. **TokenStore cache vs storage** consistency on refresh.
10. **AI screen without keys** — degrades to the "unavailable" banner, no crash.

---

## 8. CI integration & coverage gates

- **`backend.yml`:** `./mvnw verify` already runs the suite; add a JaCoCo report +
  `jacoco:check` (start as report, then enforce ≥80% on service/web); add a
  scheduled `dependency-check` job (non-gating first).
- **`frontend.yml`:** `flutter test --coverage` → upload lcov; add the ARB
  key-parity test; run goldens (PR-gating once stable).
- **Nightly workflow:** Flutter `integration_test` on an emulator, Gatling/k6
  load, and the ZAP baseline.

---

## 9. Execution order (priority = risk × recency-of-change)

1. **P0** (riskiest, recently changed, zero coverage): Flutter **sync engine** +
   **`ApiClient` refresh-retry** + **idempotency** (client+server) + **`Gtin`** +
   **`LeafletParser`/`SourceText`** + **`RagService` decline/caveat** + **per-box
   inventory**.
2. **P1:** feature DTO parsing, SSE parser, auth flows (FE+BE), schedule/inventory/
   logs integration, `MedicationService`, `CursorCodec`/exception envelope, new
   widget tests (login/profile/expiration/leaflet/AI), JaCoCo + lcov gates.
3. **P2:** golden tests, i18n key-parity (post-F1), `integration_test` e2e, load,
   ZAP/dependency-check/gitleaks, mutation testing.

---

## 10. Verification (how to run)

```bash
# Backend
cd backend && ./mvnw verify                 # unit + Testcontainers integration
cd backend && ./mvnw jacoco:report          # → target/site/jacoco/index.html

# Frontend
cd frontend && flutter test --coverage      # unit + widget; → coverage/lcov.info
cd frontend && flutter test integration_test --dart-define=API_BASE_URL=…  # e2e (emulator)
```

Green = all suites pass, coverage ≥ targets (§3), goldens unchanged, ARB keys at
parity, and each §7 flaw has a test pinning its current (or fixed) behavior.
