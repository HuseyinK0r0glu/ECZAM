# ECZAM — User Personas

> The people ECZAM is for. Personas are referenced by ID (`P1`–`P5`) throughout the
> documentation set (user stories, use cases, requirements).

**Status:** Draft · **Owner:** Product/UX · **Last updated:** 2026-06-18
**Related:** [problem-statement.md](problem-statement.md) · [user-stories.md](user-stories.md) · [use-cases.md](use-cases.md) · [non-functional-requirements.md](non-functional-requirements.md)

---

## Persona overview

| ID | Persona | Type | Defining need |
|---|---|---|---|
| **P1** | Ayşe — elderly polypharmacy patient | Primary | Never miss a dose; understand many medicines despite poor eyesight |
| **P2** | Mehmet — chronic-condition patient | Primary | Reliable daily adherence and stock tracking around a busy life |
| **P3** | Zeynep — caregiver / family member | Primary | Manage a relative's medication confidently, often remotely |
| **P4** | Deniz — organized household adult | Secondary | A searchable record of household medicines and expiry dates |
| **P5** | Dr. Kaya — pharmacist / physician | Influencer | Confidence that patients are informed safely and not misled |

> **MVP note:** ECZAM MVP is **single-user**. P3 (caregiver) and P5 (pharmacist)
> shape requirements and trust, but the multi-user/caregiver account model is
> explicitly post-MVP (see [mvp-definition.md](mvp-definition.md)). In MVP, a
> caregiver uses ECZAM as the patient's single account.

---

## P1 — Ayşe, 74 — Elderly polypharmacy patient

> *"I just want to know I've taken the right pills, and I want the writing big
> enough to read."*

- **Context:** Lives alone; manages 6 medications for hypertension, thyroid, and
  cholesterol, taken at different times of day. Children check in by phone.
- **Tech comfort:** Low. Uses a smartphone for calls, messaging, and photos. Wary of
  installing apps; intimidated by small text and cluttered screens.
- **Accessibility needs:** Poor eyesight (needs large, high-contrast, scalable text);
  prefers listening over reading; limited fine motor precision (large tap targets).
- **Goals:**
  - Take every dose at the right time without second-guessing.
  - Avoid running out of essential medication.
  - Understand what each medicine is for and its side effects.
- **Frustrations:** Forgets whether she already took a pill; can't read leaflets;
  discovers empty boxes too late; confused by complex apps.
