# ECZAM — User Stories

> Agile user stories (`US-###`) grouped under epics (`EP-##`), in the brief's
> Phase 1–6 build order so the list doubles as a backlog seed. Each story has
> acceptance criteria (Given/When/Then), priority, and linked functional
> requirements.

**Status:** Draft · **Owner:** Product · **Last updated:** 2026-06-18
**Related:** [user-personas.md](user-personas.md) · [functional-requirements.md](functional-requirements.md) · [use-cases.md](use-cases.md) · [mvp-definition.md](mvp-definition.md) · [feature-backlog.md](feature-backlog.md)

---

## Legend

- **Priority:** **M** Must · **S** Should · **C** Could
- Personas referenced by ID (P1–P5) — see [user-personas.md](user-personas.md).
- FRs referenced by ID — see [functional-requirements.md](functional-requirements.md).

## Epic overview

| Epic | Title | Phase | Stories |
|---|---|---|---|
| **EP-01** | Authentication & account | 1 | US-001 … US-006 |
| **EP-02** | Medication inventory | 2 | US-010 … US-017 |
| **EP-03** | Dose scheduling & reminders | 3 | US-020 … US-025 |
| **EP-04** | Dose logging | 3 | US-030 … US-034 |
| **EP-05** | Expiration monitoring | 4 | US-040 … US-043 |
| **EP-06** | Notifications & PWA | 4/6 | US-050 … US-056 |
| **EP-07** | Leaflet information & TTS | 5 | US-060 … US-064 |
| **EP-08** | AI assistant | 5 | US-070 … US-074 |
| **EP-09** | Dashboard | 6 | US-080 … US-082 |

---

## EP-01 — Authentication & account *(Phase 1)*

**US-001 (M, P1–P4) — Register.** As a new user, I want to register with my email and
a password so that I can have a private medication record.
- *Given* I'm on the registration screen, *when* I submit a valid, unused email and a
  valid password, *then* my account is created and I'm signed in. *(FR-001)*
- *Given* the email is already registered, *when* I submit, *then* I see a clear field
  error and no duplicate account is created.

**US-002 (M, P1–P4) — Log in.** As a registered user, I want to log in so that I can
access my medications.
- *Given* valid credentials, *when* I log in, *then* I receive a session and land on
  the dashboard. *(FR-002, FR-003)*
- *Given* invalid credentials, *when* I log in, *then* I see a non-revealing error.

**US-003 (M, all) — Stay signed in.** As a user, I want my session to persist and
refresh so that I'm not constantly logged out. *(FR-003)*

**US-004 (M, all) — Reset password.** As a user who forgot my password, I want to
reset it via an emailed link so that I can regain access.
- *Given* I request a reset for my email, *when* I open the single-use, time-limited
  link, *then* I can set a new password; an expired/used link is rejected. *(FR-004)*

**US-005 (M, all) — Manage preferences.** As a user, I want to set my display name and
notification preferences (push/email, low-stock threshold, expiry-warning days) so
that the app behaves the way I need. *(FR-005)*

**US-006 (S, all) — Log out.** As a user, I want to log out so that my data isn't
accessible on a shared device. *(FR-006)*

## EP-02 — Medication inventory *(Phase 2)*

**US-010 (M, P2,P3,P4) — Add by barcode.** As a user, I want to scan a medication's
barcode so that its details auto-fill and I add it fast.
- *Given* camera access, *when* I scan a known barcode, *then* the Add form is
  pre-filled from the catalog (or OpenFDA). *(FR-012, FR-013, FR-080, FR-081)*

**US-011 (M, all) — Add manually.** As a user, I want to add a medication manually so
that I can record items without a recognized barcode.
- *Given* an unrecognized/declined scan, *when* I enter details and save, *then* a
  catalog medication (if new) and my inventory entry are created. *(FR-011, FR-014,
  FR-020, FR-082, FR-083)*

