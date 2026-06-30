# ECZAM — Project Brief & Technical Specification

> This document is the single source of truth for the ECZAM project. It is written for Claude Code and any AI-assisted development agent. Every implementation decision, architecture choice, and feature priority described here is intentional. Do not deviate from this specification without an explicit instruction to do so.

---

## 1. What Is ECZAM?

ECZAM is a **smart medication management platform** built as a Progressive Web Application (PWA). Its mission is to make medication management safe, organized, and genuinely accessible — especially for elderly patients and individuals living with chronic conditions, groups who face the highest risk from medication non-adherence, accidental overdose, and unsafe use of expired medicines.

Today, millions of people manage their medications reactively: they remember to take a pill when they feel sick, notice an empty box only at the pharmacy, and never read the leaflet because it is printed in 6-point font across eight dense pages. ECZAM replaces this fragmented, error-prone routine with a unified digital assistant that handles the full medication lifecycle — from adding a medicine to the inventory, through scheduling doses and logging consumption, to warning users when something is running low or has expired.

The platform has four core pillars:

1. **Medication Adherence** — Smart scheduling and automated reminders so users never miss a dose.
2. **Inventory Management** — Real-time tracking of pill counts, automatically decremented with each logged dose, with low-stock alerts before the user runs out.
3. **Expiration Monitoring** — Proactive warnings before medications expire and active flags for expired items still stored in the inventory.
4. **Intelligent Information Access** — A searchable, voice-enabled interface over medication leaflets, replacing dense printed text with a conversational experience that surfaces exactly what the user needs to know.

ECZAM is engineered as a PWA to eliminate the app store barrier entirely. Users install it directly from the browser, receive push notifications, and interact with it exactly as they would a native mobile application — with no download, no account required to begin, and no platform lock-in.

The long-term vision is for ECZAM to become a comprehensive digital medication companion: one that not only reminds users what to take, but helps them understand every medicine they own, manage a shared household pharmacy, and make informed decisions throughout the entire lifecycle of their medications.

---

## 2. Who Is This For?

### Primary Users
- **Elderly patients** managing multiple chronic-condition medications (polypharmacy), often with poor eyesight, low digital literacy, and high risk of adherence failure.
- **Chronic condition patients** (diabetes, hypertension, asthma, thyroid, etc.) who take daily medications and need reliable tracking of dosage schedules and remaining stock.
- **Caregivers and family members** who manage medications on behalf of elderly relatives.

### Secondary Users
- Any adult who wants a structured, searchable record of their household medications and expiration dates.

### Design Implications
The UI must prioritize:
- Large, readable text with high contrast
- Simple, unambiguous navigation
- Minimal steps to complete frequent actions (log a dose, check inventory, search a medication)
- Voice output as a first-class feature, not an afterthought

---

## 3. Core Features (MVP Scope)

### 3.1 User Authentication
- Email / password registration and login
- JWT-based session management
- Password reset via email

### 3.2 Medication Inventory
- Add medications to personal inventory (manual entry or barcode scan)
- Store: medication name, manufacturer, barcode, quantity, expiration date, leaflet content
- Edit and delete inventory entries
- Real-time quantity display with visual low-stock indicators

### 3.3 Dose Scheduling & Reminders
- Create schedules per medication: dosage amount, frequency, and time(s) of day
- Pause and resume schedules
- Browser push notifications and PWA notifications for scheduled doses
- Optional email reminders

### 3.4 Dose Logging
- One-tap dose logging ("Taken" action from a reminder or the medication detail screen)
- Each logged dose automatically decrements the inventory quantity
- Full consumption history per medication

### 3.5 Expiration Monitoring
- Dashboard section showing medications expiring within configurable thresholds (e.g., 30 days, 7 days)
- Active warning flag for already-expired medications still in inventory
- Push notification alerts for upcoming expirations

### 3.6 Medication Information & Leaflet Access
- Structured storage of medication leaflet content (extracted and indexed at ingestion time)
- Full-text search across leaflet sections: dosage, side effects, contraindications, storage, interactions, missed dose instructions
- Text-to-speech (TTS) playback for any leaflet section — users can have content read aloud

### 3.7 AI Chat Assistant
- Conversational interface where users can ask natural language questions about any medication they own
- RAG pipeline: query is embedded → semantic search over leaflet vector database → top-k passages retrieved → LLM synthesizes a response
- Strict scope: the assistant answers only about medications present in the system's leaflet database; it does not give general medical advice

### 3.8 Barcode / DataMatrix Scanning
- Camera-based scanning from the browser (using a JS barcode library)
- On successful scan: lookup medication by barcode → auto-fill the Add Medication form
- Graceful fallback to manual entry if the barcode is not found in the database

