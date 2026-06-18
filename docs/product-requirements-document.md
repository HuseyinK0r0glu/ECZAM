# ECZAM — Product Requirements Document (PRD)

> The umbrella product specification. It frames the goals, users, scope, and success
> criteria, and points to the detailed requirement docs that decompose it.

**Status:** Draft · **Owner:** Product · **Last updated:** 2026-06-18
**Related:** [vision-document.md](vision-document.md) · [functional-requirements.md](functional-requirements.md) · [non-functional-requirements.md](non-functional-requirements.md) · [user-stories.md](user-stories.md) · [use-cases.md](use-cases.md) · [mvp-definition.md](mvp-definition.md)

---

## 1. Overview

ECZAM is a smart medication management PWA that helps users — especially elderly and
chronic-condition patients and their caregivers — manage the full medication
lifecycle: inventory, scheduling, dose logging, expiration monitoring, leaflet
information access (search + TTS), and a RAG-based AI assistant grounded strictly in
leaflet content. This PRD defines what the MVP must deliver and the criteria by which
it will be judged.

## 2. Goals

- **G1 — Improve adherence:** make taking doses on time effortless via reminders and
  one-tap logging.
- **G2 — Prevent stockouts:** keep an accurate, auto-decrementing inventory and warn
  before users run low.
- **G3 — Prevent expired-medicine use:** monitor expiry proactively and flag expired
  stock.
- **G4 — Make information accessible:** turn dense leaflets into searchable, spoken,
  conversational, grounded answers.
- **G5 — Reach the highest-risk users:** ship an accessibility-first PWA with no
  app-store barrier.

## 3. Non-goals

ECZAM is not a diagnostic tool, telemedicine service, pharmacy/e-commerce platform,
or source of general medical advice. For MVP it is also **single-user** (no caregiver
accounts), with no interaction detection, OCR, prescription import, refill
recommendations, or voice input. See [mvp-definition.md](mvp-definition.md) §Out of
scope.

## 4. Target users

Primary: elderly polypharmacy patients (**P1**), chronic-condition patients (**P2**),
caregivers/family (**P3**). Secondary: organized household adults (**P4**).
Influencer: pharmacists/physicians (**P5**). Full detail:
[user-personas.md](user-personas.md).

## 5. Feature overview

The MVP comprises eight core capability areas (from the brief §3). Each maps to
detailed functional requirements in [functional-requirements.md](functional-requirements.md).

| # | Capability | Summary | FR group |
|---|---|---|---|
| 1 | **Authentication** | Email/password registration & login, JWT sessions, password reset | FR-AUTH |
| 2 | **Medication inventory** | Add (manual/barcode), edit, delete; quantity + low-stock indicators | FR-INV / FR-MED |
| 3 | **Dose scheduling & reminders** | Per-medication schedules; pause/resume; push + optional email reminders | FR-SCH / FR-NOT |
| 4 | **Dose logging** | One-tap "Taken"; auto inventory decrement; full history | FR-LOG |
| 5 | **Expiration monitoring** | Expiring-soon thresholds; expired flags; expiry alerts | FR-EXP |
| 6 | **Leaflet information & TTS** | Structured leaflet storage; section search; text-to-speech | FR-LEAF |
| 7 | **AI chat assistant** | RAG over leaflet vectors; grounded, streamed answers | FR-AI |
| 8 | **Barcode / DataMatrix scan** | Camera scan → lookup → auto-fill; OpenFDA fallback | FR-BAR |

Cross-cutting: **PWA & notifications** (installability, service worker, push) — FR-PWA.

## 6. Detailed requirements (references)

- **Functional requirements:** [functional-requirements.md](functional-requirements.md)
  (`FR-###`)
- **Non-functional requirements:** [non-functional-requirements.md](non-functional-requirements.md)
  (`NFR-###`)
- **User stories:** [user-stories.md](user-stories.md) (`US-###`, grouped by epic)
- **Use cases:** [use-cases.md](use-cases.md) (`UC-###`)

## 7. Release scope

MVP scope and phasing are defined in [mvp-definition.md](mvp-definition.md): six
vertical-slice phases — Foundation → Core Inventory → Scheduling & Logging →
Notifications → AI Assistant & TTS → PWA & Polish. The post-MVP roadmap lives in
[feature-backlog.md](feature-backlog.md).

## 8. Assumptions

- Users have a modern browser supporting service workers, Web Push, the Web Speech
  API, and camera access.
- Leaflet content can be obtained and stored as raw text + structured sections at
  medication-ingestion time.
- An Anthropic API key (assistant) and an embeddings model (OpenAI or local) are
  available.
- OpenFDA is an acceptable external barcode/label fallback source for MVP.

## 9. Dependencies

| Dependency | Used for |
|---|---|
| PostgreSQL + pgvector | Primary datastore and vector search |
| Anthropic API (`claude-sonnet-4-6`) | AI assistant response synthesis |
| Embedding model (OpenAI `text-embedding-3-small` or local) | Leaflet + query embeddings |
| Web Push (VAPID) | Browser/PWA push notifications |
| OpenFDA API | Barcode/label lookup fallback |
| Email/SMTP provider (optional) | Email reminders when enabled |
| Browser Web Speech API | Text-to-speech (frontend, no external service) |

## 10. Constraints

- **Accessibility:** WCAG 2.1 AA; functional at 375px; user font scaling respected.
- **Compliance:** KVKK — health data is special-category personal data
  ([security-requirements.md](security-requirements.md)).
- **Performance:** non-AI endpoints p95 < 300ms; AI time-to-first-token < 2s.
- **Stack:** Spring Boot + PostgreSQL backend; React + TypeScript PWA frontend.
- **AI grounding:** assistant answers only from retrieved leaflet passages; no
  general medical advice.

## 11. Success metrics

North-star: on-time medication adherence rate. Supporting KPIs (adherence, stock
safety, expiry safety, information usage, engagement, quality) are defined in
[vision-document.md](vision-document.md) §7. MVP acceptance criteria are in
[mvp-definition.md](mvp-definition.md).

## 12. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Leaflet data availability/quality | AI & search degrade | Structured ingestion; graceful "not found" handling; manual entry fallback |
| AI hallucination / ungrounded advice | Safety + trust | Strict RAG grounding, citations, decline-and-refer behavior; eval tests |
| Notification reliability across browsers | Missed reminders | Standards-based Web Push; optional email backup; in-app today view |
| Accessibility gaps for P1 | Adoption failure | A11y in NFRs + audits; TTS first-class; usability testing with target users |
| KVKK non-compliance | Legal/trust | Compliance controls in [security-requirements.md](security-requirements.md) |

## 13. Open questions

- Embeddings: hosted (OpenAI) vs local model for MVP — cost vs privacy trade-off.
- Initial leaflet corpus: how is the catalog seeded (manual, batch import, OpenFDA)?
- Email provider choice (SMTP vs transactional service) for the optional channel.
- Scheduler backing: in-process `@Scheduled` vs Quartz vs Redis-backed queue at MVP
  scale (see [system-architecture.md](system-architecture.md)).

## 14. Glossary

See the brief §15 and [database-design.md](database-design.md) for definitions of
Inventory entry, Schedule, Dose log, Leaflet, Chunk, RAG, PWA, and VAPID.
