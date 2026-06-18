# ECZAM — Functional Requirements

> Enumerated, testable functional requirements (`FR-###`) grouped by module. Each
> requirement has a priority (MoSCoW), acceptance criteria, and links to the
> personas/stories it serves.

**Status:** Draft · **Owner:** Product/Eng · **Last updated:** 2026-06-18
**Related:** [product-requirements-document.md](product-requirements-document.md) · [user-stories.md](user-stories.md) · [use-cases.md](use-cases.md) · [non-functional-requirements.md](non-functional-requirements.md) · [api-specification.md](api-specification.md)

---

## Legend

- **Priority (MoSCoW):** **M** Must · **S** Should · **C** Could · **W** Won't (this release)
- Acceptance criteria are written so each is independently verifiable (see
  [test-plan.md](test-plan.md)).
- ID prefixes: AUTH, MED, INV, SCH, LOG, EXP, LEAF, AI, BAR, NOT, PWA.

---

## 1. Authentication & account (FR-AUTH) — *brief §3.1, §5*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-001** | A visitor can register with email + password. | M | P1–P4 |
| **FR-002** | A registered user can log in with email + password and receive a JWT session. | M | P1–P4 |
| **FR-003** | The system issues a JWT access token and supports session refresh. | M | All |
| **FR-004** | A user can request a password reset via email and set a new password via a tokenized link. | M | All |
| **FR-005** | A user can view and edit their profile (`display_name`) and notification preferences. | M | All |
| **FR-006** | A user can log out, invalidating the client session. | S | All |

**Acceptance (samples):**
- FR-001: Registering with a new email creates a `users` row with a **bcrypt** hash;
  duplicate email → 409/422 with a field error; weak/invalid input → 422 field errors.
- FR-002: Valid credentials → 200 with token; invalid → 401 with a non-enumerating
  message.
- FR-004: Reset token is single-use, time-limited; expired/used token → error.

## 2. Medication catalog (FR-MED) — *brief §3.2, §6.2, §9*

The global, shared catalog of medications.

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-010** | The system stores catalog medications: name, generic name, manufacturer, barcode, form, strength, raw leaflet, structured leaflet sections. | M | All |
| **FR-011** | A medication can be created via manual entry when not already in the catalog. | M | P2,P3,P4 |
| **FR-012** | A medication can be looked up by barcode against the local catalog. | M | P2,P3,P4 |
| **FR-013** | On a barcode catalog miss, the system queries OpenFDA; on success it creates the catalog record and triggers background leaflet ingestion. | S | P2,P3,P4 |
| **FR-014** | On a total lookup miss, the system returns a 404 directing the user to manual entry. | M | P2,P3,P4 |
| **FR-015** | A user can view a medication's structured leaflet sections (dosage, side effects, contraindications, storage, interactions, missed dose). | M | All |

**Acceptance (samples):**
- FR-012/013: Known barcode returns the catalog object; unknown-but-OpenFDA-known
  triggers ingestion and returns the populated object; unknown-everywhere → 404.

## 3. Personal inventory (FR-INV) — *brief §3.2, §6.3*

A user's `user_medications` entries.

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-020** | A user can add an inventory entry: medication, quantity, unit, expiration date, notes. | M | All |
| **FR-021** | A user can edit an inventory entry (quantity, unit, expiry, notes). | M | All |
| **FR-022** | A user can delete an inventory entry (cascading its schedules & logs). | M | All |
| **FR-023** | A user can view their full inventory list with current quantity per entry. | M | All |
| **FR-024** | The inventory list shows a visual **low-stock indicator** when quantity ≤ the user's `low_stock_threshold`. | M | P1,P2,P3 |
| **FR-025** | The same medication may be held as separate entries by expiration batch (unique on user + medication + expiry). | S | P3,P4 |
| **FR-026** | The inventory list shows an **expiry status** indicator (ok / expiring soon / expired) per entry. | M | All |

**Acceptance (samples):**
- FR-024: An entry at or below threshold renders the low-stock badge and is included
  in the dashboard low-stock section.
- FR-025: Adding the same medication with a different expiry creates a second entry;
  same expiry → uniqueness conflict handled gracefully.

## 4. Dose scheduling (FR-SCH) — *brief §3.3, §6.4*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-030** | A user can create a schedule for an inventory entry: dosage amount, frequency type (daily / weekly / interval), frequency value, scheduled time(s) of day. | M | P1,P2,P3 |
| **FR-031** | A weekly schedule can specify days of week; an interval schedule specifies "every N days". | M | P1,P2,P3 |
| **FR-032** | A schedule can have a start date and an optional end date. | S | P2,P3 |
| **FR-033** | A user can edit a schedule. | M | P1,P2,P3 |
| **FR-034** | A user can **pause and resume** a schedule (toggle active). | M | P1,P2,P3 |
| **FR-035** | A user can delete a schedule. | M | P1,P2,P3 |
| **FR-036** | A user can view all schedules across all medications (active and paused). | M | P2,P3 |

**Acceptance (samples):**
- FR-030/031: A daily schedule with times `{08:00,20:00}` produces reminders at both
  times; weekly `[1,3,5]` only on Mon/Wed/Fri; interval N only every N days from start.
- FR-034: A paused schedule generates no reminders until resumed.