---

## 4. Frontend Architecture

> **⚠️ Implemented as Flutter, not React.** The frontend was migrated from the
> React/PWA stack described below to a **Flutter** mobile app
> (see [`plans/flutter-migration-plan.md`](plans/flutter-migration-plan.md); archived
> React app in `old_frontend_react/`). The mapping: React→Flutter widgets,
> TanStack Query/Zustand→**Provider**, React Router→in-app navigation,
> Tailwind→Material 3/theme, Axios/fetch→**Dio**, Web Speech→**`flutter_tts`**,
> html5-qrcode→**`mobile_scanner`**, service worker/Web Push→on-device
> **`flutter_local_notifications`** (FCM is a documented gap). The REST contract,
> data model, and feature set are unchanged. The sections below remain the
> original spec for reference.

**Stack (original spec):** React 18+ with TypeScript, Vite as the build tool, React Router v6 for routing, TanStack Query (React Query) for server state, Zustand for lightweight client state, Tailwind CSS for styling.

**Stack (as built):** Flutter (Dart), Material 3, Provider for state, Dio for HTTP (with auth/refresh interceptors and SSE streaming), sqflite as an offline mirror cache + outbox, `flutter_secure_storage` for JWTs, `flutter_local_notifications` for reminders, `flutter_tts` for read-aloud, `mobile_scanner` for barcodes.

**PWA (original spec):** Service worker via Vite PWA plugin, Web App Manifest for installability, Web Push API for notifications. In the Flutter build, installability is native and reminders are local notifications.

**Accessibility targets:** WCAG 2.1 AA-equivalent. Font sizes must respect the OS font-scale setting. All interactive elements must expose semantics/focus. TTS controls must be reachable via a labelled control.

### 4.1 Directory Structure

```
src/
├── components/          # Shared, reusable UI components (Button, Modal, Badge, etc.)
├── pages/               # Top-level route components
│   ├── Dashboard.tsx
│   ├── Inventory.tsx
│   ├── MedicationDetail.tsx
│   ├── Schedules.tsx
│   ├── Logs.tsx
│   ├── Expiration.tsx
│   ├── AiAssistant.tsx
│   ├── Login.tsx
│   └── Register.tsx
├── features/
│   ├── medications/     # Inventory CRUD, barcode scan, leaflet viewer
│   ├── reminders/       # Schedule management, notification triggers
│   ├── inventory/       # Quantity tracking, low-stock logic
│   ├── expiration/      # Expiry dashboard, warning logic
│   └── ai-assistant/    # Chat UI, streaming response renderer
├── services/            # Axios instance, API call functions, push notification registration
├── hooks/               # Custom hooks (useNotifications, useBarcode, useTTS, etc.)
├── contexts/            # AuthContext, NotificationContext
├── routes/              # Route definitions, protected route wrapper
└── utils/               # Date helpers, quantity formatters, TTS wrapper
```

### 4.2 Key Pages

| Page | Purpose |
|---|---|
| Dashboard | Overview: today's schedule, low-stock alerts, expiry warnings |
| Inventory | Full medication list with quantity badges and expiry status |
| Medication Detail | Single medication: leaflet viewer with section search and TTS, schedule list, dose log |
| Schedules | All active and paused schedules across all medications |
| Logs | Chronological consumption history, filterable by medication |
| Expiration | Dedicated view for medications expiring soon or already expired |
| AI Assistant | Chat interface with message history and streaming responses |

### 4.3 PWA & Notifications
- Register a service worker at app startup
- Request push notification permission during onboarding
- Store push subscription endpoint on the backend
- Service worker handles background push events (dose reminders, expiry alerts)

---

## 5. Backend Architecture

**Stack:** Node.js with Express (or NestJS if structure is preferred), TypeScript, PostgreSQL via Prisma ORM, Redis for scheduler queue (Bull), JWT for auth, bcrypt for password hashing.

