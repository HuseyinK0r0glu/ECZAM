# ECZAM end-to-end tests

Real HTTP tests against a **live, running** ECZAM backend — not MockMvc or
Testcontainers (those already exist under `backend/src/test`, 96 tests). This
suite hits an actual deployed instance over the network, using the real seeded
medication catalog, to catch things that only show up when the whole system is
actually wired together: routing, JSON (de)serialization, Postgres parameter
typing, security filter behavior on async/streaming responses, rate limiting,
and so on.

## Running it

```bash
cd backend && docker compose up -d   # bring up db + backend if not already running
python3 backend/e2e/run_e2e.py
```

Env vars:

- `E2E_BASE_URL` — API base, default `http://localhost:8090/api/v1` (matches
  this machine's local port remap in `docker-compose.override.yml`; use
  `http://localhost:8080/api/v1` if you don't have that override).

No `pytest` dependency — this is a small self-contained runner (stdlib +
`requests`). Each `test_*` function runs in isolation; failures are collected
and printed with a reason, and the process exits non-zero if anything failed.

## Design notes

- Most tests share **one** registered user (`shared_ctx()`) instead of
  registering a fresh account each time, because `POST /auth/register` and
  `POST /auth/login` share one fixed-window rate-limit bucket
  (`eczam.ratelimit.auth-per-minute`, default 10/min per IP — see
  `RateLimitFilter`). A test that needs real isolation (two distinct users, or
  exercising registration/login/lockout mechanics themselves) still registers
  its own account. `register()`/`auth_post()` retry once after a 60s backoff
  if they do hit the rate limit.
- Real fixture data (medication ids/GTINs) is pulled from the seeded
  20,471-row Tip-Atlası catalog rather than invented — see the `MED_WITH_GTIN` /
  `MED_WITH_LEAFLET` / `ASPIRIN_IDS` constants at the top of `run_e2e.py`. If
  the catalog is ever reseeded from scratch these should still resolve, since
  the seed is idempotent and deterministic over the same source file — but if
  a fixture ever goes missing, re-derive it with a query like:
  `docker exec -i eczam-db psql -U eczam -d eczam -t -c "SELECT id, name, gtin FROM medications WHERE gtin IS NOT NULL LIMIT 3;"`
- `test_ai_chat_grounded_answer_cites_a_leaflet_section` prints an inline
  `SKIP (env)` note instead of failing if `backend/.env`'s `OPENAI_API_KEY`
  isn't a real key (embeddings fail, so the RAG pipeline can never ground an
  answer) — a known environment gap, not a code issue. Supply a real key and
  re-run for full coverage of the grounded path.

## Real bugs this suite found and fixed (2026-09-26)

Writing and running this suite against the live stack surfaced several bugs
that the existing MockMvc/Testcontainers suite hadn't caught:

1. **`GET /medications?q=` 500'd whenever `q` was blank/absent** — Postgres
   couldn't infer a type for the null `:q` JPQL parameter inside `CONCAT()`,
   resolving `LOWER(...)` to its `bytea` overload. Fixed with an explicit
   `CAST(:q AS string)` in `MedicationRepository.search`.
2. **`GET /medication-logs?userMedicationId=...` 500'd whenever `from`/`to`
   were omitted** — same class of bug (`could not determine data type of
   parameter $2`) in `MedicationLogRepository.history`. Fixed with
   `CAST(:from/:to AS timestamp)`.
3. **Any unmapped/mistyped route returned a 500** instead of a 404
   (`NoResourceFoundException` fell through to the generic exception handler).
   Added a dedicated handler in `GlobalExceptionHandler`.
4. **A malformed request body (e.g. an enum field with an out-of-range value)
   returned a 500** instead of a 422 (`HttpMessageNotReadableException` wasn't
   mapped). Added it alongside the existing `MethodArgumentTypeMismatchException`/
   `DateTimeParseException` → 422 handler.
5. **The AI chat SSE endpoint (`POST /ai/chat`) could return a 401 that had
   nothing to do with authentication** — an upstream RAG/embedding failure
   inside the background-executor thread called `emitter.completeWithError()`,
   which triggers a second, error-flavored async dispatch that re-enters the
   whole servlet filter chain on a thread with no propagated security context;
   Spring Security rejected it as unauthenticated and overwrote the response.
   Fixed two ways: `ChatController` now degrades any RAG failure to the same
   `done:{"grounded":false}` guardrail path a client already handles instead of
   `completeWithError()`, and `JwtAuthFilter` no longer skips Spring's async/
   error dispatch types (`shouldNotFilterAsyncDispatch()`/
   `shouldNotFilterErrorDispatch()`), so if something else ever does trigger
   that path, authentication is correctly re-derived from the still-present
   `Authorization` header instead of coming up empty.
6. **The generic exception handler logged nothing** (`GlobalExceptionHandler
   .handleOther`) — every unmapped exception returned a bare 500 with zero
   trace in the logs, which is what made bugs 1–5 so slow to diagnose in the
   first place. Added `log.error(...)`.
7. **`backend/.env`'s `OPENAI_API_KEY` is not a valid key** (see the skip note
   above) — flagged here since it silently breaks the RAG assistant's grounding
   entirely; not something this suite can fix, needs a real key supplied.

All backend unit/integration tests (`./mvnw verify`, 96 tests) and this e2e
suite (45 tests) pass after these fixes.