## 5. Dose logging (FR-LOG) — *brief §3.4, §6.5*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-040** | A user can log a dose as taken in **one tap** from a reminder or the medication detail screen. | M | P1,P2 |
| **FR-041** | Logging a dose **automatically decrements** the matching inventory entry's quantity by the dose amount. | M | All |
| **FR-042** | A dose log records timestamp, quantity used, optional linked schedule, and optional notes; logs are **immutable**. | M | All |
| **FR-043** | Quantity must not go below zero; logging against insufficient stock is handled with a clear warning. | S | All |
| **FR-044** | A user can view full consumption history, filterable by medication and date. | M | P2,P3 |
| **FR-045** | A user can log an ad-hoc (unscheduled) dose. | S | P1,P2 |

**Acceptance (samples):**
- FR-041: Logging a 1-unit dose against quantity 10 results in quantity 9 and one new
  immutable `medication_logs` row in the same transaction.
- FR-043: Logging more than remaining quantity warns and does not produce a negative
  quantity.

## 6. Expiration monitoring (FR-EXP) — *brief §3.5, §6.7, §7*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-050** | A dashboard section lists medications expiring within configurable thresholds (e.g. 30 days, 7 days). | M | All |
| **FR-051** | Already-expired inventory still in stock is actively flagged. | M | All |
| **FR-052** | The user's `expiry_warning_days` preference drives the warning window. | M | All |
| **FR-053** | The system sends expiry-warning and expired notifications per the notification system. | M | P1,P3 |
| **FR-054** | A dedicated Expiration page lists expiring-soon and expired items. | M | P4 |

## 7. Leaflet information, search & TTS (FR-LEAF) — *brief §3.6, §10*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-060** | Leaflet content is stored structured into named sections at ingestion time. | M | All |
| **FR-061** | A user can perform full-text search across leaflet sections (dosage, side effects, contraindications, storage, interactions, missed dose). | M | All |
| **FR-062** | A user can play any leaflet section aloud via **text-to-speech** (Web Speech API) with Play / Pause / Stop controls and a section selector. | M | P1,P3 |
| **FR-063** | TTS selects a voice matching the user's system language, falling back to the first available voice. | S | P1 |
| **FR-064** | TTS controls are fully keyboard-accessible. | M | P1 |

## 8. AI chat assistant (FR-AI) — *brief §3.7, §8*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-070** | A user can ask natural-language questions about medications they own in a chat interface. | M | All |
| **FR-071** | The assistant uses a RAG pipeline: embed query → top-k vector search over leaflet chunks → context assembly → LLM synthesis. | M | All |
| **FR-072** | The assistant answers **only** from retrieved leaflet passages; it must not give general medical advice. | M | All, P5 |
| **FR-073** | When a question cannot be grounded, the assistant says so and suggests consulting a pharmacist/physician. | M | All, P5 |
| **FR-074** | The assistant cites which leaflet section each answer came from. | M | All, P5 |
| **FR-075** | The assistant replies in the same language the user wrote in. | S | P1–P4 |
| **FR-076** | Responses are **streamed** to the UI (SSE) with conversation history retained in the session. | S | P2 |
| **FR-077** | Retrieval can be scoped to a specific medication when the user selects one. | S | All |

## 9. Barcode / DataMatrix scanning (FR-BAR) — *brief §3.8, §9*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-080** | A user can scan a barcode/DataMatrix using the browser camera in a viewfinder modal with a scan-target overlay. | M | P2,P3,P4 |
| **FR-081** | On a successful decode, the system looks up the code and auto-fills the Add Medication form. | M | P2,P3,P4 |
| **FR-082** | On a lookup miss, the UI falls back gracefully to manual entry. | M | P2,P3,P4 |
| **FR-083** | If camera permission is denied/unavailable, the user can still add medications manually. | M | All |

## 10. Notifications (FR-NOT) — *brief §3.3, §3.5, §7*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-090** | During onboarding the app requests push permission and stores the push subscription on the backend. | M | All |
| **FR-091** | The system sends `DOSE_REMINDER` push at scheduled dose times, with a "Mark as taken" action. | M | P1,P2 |
| **FR-092** | The system sends `LOW_STOCK` notifications when quantity ≤ threshold. | M | P1,P2,P3 |
| **FR-093** | The system sends `EXPIRY_WARNING` and `EXPIRED` notifications per the expiry window. | M | P1,P3 |
| **FR-094** | A user can enable optional **email** reminders; email is sent only when `notification_preferences.email = true`. | S | P2,P3 |
| **FR-095** | A background scheduler evaluates due reminders, low-stock, and expiry every minute. | M | (system) |
| **FR-096** | A user can manage multiple device push subscriptions and unsubscribe a device. | C | P2 |

**Acceptance (samples):**
- FR-091: A schedule due in the current minute window produces exactly one dose
  reminder per device; acting on "Mark as taken" logs the dose (FR-040).
- FR-095: The tick queries due schedules, low-stock entries, and expiry-window
  entries without duplicate sends.

## 11. PWA & platform (FR-PWA) — *brief §4.3, §6 (Phase 6)*

| ID | Requirement | Priority | Personas |
|---|---|---|---|
| **FR-100** | The app registers a service worker at startup and is installable (Web App Manifest with icons, theme color, display mode). | M | All |
| **FR-101** | The service worker handles background push events for reminders and alerts. | M | All |
| **FR-102** | The app provides an offline fallback page and a sensible caching strategy (cache-first static, network-first API). | S | All |
| **FR-103** | A dashboard summarizes today's schedule, low-stock alerts, and expiry warnings. | M | P1,P2,P3 |

---

## Traceability

Each FR is realized by one or more user stories ([user-stories.md](user-stories.md))
and exercised by use cases ([use-cases.md](use-cases.md)) and tests
([test-plan.md](test-plan.md)). The consolidated matrix lives in
[README.md](README.md).