**Alternative stack acceptable:** If the developer prefers a Rust/Axum backend (consistent with developer's existing ECM project), that is a valid choice. The API contract below is stack-agnostic.

### 5.1 Directory Structure

```
backend/
├── src/
│   ├── auth/
│   │   ├── auth.controller.ts
│   │   ├── auth.service.ts
│   │   └── auth.dto.ts
│   ├── users/
│   ├── medications/
│   ├── inventory/
│   ├── reminders/
│   ├── expiration/
│   ├── notifications/
│   │   └── push/         # Web Push sending, subscription management
│   ├── ai/
│   │   ├── chat.controller.ts
│   │   ├── rag.service.ts       # Embedding, vector search, LLM call
│   │   └── leaflet.indexer.ts   # Run at medication ingestion time
│   ├── integrations/
│   │   └── barcode/      # Barcode lookup (OpenFDA or local DB)
│   └── shared/
│       ├── middleware/
│       ├── guards/
│       └── interceptors/
├── prisma/
│   └── schema.prisma
└── test/
```

Each feature module follows this internal structure:

```
module/
├── *.controller.ts   # HTTP handler, request validation, response shaping
├── *.service.ts      # Business logic
├── *.repository.ts   # Database access (Prisma queries)
├── *.dto.ts          # Input validation schemas (Zod or class-validator)
├── *.entity.ts       # Type definitions matching DB schema
└── *.mapper.ts       # Entity ↔ DTO transformation
```

### 5.2 API Design Principles
- RESTful resource-based URLs
- Versioned under `/api/v1/`
- All responses follow a consistent envelope: `{ data, meta, error }`
- Pagination on all list endpoints using cursor-based pagination
- Input validation on every endpoint; return 422 Unprocessable Entity with field-level error details on validation failure
- All authenticated endpoints require a valid `Authorization: Bearer <token>` header

---

## 6. Database Schema

All tables use UUIDs as primary keys and include `created_at` / `updated_at` timestamps. The schema below is the authoritative definition.

### 6.1 `users`

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   TEXT NOT NULL,
    display_name    VARCHAR(100),
    notification_preferences JSONB DEFAULT '{
        "push": true,
        "email": false,
        "low_stock_threshold": 7,
        "expiry_warning_days": 30
    }',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 6.2 `medications`

Global medication catalog — shared across all users. Populated via barcode scan, manual entry, or batch import.

```sql
CREATE TABLE medications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(255) NOT NULL,
    generic_name     VARCHAR(255),
    manufacturer     VARCHAR(255),
    barcode          VARCHAR(100) UNIQUE,
    form             VARCHAR(50),        -- tablet, capsule, syrup, injection, etc.
    strength         VARCHAR(50),        -- e.g. "500mg", "10mg/5ml"
    leaflet_raw      TEXT,               -- raw extracted text from the official leaflet
    leaflet_sections JSONB,              -- structured: { dosage, side_effects, contraindications, storage, interactions, missed_dose }
    vector_indexed   BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 6.3 `user_medications`

A user's personal inventory entry for a specific medication.

```sql
CREATE TABLE user_medications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    medication_id   UUID NOT NULL REFERENCES medications(id),
    quantity        NUMERIC(10, 2) NOT NULL DEFAULT 0,   -- current stock (pill count, ml, etc.)
    unit            VARCHAR(20) NOT NULL DEFAULT 'pill', -- pill, ml, patch, etc.
    expiration_date DATE,
    notes           TEXT,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, medication_id, expiration_date)     -- allows same medication with different expiry batches
);
```

### 6.4 `medication_schedules`

Dose schedules created by users per inventory entry.

```sql
CREATE TABLE medication_schedules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_medication_id  UUID NOT NULL REFERENCES user_medications(id) ON DELETE CASCADE,
    dosage_amount       NUMERIC(6, 2) NOT NULL,   -- how many units per dose
    frequency_type      VARCHAR(20) NOT NULL,     -- daily, weekly, interval
    frequency_value     INTEGER,                  -- e.g. every N days (for interval type)
    scheduled_times     TIME[] NOT NULL,           -- array of times of day, e.g. {08:00, 20:00}
    days_of_week        SMALLINT[],               -- [1,3,5] = Mon/Wed/Fri; NULL = every day
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    starts_on           DATE NOT NULL DEFAULT CURRENT_DATE,
    ends_on             DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 6.5 `medication_logs`

Immutable record of every logged dose.

```sql
CREATE TABLE medication_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_medication_id  UUID NOT NULL REFERENCES user_medications(id) ON DELETE CASCADE,
    schedule_id         UUID REFERENCES medication_schedules(id) ON DELETE SET NULL,
    taken_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    quantity_used       NUMERIC(6, 2) NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 6.6 `push_subscriptions`

Stores Web Push API subscription objects per user device.

```sql
CREATE TABLE push_subscriptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint    TEXT NOT NULL UNIQUE,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 6.7 Indexes

```sql
CREATE INDEX idx_user_medications_user_id ON user_medications(user_id);
CREATE INDEX idx_user_medications_expiration ON user_medications(expiration_date) WHERE expiration_date IS NOT NULL;
CREATE INDEX idx_medication_schedules_active ON medication_schedules(user_medication_id) WHERE active = TRUE;
CREATE INDEX idx_medication_logs_user_med ON medication_logs(user_medication_id, taken_at DESC);
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions(user_id);
```

