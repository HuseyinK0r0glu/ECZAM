# ECZAM

**Smart medication management PWA** — helps elderly and chronic-condition patients (and
their caregivers) manage the full medication lifecycle: inventory, dose scheduling and
logging, expiration monitoring, leaflet search with text-to-speech, and a leaflet-grounded
AI assistant.

> **Stack:** Java 21 · Spring Boot 3.2+ · PostgreSQL + pgvector (backend) · React 18 ·
> TypeScript · Vite PWA (frontend) · **Compliance:** KVKK (Turkey).

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
├── docs/                    # documentation suite (start at docs/README.md)
├── plans/                   # phase-by-phase implementation plans (start at 00-overview.md)
├── backend/                 # Spring Boot service (package com.eczam) — built in Phase 1+
│   ├── docker-compose.yml   # dev PostgreSQL + pgvector
│   └── .env.example         # backend env template
└── frontend/                # React 18 + TS PWA — built in Phase 1+
    └── .env.example         # frontend env template
```

Backend layering per domain: `controller → service → repository → entity → dto → mapper`
(see [docs/system-architecture.md](docs/system-architecture.md)). Frontend is feature-based
(`pages/`, `features/`, `services/`, `hooks/`, `contexts/`, `routes/`).

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 (Temurin) |
| Node | 20+ |
| Docker | for the dev database |
| Maven | via the `./mvnw` wrapper (added in Phase 1) |

```bash
java -version    # 21
node -v          # 20+
docker -v
```

## Setup

```bash
# 1. Configure environment (copy templates, then fill in secrets)
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env

# 2. Start the dev database (PostgreSQL + pgvector)
cd backend && docker compose up -d
```

Generate the secrets referenced in `backend/.env`:

```bash
openssl rand -base64 48                 # JWT_SECRET
npx web-push generate-vapid-keys        # VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY (Phase 4)
```

Never commit a real `.env` — the root [.gitignore](.gitignore) excludes it and keeps only
the `.env.example` templates.

## Run

> The `backend/` and `frontend/` projects are scaffolded in **Phase 1**
> ([plans/phase-1-foundation.md](plans/phase-1-foundation.md)). Once scaffolded:

```bash
# Terminal 1 — database
cd backend && docker compose up -d

# Terminal 2 — backend API  → http://localhost:8080  (base path /api/v1)
cd backend && ./mvnw spring-boot:run

# Terminal 3 — frontend PWA → http://localhost:5173
cd frontend && npm install && npm run dev
```

Tests:

```bash
cd backend  && ./mvnw test    # JUnit 5 + Testcontainers
cd frontend && npm test       # Vitest
```

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
