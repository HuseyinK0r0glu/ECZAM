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

ECZAM is a **smart medication management Progressive Web App (PWA)** that makes
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
| **Frontend** | React 18 + **TypeScript** + Vite, React Router v6, TanStack Query (server state), Zustand (client state), Tailwind CSS |
| **PWA** | Vite PWA plugin (service worker), Web App Manifest, Web Push API |
| **Scheduler** | Spring `@Scheduled` / Quartz (per-minute tick); Redis optional |
| **AI** | Anthropic API `claude-sonnet-4-6` (assistant); OpenAI `text-embedding-3-small` or a local model (embeddings) |
| **Notifications** | Web Push (VAPID) from the backend; optional email via Spring Mail |

> **Stack note:** the brief originally suggested a Node/NestJS or Rust/Axum backend.
> This project deliberately uses **Spring Boot + PostgreSQL** instead. The REST
> contract, DB schema, and domain logic in the brief still apply verbatim — only the
> implementation framework changed. See [`docs/system-architecture.md`](docs/system-architecture.md).

---

## 3. Repository layout

```
ECZAM/
├── ECZAM_PROJECT_BRIEF.md   # authoritative product/technical spec (source of truth)
├── CLAUDE.md                # this file
├── docs/                    # full documentation suite (see docs/README.md)
├── plans/                   # planning artifacts
├── backend/                 # Spring Boot service        (created once scaffolded)
└── frontend/                # React + TS PWA             (created once scaffolded)
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

**Frontend structure** is feature-based: `components/`, `pages/`, `features/`
(medications, reminders, inventory, expiration, ai-assistant), `services/`,
`hooks/` (e.g. `useTTS`, `useBarcode`, `useNotifications`), `contexts/`, `routes/`,
`utils/`.

---

## 4. Build / run / test commands

> Marked **once scaffolded** — code does not exist yet. Add real commands here when
> the projects are created.

```bash
# Backend (once scaffolded)
cd backend && ./mvnw spring-boot:run      # run API
cd backend && ./mvnw test                 # unit + integration tests (Testcontainers)
cd backend && ./mvnw clean package        # build jar

# Frontend (once scaffolded)
cd frontend && npm run dev                # Vite dev server (http://localhost:5173)
cd frontend && npm test                   # Vitest unit/component tests
cd frontend && npm run build              # production PWA build
```

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
- **Accessibility:** WCAG 2.1 AA minimum; fully functional at 375px; user font
  scaling respected; keyboard focus indicators; TTS keyboard-accessible.
- **Compliance:** **KVKK** (Turkey) — health data is special-category personal data.
  See [`docs/security-requirements.md`](docs/security-requirements.md).
- **Performance:** p95 < 300ms for non-AI endpoints; AI time-to-first-token < 2s.

---

## 9. Where to look

- Product spec & non-negotiables → [`ECZAM_PROJECT_BRIEF.md`](ECZAM_PROJECT_BRIEF.md)
- Documentation index & reading order → [`docs/README.md`](docs/README.md)
- When adding a feature: find its `FR-###` / `US-###` / `UC-###` in the docs, keep
  IDs consistent, and update the traceability matrix in `docs/README.md`.