---

## 7. Notification System

### 7.1 Scheduler
- A background job runner (Bull + Redis, or pg-boss for Postgres-only setups) runs every minute.
- On each tick it queries:
  - Active schedules where the next scheduled time falls within the current minute window
  - `user_medications` where `quantity <= low_stock_threshold` (from user preferences)
  - `user_medications` where `expiration_date BETWEEN NOW() AND NOW() + expiry_warning_days`

### 7.2 Delivery
- **Browser / PWA Push:** Web Push Protocol via the `web-push` npm package. VAPID keys generated once and stored in environment variables.
- **Email:** Nodemailer or a transactional email provider (SendGrid, Resend). Only sent if user has `notification_preferences.email = true`.

### 7.3 Notification Types

| Type | Trigger | Payload |
|---|---|---|
| `DOSE_REMINDER` | Scheduled dose time reached | Medication name, dosage, "Mark as taken" action |
| `LOW_STOCK` | Quantity ≤ threshold | Medication name, remaining quantity |
| `EXPIRY_WARNING` | Expiration within warning window | Medication name, days remaining |
| `EXPIRED` | Expiration date has passed | Medication name, expired date |

---

## 8. AI Assistant Architecture

### 8.1 Overview

The AI assistant answers user questions about medications using a Retrieval-Augmented Generation (RAG) pipeline grounded strictly in the indexed leaflet content. It does not draw on general medical knowledge and will not answer questions that cannot be grounded in a retrieved passage.

### 8.2 Leaflet Ingestion Pipeline

Run once when a new medication is added to the catalog:

```
Leaflet Text (raw)
        ↓
Section Splitter        — splits into named sections (dosage, side effects, etc.)
        ↓
Chunk Generator         — produces overlapping ~300-token chunks per section
        ↓
Embedding Model         — generates a vector per chunk (OpenAI text-embedding-3-small or local model)
        ↓
Vector Store            — pgvector extension on PostgreSQL; stores (chunk_text, embedding, medication_id, section_name)
```

### 8.3 Query Pipeline

```
User Question
        ↓
Embed Query             — same embedding model as ingestion
        ↓
Vector Similarity Search — top-5 chunks by cosine similarity, filtered by medication_id if user specified one
        ↓
Context Assembly        — retrieved chunks + conversation history formatted as prompt context
        ↓
LLM (Claude claude-sonnet-4-6 via Anthropic API)
        ↓
Streamed Response       — returned to frontend via SSE
```

### 8.4 System Prompt for the AI Assistant

The LLM call must include a system prompt of this form:

```
You are ECZAM Assistant, a medication information helper embedded in the ECZAM platform.
You answer questions strictly based on the medication leaflet passages provided to you in the context.
Do not speculate beyond what the passages say. Do not provide general medical advice.
If a question cannot be answered from the provided passages, say so clearly and suggest the user consult their pharmacist or physician.
Always cite which section of the leaflet your answer comes from.
Respond in the same language the user writes in.
```

### 8.5 Vector Store Schema (pgvector)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE leaflet_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medication_id   UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    section_name    VARCHAR(100) NOT NULL,   -- e.g. 'side_effects', 'dosage', 'storage'
    chunk_text      TEXT NOT NULL,
    embedding       VECTOR(1536),            -- dimension matches text-embedding-3-small
    chunk_index     INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_leaflet_chunks_medication ON leaflet_chunks(medication_id);
CREATE INDEX idx_leaflet_chunks_embedding ON leaflet_chunks
    USING hnsw (embedding vector_cosine_ops);
```

---

## 9. Barcode Scanning Module

### 9.1 Frontend
- Use the `@zxing/library` or `html5-qrcode` JavaScript library for in-browser camera scanning.
- Display a camera viewfinder modal with a scan target overlay.
- On decode: POST the barcode value to `/api/v1/medications/barcode/:code`.

### 9.2 Backend
- Look up the barcode in the local `medications` table first.
- If not found, query the **OpenFDA API** (`https://api.fda.gov/drug/label.json?search=openfda.upc_udi_di:BARCODE`) as a fallback.
- If found externally: create a new `medications` record, trigger leaflet ingestion in the background, and return the populated medication object.
- If not found anywhere: return a 404 with a message instructing the user to fill in the details manually.

---

## 10. Text-to-Speech (TTS)

