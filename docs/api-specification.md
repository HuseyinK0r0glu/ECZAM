# ECZAM — API Specification

> The REST API contract: conventions, the response envelope, auth, error handling,
> and the endpoint catalog by resource (translated to Spring `@RestController`
> signatures). Stack-agnostic in shape; Spring Boot in implementation.

**Status:** Draft · **Owner:** Eng · **Last updated:** 2026-06-18
**Related:** [system-architecture.md](system-architecture.md) · [database-design.md](database-design.md) · [functional-requirements.md](functional-requirements.md) · [security-requirements.md](security-requirements.md)

---

## 1. Conventions — *brief §5*

- **Base path:** all endpoints under **`/api/v1`**.
- **Resource-oriented**, RESTful URLs; plural nouns.
- **Auth:** authenticated endpoints require `Authorization: Bearer <JWT>`.
- **Content type:** `application/json` (except the SSE chat stream:
  `text/event-stream`).
- **Validation:** every endpoint validates input; failures → **422** with
  field-level errors.
- **Pagination:** all list endpoints use **cursor-based** pagination
  (`?cursor=&limit=`); the next cursor is returned in `meta`.

### 1.1 Response envelope

Every response uses `{ data, meta, error }`:

```json
// success (single)
{ "data": { "id": "…", "name": "…" }, "meta": null, "error": null }

// success (list, paginated)
{ "data": [ /* items */ ],
  "meta": { "nextCursor": "eyJpZCI6…", "limit": 20 },
  "error": null }

// error
{ "data": null, "meta": null,
  "error": { "code": "VALIDATION_FAILED", "message": "…",
             "fields": { "email": "must be a valid email" } } }
```

### 1.2 Status codes

| Code | Meaning |
|---|---|
| 200 | OK |
| 201 | Created |
| 204 | No Content (deletes) |
| 400 | Malformed request |
| 401 | Unauthenticated / invalid token |
| 403 | Authenticated but not allowed |
| 404 | Resource not found (also: barcode not found anywhere → manual entry) |
| 409 | Conflict (e.g. duplicate email, duplicate inventory batch) |
| 422 | Validation failed (with `error.fields`) |
| 429 | Rate limited |
| 500 | Unexpected server error |

### 1.3 Error codes (representative)

`VALIDATION_FAILED`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `FORBIDDEN`,
`NOT_FOUND`, `EMAIL_TAKEN`, `INVENTORY_BATCH_EXISTS`, `INSUFFICIENT_STOCK`,
`BARCODE_NOT_FOUND`, `RESET_TOKEN_INVALID`, `RATE_LIMITED`, `INTERNAL_ERROR`.

## 2. Authentication & users

| Method | Path | Auth | Purpose | FRs |
|---|---|:--:|---|---|
| POST | `/api/v1/auth/register` | – | Register (email, password, displayName) → user + token | FR-001 |
| POST | `/api/v1/auth/login` | – | Login → access token (+ refresh) | FR-002,003 |
| POST | `/api/v1/auth/refresh` | – | Exchange refresh token for a new access token | FR-003 |
| POST | `/api/v1/auth/logout` | ✓ | Invalidate client session | FR-006 |
| POST | `/api/v1/auth/password-reset/request` | – | Send reset email (always 204, non-enumerating) | FR-004 |
| POST | `/api/v1/auth/password-reset/confirm` | – | Set new password via reset token | FR-004 |
| GET | `/api/v1/users/me` | ✓ | Current profile + preferences | FR-005 |
| PATCH | `/api/v1/users/me` | ✓ | Update displayName | FR-005 |
| PATCH | `/api/v1/users/me/preferences` | ✓ | Update notification preferences (push, email, low_stock_threshold, expiry_warning_days) | FR-005 |

```jsonc
// POST /api/v1/auth/register  (request)
{ "email": "ayse@example.com", "password": "•••••••••", "displayName": "Ayşe" }
// 201 → { "data": { "user": { "id": "…", "email": "…" }, "accessToken": "…", "refreshToken": "…" } }
// 409 EMAIL_TAKEN · 422 VALIDATION_FAILED
```