**US-012 (M, all) — Record stock details.** As a user, I want to record quantity,
unit, expiration date, and notes so that inventory reflects reality. *(FR-020)*

**US-013 (M, all) — View inventory.** As a user, I want to see all my medications with
current quantities so that I know what I have. *(FR-023)*

**US-014 (M, P1,P2,P3) — See low stock.** As a user, I want low-stock items visibly
flagged so that I refill in time. *(FR-024)*

**US-015 (M, all) — See expiry status.** As a user, I want each item's expiry status
shown so that I notice problems at a glance. *(FR-026)*

**US-016 (M, all) — Edit/delete.** As a user, I want to edit or delete inventory
entries so that my records stay accurate. *(FR-021, FR-022)*

**US-017 (S, P3,P4) — Separate expiry batches.** As a user, I want to hold the same
medication as separate entries by expiration date so that batches are tracked
independently. *(FR-025)*

## EP-03 — Dose scheduling & reminders *(Phase 3)*

**US-020 (M, P1,P2,P3) — Create a schedule.** As a user, I want to schedule a
medication (dose amount, frequency, time(s)) so that I'm reminded to take it.
- *Given* an inventory entry, *when* I set times `{08:00,20:00}` daily, *then*
  reminders are generated for those times. *(FR-030, FR-031)*

**US-021 (M, P1,P2,P3) — Weekly / interval frequency.** As a user, I want weekly
(specific days) or interval ("every N days") schedules so that non-daily regimens are
supported. *(FR-031)*

**US-022 (S, P2,P3) — Start/end dates.** As a user, I want a schedule start and
optional end date so that courses with a defined length stop automatically. *(FR-032)*

**US-023 (M, P1,P2,P3) — Pause/resume.** As a user, I want to pause and resume a
schedule so that I can stop reminders temporarily without losing the setup. *(FR-034)*

**US-024 (M, P1,P2,P3) — Edit/delete schedule.** As a user, I want to edit or delete a
schedule so that it matches my current regimen. *(FR-033, FR-035)*

**US-025 (M, P2,P3) — See all schedules.** As a user, I want one view of all active
and paused schedules so that I understand my whole regimen. *(FR-036)*

## EP-04 — Dose logging *(Phase 3)*

**US-030 (M, P1,P2) — One-tap log.** As a user, I want to mark a dose "Taken" in one
tap so that logging is effortless.
- *Given* a due dose, *when* I tap "Taken," *then* a dose log is recorded and
  inventory decrements atomically. *(FR-040, FR-041, FR-042)*

**US-031 (M, all) — Auto-decrement.** As a user, I want logging to reduce my stock
automatically so that inventory stays accurate without manual edits. *(FR-041)*

**US-032 (S, all) — Insufficient-stock guard.** As a user, I want a warning if I log
more than I have so that quantities never go negative. *(FR-043)*

**US-033 (M, P2,P3) — View history.** As a user, I want to see my consumption history,
filterable by medication and date, so that I (and my doctor) can review adherence.
*(FR-044)*

**US-034 (S, P1,P2) — Log ad-hoc dose.** As a user, I want to log an unscheduled dose
so that off-schedule intake is still recorded. *(FR-045)*

## EP-05 — Expiration monitoring *(Phase 4)*

**US-040 (M, all) — Expiring-soon view.** As a user, I want a list of medications
expiring within my warning window so that I can act before they expire. *(FR-050,
FR-052, FR-054)*

**US-041 (M, all) — Expired flags.** As a user, I want expired items still in stock
clearly flagged so that I don't use them. *(FR-051)*

**US-042 (M, P1,P3) — Expiry alerts.** As a user, I want notifications about upcoming
and passed expirations so that I act even when I'm not in the app. *(FR-053, FR-093)*

**US-043 (S, all) — Configurable window.** As a user, I want to set my expiry-warning
days so that warnings match how I shop/refill. *(FR-052, FR-005)*

## EP-06 — Notifications & PWA *(Phase 4 / 6)*