- **Most valued features:** Dose reminders + one-tap "Taken"; large-text inventory
  with low-stock badges; **TTS leaflet playback**; simple AI questions ("what is this
  for?").
- **Success looks like:** She opens ECZAM, sees today's doses in big text, taps
  "Taken," and never worries about running out or expiry.

## P2 — Mehmet, 52 — Chronic-condition patient

> *"My diabetes meds can't slip. I need it to just work in the background of a busy
> day."*

- **Context:** Works full-time; manages type 2 diabetes and hypertension. Travels
  occasionally; juggles multiple daily doses.
- **Tech comfort:** High. Comfortable with apps, push notifications, and installing a
  PWA to his home screen.
- **Accessibility needs:** Standard; values speed and minimal taps over hand-holding.
- **Goals:**
  - Precise, sustained daily adherence with minimal friction.
  - Know stock levels in advance to refill before running out.
  - Keep a history he can review and share with his doctor.
- **Frustrations:** Generic phone alarms aren't tied to inventory and don't record
  what he took; manual tracking is tedious; refills sneak up on him.
- **Most valued features:** Reliable push reminders; one-tap logging with auto
  inventory decrement; low-stock alerts; consumption history; barcode scan to add
  meds fast.
- **Success looks like:** Reminders fire on time, logging is one tap, and he's warned
  to refill days before running out.

## P3 — Zeynep, 41 — Caregiver / family member

> *"I'm managing my mother's medicines from across town. I need to trust the system
> when I can't be there."*

- **Context:** Manages medication for her elderly mother (a P1-type patient) in
  addition to her own household. Often acts remotely.
- **Tech comfort:** High. Sets things up on behalf of a less tech-comfortable
  relative.
- **Accessibility needs:** Standard for herself; must configure an accessible setup
  for her mother.
- **Goals:**
  - Set up schedules, inventory, and reminders that her mother can follow unaided.
  - Be confident nothing is missed, low, or expired.
  - Quickly look up information about her mother's medicines.
- **Frustrations:** No shared, reliable record; relies on phone calls to verify doses;
  hard to keep expiry and stock straight from a distance.
- **Most valued features:** Easy setup of inventory + schedules; clear dashboards for
  stock/expiry; notifications; the AI assistant and leaflet search for quick answers.
- **MVP reality:** Operates ECZAM as her mother's single account. Native multi-user
  caregiver accounts are a post-MVP bet ([feature-backlog.md](feature-backlog.md)).
- **Success looks like:** She configures her mother's medications once, and the app
  keeps both of them informed.

## P4 — Deniz, 35 — Organized household adult (secondary)

> *"I just want one tidy place for every medicine in the house and when it expires."*

- **Context:** Manages a household medicine cabinet (occasional and family meds).
  Not on a strict daily regimen.
- **Tech comfort:** High.
- **Accessibility needs:** Standard.
- **Goals:** A structured, searchable inventory; expiry awareness so nothing is used
  past date; quick info lookups when someone feels unwell.
- **Frustrations:** Medicines pile up untracked; expiry dates are invisible;
  guesses at dosages and uses.
- **Most valued features:** Inventory CRUD; expiration dashboard; leaflet search +
  TTS; AI assistant for occasional questions.
- **Success looks like:** Adds medicines in seconds (barcode), and the app flags
  anything expiring.

## P5 — Dr. Kaya — Pharmacist / physician (influencer, not a direct user)

> *"As long as it never invents medical advice and always points back to the leaflet
> and to us, I'm comfortable recommending it."*

- **Role:** External stakeholder whose trust drives adoption and word-of-mouth.
- **Concerns:** That the AI stays strictly grounded in real leaflet content, never
  gives general medical advice, and explicitly directs users to a pharmacist or
  physician when it can't answer.
- **Influence on the product:** Drives the AI grounding/guardrail requirements and
  the "informational, not diagnostic" non-goal.
- **Success looks like:** Patients arrive better informed, with accurate
  leaflet-based understanding and no dangerous misinformation.

---

## Accessibility implications (cross-persona)

Driven mainly by **P1** (and benefiting everyone), these shape the UI and the NFRs:

- **Large, high-contrast, user-scalable text** — font size overridable by browser
  settings; never block zoom.
- **Voice output (TTS) as a first-class feature** — any leaflet section can be read
  aloud; controls are keyboard-accessible.
- **Minimal steps for frequent actions** — log a dose, check inventory, search a
  medicine should each take as few taps as possible.
- **Simple, unambiguous navigation** and large tap targets for limited dexterity.
- **Keyboard focus indicators** on all interactive elements.

These are formalized in [non-functional-requirements.md](non-functional-requirements.md)
(WCAG 2.1 AA, 375px viewport, font scaling, keyboard accessibility).

## Persona → priority feature map

| Feature | P1 | P2 | P3 | P4 |
|---|:--:|:--:|:--:|:--:|
| Dose reminders + one-tap logging | ●●● | ●●● | ●● | ○ |
| Inventory + low-stock alerts | ●● | ●●● | ●● | ●● |
| Expiration monitoring | ●● | ●● | ●● | ●●● |
| Leaflet search + TTS | ●●● | ● | ●● | ●● |
| AI assistant (RAG) | ●● | ● | ●● | ●● |
| Barcode scanning | ● | ●● | ●● | ●●● |

●●● critical · ●● important · ● useful · ○ minor