## 3. Medication catalog

| Method | Path | Auth | Purpose | FRs |
|---|---|:--:|---|---|
| GET | `/api/v1/medications` | ✓ | Search/list catalog (`?q=&cursor=&limit=`) | FR-010 |
| GET | `/api/v1/medications/{id}` | ✓ | Catalog medication detail | FR-010,015 |
| POST | `/api/v1/medications` | ✓ | Create catalog medication (manual) | FR-011 |
| GET | `/api/v1/medications/barcode/{code}` | ✓ | Lookup by barcode (local → OpenFDA fallback → ingest) | FR-012,013,014 |
| GET | `/api/v1/medications/{id}/leaflet` | ✓ | Structured leaflet sections | FR-015,060 |
| GET | `/api/v1/medications/{id}/leaflet/search` | ✓ | Full-text search across leaflet sections (`?q=`) | FR-061 |

```jsonc
// GET /api/v1/medications/barcode/8699{...}
// 200 → { "data": { "id": "…", "name": "…", "barcode": "…", "vector_indexed": false } }
// 404 BARCODE_NOT_FOUND → client falls back to manual entry (UC-003)
```

## 4. Inventory (`user-medications`)

| Method | Path | Auth | Purpose | FRs |
|---|---|:--:|---|---|
| GET | `/api/v1/user-medications` | ✓ | List my inventory (`?status=low|expiring|expired&cursor=&limit=`) with low-stock + expiry flags | FR-023,024,026 |
| POST | `/api/v1/user-medications` | ✓ | Add inventory entry (medicationId, quantity, unit, expirationDate, notes) | FR-020,025 |
| GET | `/api/v1/user-medications/{id}` | ✓ | Inventory entry detail (with schedules + recent logs) | FR-023 |
| PATCH | `/api/v1/user-medications/{id}` | ✓ | Edit quantity/unit/expiry/notes | FR-021 |
| DELETE | `/api/v1/user-medications/{id}` | ✓ | Delete entry (cascades schedules + logs) | FR-022 |

```jsonc
// POST /api/v1/user-medications  (request)
{ "medicationId": "…", "quantity": 30, "unit": "pill",
  "expirationDate": "2027-01-31", "notes": "morning batch" }
// 201 → { "data": { "id": "…", "quantity": 30, "lowStock": false, "expiryStatus": "ok" } }
// 409 INVENTORY_BATCH_EXISTS (same user+medication+expiry)
```

## 5. Schedules

| Method | Path | Auth | Purpose | FRs |
|---|---|:--:|---|---|
| GET | `/api/v1/schedules` | ✓ | All schedules (active + paused) across meds | FR-036 |
| GET | `/api/v1/user-medications/{id}/schedules` | ✓ | Schedules for one inventory entry | FR-036 |
| POST | `/api/v1/user-medications/{id}/schedules` | ✓ | Create schedule | FR-030,031,032 |
| PATCH | `/api/v1/schedules/{id}` | ✓ | Edit schedule | FR-033 |
| POST | `/api/v1/schedules/{id}/pause` | ✓ | Pause (active=false) | FR-034 |
| POST | `/api/v1/schedules/{id}/resume` | ✓ | Resume (active=true) | FR-034 |
| DELETE | `/api/v1/schedules/{id}` | ✓ | Delete schedule | FR-035 |

```jsonc
// POST /api/v1/user-medications/{id}/schedules  (request)
{ "dosageAmount": 1, "frequencyType": "daily",
  "scheduledTimes": ["08:00", "20:00"], "startsOn": "2026-06-18" }
// weekly:   "frequencyType":"weekly", "daysOfWeek":[1,3,5]
// interval: "frequencyType":"interval", "frequencyValue":2
// 201 → { "data": { "id": "…", "active": true } } · 422 invalid combination
```

## 6. Dose logs

| Method | Path | Auth | Purpose | FRs |
|---|---|:--:|---|---|
| POST | `/api/v1/medication-logs` | ✓ | Log a dose → **atomic** insert + inventory decrement | FR-040,041,042,043 |
| GET | `/api/v1/medication-logs` | ✓ | History (`?userMedicationId=&from=&to=&cursor=&limit=`) | FR-044 |