**US-050 (M, all) — Install the app.** As a user, I want to install ECZAM from the
browser so that it behaves like a native app with no app store. *(FR-100)*

**US-051 (M, all) — Enable push.** As a user, I want to grant push permission during
onboarding so that I receive reminders and alerts. *(FR-090)*

**US-052 (M, P1,P2) — Dose reminders.** As a user, I want a push at each scheduled
dose time with a "Mark as taken" action so that I act immediately. *(FR-091, FR-101)*

**US-053 (M, P1,P2,P3) — Low-stock alerts.** As a user, I want a notification when an
item drops to my threshold so that I refill in time. *(FR-092)*

**US-054 (S, P2,P3) — Email reminders.** As a user, I want optional email reminders so
that I have a backup channel. *(FR-094)*

**US-055 (S, all) — Offline fallback.** As a user, I want a graceful offline screen
and cached assets so that the app doesn't break without a network. *(FR-102)*

**US-056 (C, P2) — Manage devices.** As a user, I want to unsubscribe a device so that
old devices stop receiving notifications. *(FR-096)*

## EP-07 — Leaflet information & TTS *(Phase 5)*

**US-060 (M, all) — View leaflet sections.** As a user, I want a medication's leaflet
organized into sections so that I can find what I need. *(FR-015, FR-060)*

**US-061 (M, all) — Search the leaflet.** As a user, I want to search across leaflet
sections so that I jump straight to dosage, side effects, storage, etc. *(FR-061)*

**US-062 (M, P1,P3) — Listen to a section.** As a user with poor eyesight, I want any
leaflet section read aloud so that I can understand it without reading. *(FR-062)*

**US-063 (M, P1) — Control playback.** As a user, I want Play/Pause/Stop and a section
selector so that I control what's read. *(FR-062)*

**US-064 (M, P1) — Accessible TTS.** As a keyboard-only user, I want TTS controls
fully keyboard-operable so that I can use them without a mouse. *(FR-064)*

## EP-08 — AI assistant *(Phase 5)*

**US-070 (M, all) — Ask about my meds.** As a user, I want to ask natural-language
questions about medications I own so that I get understandable answers. *(FR-070,
FR-071)*

**US-071 (M, all, P5) — Grounded answers.** As a user, I want answers based only on my
medicines' real leaflets so that I'm not misled. *(FR-072)*

**US-072 (M, all, P5) — Honest limits.** As a user, I want the assistant to tell me
when it can't answer and to refer me to a pharmacist/physician so that I stay safe.
*(FR-073)*

**US-073 (M, all, P5) — Citations.** As a user, I want the assistant to cite the
leaflet section it used so that I can verify the source. *(FR-074)*

**US-074 (S, P2) — Streamed, multilingual chat.** As a user, I want streamed responses
in my own language with conversation history so that the chat feels fast and natural.
*(FR-075, FR-076, FR-077)*

## EP-09 — Dashboard *(Phase 6)*

**US-080 (M, P1,P2,P3) — Today at a glance.** As a user, I want a dashboard showing
today's doses, low-stock alerts, and expiry warnings so that I see everything
important at once. *(FR-103)*

**US-081 (M, P1) — Big, simple overview.** As a low-vision user, I want the dashboard
in large, high-contrast text with minimal clutter so that I can use it unaided.
*(NFR-010…NFR-014)*

**US-082 (S, P1,P2) — Act from the dashboard.** As a user, I want to log a due dose
directly from the dashboard so that I act in one place. *(FR-040)*

---

## Story → FR coverage check

Every brief §3 feature is covered: Auth (EP-01), Inventory (EP-02), Scheduling/
Reminders (EP-03/EP-06), Logging (EP-04), Expiration (EP-05), Leaflet/TTS (EP-07),
AI (EP-08), Barcode (within EP-02), PWA/Notifications (EP-06), Dashboard (EP-09).
The consolidated traceability matrix is in [README.md](README.md).