- Implemented entirely on the **frontend** using the Web Speech API (`window.speechSynthesis`).
- No external TTS service is required for MVP.
- Provide a TTS control bar on the Medication Detail page: Play, Pause, Stop, and a section selector (dropdown of leaflet sections).
- Respect the user's system language for voice selection; fall back to the first available voice if no match.
- Expose a `useTTS` custom React hook that encapsulates the `SpeechSynthesisUtterance` lifecycle.

---

## 11. Environment Variables

All secrets and environment-specific values must be managed via environment variables. Never hardcode them.

```
# App
NODE_ENV=development
PORT=3000
FRONTEND_URL=http://localhost:5173

# Database
DATABASE_URL=postgresql://user:password@localhost:5432/eczam

# Auth
JWT_SECRET=<long-random-string>
JWT_EXPIRES_IN=7d

# Web Push
VAPID_PUBLIC_KEY=<generated>
VAPID_PRIVATE_KEY=<generated>
VAPID_EMAIL=mailto:admin@eczam.app

# AI
ANTHROPIC_API_KEY=<your-key>
OPENAI_API_KEY=<your-key>    # for embeddings (or replace with local model)

# Redis (for scheduler queue)
REDIS_URL=redis://localhost:6379

# Email (optional)
SMTP_HOST=
SMTP_PORT=
SMTP_USER=
SMTP_PASS=
```

---

## 12. Implementation Priorities

Build in this order. Each phase should be a fully working vertical slice before moving to the next.

### Phase 1 — Foundation
- [ ] Project scaffolding (monorepo or separate repos for frontend/backend)
- [ ] Database schema applied via Prisma migrations
- [ ] User registration, login, JWT issuance and refresh
- [ ] Auth middleware / protected route guard
- [ ] Basic React app shell with routing and AuthContext

### Phase 2 — Core Inventory
- [ ] Medications catalog CRUD (backend)
- [ ] User medication inventory CRUD (backend)
- [ ] Inventory list page (frontend)
- [ ] Add medication form with manual entry and barcode scan
- [ ] Medication detail page with leaflet viewer (sections, search)

### Phase 3 — Scheduling & Logging
- [ ] Schedule creation and management (backend + frontend)
- [ ] Dose logging endpoint with automatic quantity decrement
- [ ] Dose log history page

### Phase 4 — Notifications
- [ ] Push subscription registration endpoint
- [ ] Service worker with push event handler
- [ ] Background scheduler: dose reminders + low-stock alerts
- [ ] Expiration monitoring jobs + expiration dashboard page

### Phase 5 — AI Assistant & TTS
- [ ] pgvector setup and leaflet ingestion pipeline
- [ ] RAG query endpoint with streaming SSE response
- [ ] AI chat page
- [ ] TTS controls on medication detail page

### Phase 6 — PWA & Polish
- [ ] Service worker caching strategy (cache-first for static assets, network-first for API)
- [ ] Web App Manifest (icons, theme color, display mode)
- [ ] Offline fallback page
- [ ] Accessibility audit and fixes
- [ ] Responsive design QA across mobile breakpoints

---

## 13. Non-Functional Requirements

| Requirement | Target |
|---|---|
| API response time (p95) | < 300ms for all non-AI endpoints |
| AI streaming time to first token | < 2 seconds |
| PWA installability | Passes all Lighthouse PWA checks |
| Accessibility | WCAG 2.1 AA |
| Mobile viewport | Fully functional at 375px width |
| Test coverage | Unit tests for all service-layer business logic; integration tests for all API endpoints |
| Database | All queries use parameterized statements; no raw string interpolation |

---

## 14. Out of Scope for MVP

These features are planned for future versions and must **not** be built during the MVP phase:

- Multi-user / family / caregiver accounts
- Medication interaction detection
- OCR-based medication box photo recognition
- Prescription import
- Smart refill recommendations
- National medication database integrations beyond OpenFDA
- Voice assistant (microphone input to AI; TTS output only is in scope)

---

## 15. Glossary

| Term | Definition |
|---|---|
| **Inventory entry** | A `user_medications` row — a specific medication owned by a specific user, with a quantity and optional expiration date |
| **Schedule** | A `medication_schedules` row — defines when and how much of an inventory entry to take |
| **Dose log** | A `medication_logs` row — immutable record of a single taken dose |
| **Leaflet** | The official medication information document; stored as raw text and structured JSON sections |
| **Chunk** | A segment of leaflet text used as the unit for vector embedding and retrieval |
| **RAG** | Retrieval-Augmented Generation — the AI pipeline that retrieves relevant leaflet chunks before generating a response |
| **PWA** | Progressive Web Application — a web app installable on mobile and desktop with native-like capabilities |
| **VAPID** | Voluntary Application Server Identification — the key pair used to authenticate Web Push notifications |
