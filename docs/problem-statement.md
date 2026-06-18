# ECZAM — Problem Statement

> A focused analysis of the problem ECZAM solves: who suffers from it, why it
> persists, why existing tools fall short, and what "solved" means.

**Status:** Draft · **Owner:** Product · **Last updated:** 2026-06-18
**Related:** [vision-document.md](vision-document.md) · [user-personas.md](user-personas.md) · [product-requirements-document.md](product-requirements-document.md)

---

## 1. Summary

Household medication management is reactive, fragmented, and error-prone. People
rely on memory and paper to track what they take, how much they have, and whether
it is still safe — and on dense, unreadable leaflets to understand it. The result is
missed doses, accidental misuse, medicine that runs out at the worst moment, and
expired medicine used unknowingly. These failures concentrate in the populations
least equipped to absorb them: the elderly, the chronically ill, and the family
members who care for them.

## 2. The status quo

A typical person manages medication through a patchwork of unreliable habits:

- **Memory-based dosing.** "Did I already take my morning pill?" There is no record,
  so doses are missed or doubled.
- **Visual stock checks.** Stock is "tracked" by glancing at a box; the shortage is
  discovered only when it's empty — often after pharmacy hours.
- **Shoebox storage.** Medicines accumulate in a drawer or cabinet with no record of
  expiry dates; expired items sit next to current ones.
- **Ignored leaflets.** The official information leaflet is printed in ~6-point font
  across many dense pages and is effectively unreadable, so it's never consulted —
  not for side effects, not for missed-dose instructions, not for storage.
- **No consolidated view.** No single place answers "what do I take, when, how much
  is left, and what is it for?"

## 3. Who is affected

| Group | Why they are at high risk |
|---|---|
| **Elderly patients (polypharmacy)** | Multiple daily medications, poor eyesight, low digital literacy — the highest adherence-failure and confusion risk |
| **Chronic-condition patients** | Diabetes, hypertension, asthma, thyroid, etc. require precise, sustained daily adherence; lapses directly worsen outcomes |
| **Caregivers / family** | Manage medication on behalf of relatives, often remotely, with no shared, reliable system of record |
| **General adults** | Anyone wanting a structured record of household medicines and expiry dates |

Detailed personas: [user-personas.md](user-personas.md).

## 4. Consequences (the cost of inaction)

- **Non-adherence** — missed or mistimed doses reduce treatment effectiveness and
  drive avoidable complications, hospitalizations, and cost. Adherence to long-term
  therapy is widely estimated at only around half of what is prescribed.
- **Accidental misuse / overdose** — without a record of what was taken and when,
  double-dosing and skipped doses both become easy.
- **Use of expired medicine** — reduced efficacy at best, harm at worst; expired
  stock is invisible without active monitoring.
- **Running out** — essential medication lapses because nobody noticed the box was
  nearly empty in time to refill.
- **Uninformed decisions** — users don't know a medicine's side effects, storage
  needs, interactions, or what to do after a missed dose, because the information is
  inaccessible.

> Quantitative targets for measuring improvement against these consequences live in
> [vision-document.md](vision-document.md) §7 and
> [non-functional-requirements.md](non-functional-requirements.md).

## 5. Root causes

1. **No system of record.** Dosing and stock live in human memory, which is
   unreliable and unshareable.
2. **No proactivity.** Nothing watches stock levels or expiry dates and warns *before*
   a problem occurs.
3. **Information is locked in an unusable format.** Leaflets optimize for legal
   completeness, not human readability or accessibility.
4. **Accessibility gap.** Tools assume good eyesight, fine motor control, and digital
   fluency that the highest-risk users lack.
5. **Friction and lock-in.** Native apps require an app store, downloads, accounts,
   and storage — barriers that stop the very people who would benefit most.

## 6. Why existing approaches fall short

| Approach | Shortcoming |
|---|---|
| Paper lists / pill organizers | No reminders, no stock or expiry tracking, no information, easy to fall behind |
| Generic phone alarms | Fire blindly — no link to inventory, no logging, no "what/why", no expiry awareness |
| Native reminder apps | App-store + download + account friction; rarely accessibility-first; no grounded medicine information; no household model |
| Reading the paper leaflet | Unreadable for the target users; not searchable; no audio |
| General web/LLM search | Ungrounded, inconsistent, may hallucinate, not specific to the medicine the user actually owns |

## 7. Problem hypotheses ECZAM is betting on

1. If reminders are tied to a real, decrementing inventory, users will both **take
   doses on time** and **never be surprised by an empty box**.
2. If expiry is monitored proactively, users will **stop using expired medicine**.
3. If leaflet content is searchable, spoken aloud, and answerable in plain language —
   grounded strictly in the real leaflet — users will finally **understand their
   medicines**.
4. If the whole thing ships as an accessibility-first PWA with no app-store barrier,
   the **highest-risk users can actually adopt it**.

## 8. Definition of "solved"

For an active ECZAM user, the problem is solved when:

- they reliably know what to take and when, and log doses in one tap;
- their on-time adherence measurably improves;
- they are warned before they run low and before anything expires — and act on it;
- they can read or listen to, and ask questions about, any medicine they own, with
  answers grounded in its real leaflet;
- and a user with poor eyesight and low digital literacy can do all of the above
  unaided.

## 9. Constraints shaping the solution

- **Accessibility-first** (WCAG 2.1 AA; usable at 375px; scalable fonts; TTS).
- **PWA, not native** — zero app-store friction.
- **Grounded AI only** — no general medical advice; answers tied to leaflet passages.
- **KVKK compliance** — health data is special-category personal data.
- **MVP is single-user** — the household/caregiver model is a deliberate later bet
  (see [mvp-definition.md](mvp-definition.md) and [feature-backlog.md](feature-backlog.md)).
