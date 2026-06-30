# ECZAM

**Smart medication management app** — helps elderly and chronic-condition patients (and
their caregivers) manage the full medication lifecycle: inventory, dose scheduling and
logging, expiration monitoring, leaflet search with text-to-speech, and a leaflet-grounded
AI assistant.

> **Stack:** Java 21 · Spring Boot 3.2+ · PostgreSQL + pgvector (backend) · **Flutter**
> (mobile client) · **Compliance:** KVKK (Turkey).
>
> The frontend was migrated from a React/TS PWA to **Flutter** (see
> [plans/flutter-migration-plan.md](plans/flutter-migration-plan.md)); the archived React
> app lives in `old_frontend_react/`.

**New here? Read in this order:**
[ECZAM_PROJECT_BRIEF.md](ECZAM_PROJECT_BRIEF.md) (source of truth) →
[CLAUDE.md](CLAUDE.md) (working guide) →
[docs/](docs/README.md) (full spec) →
[plans/](plans/00-overview.md) (implementation plans).

---

## Repository layout

```
ECZAM/
├── ECZAM_PROJECT_BRIEF.md   # authoritative product/technical spec
├── CLAUDE.md                # guide for AI-assisted development
├── Makefile                 # dev task runner (`make help`)
├── docs/                    # documentation suite (start at docs/README.md)
├── plans/                   # implementation + migration + testing plans
├── backend/                 # Spring Boot service (package com.eczam)
│   ├── docker-compose.yml   # dev stack: PostgreSQL+pgvector + the API
│   ├── Dockerfile           # backend image (multi-stage)
│   ├── api.http             # runnable REST request collection
│   └── .env.example         # backend env template
├── frontend/                # Flutter app (Dart) — the active client
└── old_frontend_react/      # archived React + TS PWA
```

Backend layering per domain: `controller → service → repository → entity → dto → mapper`
(see [docs/system-architecture.md](docs/system-architecture.md)). Flutter client is
layered `core/` (networking, sync) · `features/` (DTOs + repositories) · `data/` (SQLite
mirror) · `state/` (Provider) · `ui/` (screens) — see [CLAUDE.md](CLAUDE.md) §3.

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 (Temurin) |
| Docker | for the dev database / stack |
| Flutter | 3.x (Dart 3) for the client |
| Maven | via the `./mvnw` wrapper |

## Quick start

```bash
# 1. Configure backend secrets (copy template, then fill in)
cp backend/.env.example backend/.env
openssl rand -base64 48          # → JWT_SECRET

# 2. Bring up the whole backend stack (Postgres+pgvector + API on :8080)
make up                          # = cd backend && docker compose up --build
#   …or just the DB and run the API from your IDE:
make db-up && make backend
#   …with a few sample medicines to search/scan:
make seed-sample

# 3. Run the Flutter client against the local backend
make flutter-get && make flutter-run
```

`make help` lists every task. Poke the API directly with `backend/api.http` (VS Code
REST Client / IntelliJ HTTP client). Never commit a real `.env` — the root
[.gitignore](.gitignore) keeps only the `.env.example` templates.

## Tests

```bash
make backend-test     # JUnit 5 + Testcontainers (coverage → backend/target/site/jacoco)
make flutter-test     # flutter test --coverage
```

See [plans/testing-plan.md](plans/testing-plan.md) for the full strategy and gaps.

## Build order

The product is built as working vertical slices, in order:

```
00 Overview → 1 Foundation → 2 Inventory → 3 Scheduling+Logging
           → 4 Notifications → 5 AI+TTS → 6 PWA+Polish
```

Each phase is detailed (with full code) in [plans/](plans/00-overview.md). MVP scope,
phase exit criteria, and the demo script are in
[docs/mvp-definition.md](docs/mvp-definition.md).

## Conventions

- REST under `/api/v1`; uniform `{ data, meta, error }` response envelope; cursor pagination.
- UUID primary keys; `TIMESTAMPTZ` timestamps; parameterized queries only.
- `Authorization: Bearer <JWT>` on protected endpoints; bcrypt password hashing.
- Accessibility: WCAG 2.1 AA, functional at 375px, TTS first-class.
- AI assistant answers **only** from retrieved leaflet passages — never general medical advice.

See [CLAUDE.md](CLAUDE.md) for the full set.