```jsonc
// POST /api/v1/medication-logs  (request)
{ "userMedicationId": "…", "quantityUsed": 1, "scheduleId": "…", "notes": null }
// 201 → { "data": { "log": { "id": "…", "takenAt": "…" }, "newQuantity": 29, "lowStock": false } }
// 422 INSUFFICIENT_STOCK (quantity would go negative)
```

## 7. Expiration

| Method | Path | Auth | Purpose | FRs |
|---|---|:--:|---|---|
| GET | `/api/v1/expiration/expiring-soon` | ✓ | Entries within `expiry_warning_days` (`?days=` override) | FR-050,052 |
| GET | `/api/v1/expiration/expired` | ✓ | Already-expired entries still in stock | FR-051 |

## 8. Notifications / push

| Method | Path | Auth | Purpose | FRs |
|---|---|:--:|---|---|
| GET | `/api/v1/push/vapid-public-key` | ✓ | VAPID public key for client subscription | FR-090 |
| POST | `/api/v1/push/subscriptions` | ✓ | Store a push subscription (endpoint, p256dh, auth, userAgent) | FR-090 |
| DELETE | `/api/v1/push/subscriptions/{id}` | ✓ | Unsubscribe a device | FR-096 |

```jsonc
// POST /api/v1/push/subscriptions  (request — Web Push subscription object)
{ "endpoint": "https://push…", "keys": { "p256dh": "…", "auth": "…" },
  "userAgent": "Chrome/…" }
// 201 → { "data": { "id": "…" } }
```

## 9. AI assistant (RAG, streaming)

| Method | Path | Auth | Purpose | FRs |
|---|---|:--:|---|---|
| POST | `/api/v1/ai/chat` | ✓ | Ask a question; **SSE stream** of the grounded answer | FR-070…077 |

```jsonc
// POST /api/v1/ai/chat   Accept: text/event-stream   (request)
{ "message": "Bu ilacın yan etkileri neler?",
  "medicationId": "…",            // optional: scope retrieval to one medication
  "history": [ { "role": "user", "content": "…" }, { "role": "assistant", "content": "…" } ] }

// Response: text/event-stream
// event: token        data: {"delta":"Yan "}
// event: token        data: {"delta":"etkiler..."}
// event: citation     data: {"section":"side_effects","medicationId":"…"}
// event: done         data: {"grounded":true}
// (ungrounded → a single message declining + referring to a pharmacist/physician)
```

The endpoint embeds the query, runs top-k vector search over `leaflet_chunks`
(optionally filtered by `medicationId`), assembles context + history, and streams the
LLM response with citations. Pipeline: [system-architecture.md](system-architecture.md)
§4; flow: [use-cases.md](use-cases.md) UC-009.

## 10. Auth model & security

- **JWT bearer** access tokens (expiry per `JWT_EXPIRES_IN`, default 7d); refresh via
  `/auth/refresh`. Passwords hashed with **bcrypt**.
- **Authorization:** every resource is scoped to the authenticated user; a user may
  only access their own `user-medications`, schedules, logs, and subscriptions
  (catalog `medications` is shared/read-mostly).
- **CORS** restricted to `FRONTEND_URL`; standard security headers applied.
- **Rate limiting** on auth and AI endpoints (mitigate brute force / abuse) → 429.
- Full controls: [security-requirements.md](security-requirements.md).

## 11. OpenAPI

The contract is published via **springdoc-openapi** (OpenAPI 3) at
`/api/v1/openapi.json` with Swagger UI at `/swagger-ui` once the backend is
scaffolded. The tables above are the human-readable source; the generated spec is the
machine-readable mirror kept in sync with the controllers.

```yaml
# illustrative fragment
paths:
  /api/v1/medication-logs:
    post:
      summary: Log a dose (atomic insert + inventory decrement)
      security: [ { bearerAuth: [] } ]
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CreateDoseLogRequest' }
      responses:
        '201': { description: Created }
        '422': { description: Insufficient stock / validation failed }
```
