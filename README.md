# ECZAM — Documentation

The complete documentation suite for **ECZAM**, a smart medication management PWA.
Start with the [project brief](../ECZAM_PROJECT_BRIEF.md) (authoritative source of
truth) and the repo guide [CLAUDE.md](../CLAUDE.md), then read the docs below.

> **Stack:** Spring Boot + PostgreSQL (backend) · React 18 + TypeScript PWA
> (frontend) · **Compliance:** KVKK (Turkey). See [CLAUDE.md](../CLAUDE.md) for the
> stack-decision note vs the brief.

---

## Reading order

The suite flows discovery → requirements → scope → technical design → quality.

| # | Document | What it answers |
|---|---|---|
| **Discovery** | | |
| 1 | [vision-document.md](vision-document.md) | Why ECZAM exists; long-term vision, pillars, KPIs |
| 2 | [problem-statement.md](problem-statement.md) | The problem, who suffers, why now, "solved" criteria |
| 3 | [user-personas.md](user-personas.md) | Who we build for (P1–P5) and their needs |
| **Requirements** | | |
| 4 | [product-requirements-document.md](product-requirements-document.md) | The umbrella product spec |
| 5 | [functional-requirements.md](functional-requirements.md) | Enumerated `FR-###` behaviors |
| 6 | [non-functional-requirements.md](non-functional-requirements.md) | Quality attributes `NFR-###` with targets |
| 7 | [user-stories.md](user-stories.md) | `US-###` stories by epic, in build order |
| 8 | [use-cases.md](use-cases.md) | `UC-###` flows with sequence diagrams |
| **Scope** | | |
| 9 | [mvp-definition.md](mvp-definition.md) | MVP boundary, phases, Definition of Done |
| 10 | [feature-backlog.md](feature-backlog.md) | `FEAT-##` MVP + post-MVP roadmap |
| **Technical design** | | |
| 11 | [system-architecture.md](system-architecture.md) | Components, Spring Boot/React, RAG, ADRs |
| 12 | [database-design.md](database-design.md) | Schema, ER diagram, indexes, migrations |
| 13 | [api-specification.md](api-specification.md) | REST contract & endpoint catalog |
| 14 | [security-requirements.md](security-requirements.md) | KVKK + security controls + threat model |
| **Quality** | | |
| 15 | [test-plan.md](test-plan.md) | Test pyramid, coverage, NFR verification |

**Status legend:** Draft (initial) · Reviewed · Approved. All docs are currently
**Draft**.

---

## ID scheme

| Prefix | Meaning | Defined in |
|---|---|---|
| `P#` | Persona | [user-personas.md](user-personas.md) |
| `EP-##` | Epic | [user-stories.md](user-stories.md) |
| `US-###` | User story | [user-stories.md](user-stories.md) |
| `FR-###` | Functional requirement | [functional-requirements.md](functional-requirements.md) |
| `NFR-###` | Non-functional requirement | [non-functional-requirements.md](non-functional-requirements.md) |
| `UC-###` | Use case | [use-cases.md](use-cases.md) |
| `FEAT-##` | Feature / backlog item | [feature-backlog.md](feature-backlog.md) |
| `SEC-*` | Security/compliance control | [security-requirements.md](security-requirements.md) |

---

## Traceability matrix

Maps each capability across the suite: Persona → Epic/Stories → Functional reqs →
Use cases → Feature(s) → Test areas. (Cross-cutting NFRs and security controls apply
throughout; see their own docs.)

| Capability | Personas | Epic / Stories | FRs | Use cases | Features | Tests |
|---|---|---|---|---|---|---|
| Authentication & account | P1–P4 | EP-01 / US-001…006 | FR-001…006 | UC-001 | FEAT-01…03 | Unit(auth), Int(auth), E2E-1, KVKK |
| Medication catalog | All | EP-02 / US-010,011 | FR-010…015 | UC-002,003 | FEAT-05,07 | Int(catalog), E2E-2 |
| Barcode scanning | P2,P3,P4 | EP-02 / US-010 | FR-080…083 | UC-002 | FEAT-06 | Int(barcode), Frontend, E2E-2 |
| Personal inventory | All | EP-02 / US-012…017 | FR-020…026 | UC-002,003 | FEAT-08…10 | Unit, Int, Frontend(badges), E2E-2/5 |
| Dose scheduling | P1,P2,P3 | EP-03 / US-020…025 | FR-030…036 | UC-004 | FEAT-12 | Unit(is-due), Int, E2E-3 |
| Dose logging | P1,P2 | EP-04 / US-030…034 | FR-040…045 | UC-005 | FEAT-13,14 | Unit+Int(atomicity), E2E-3 |
| Expiration monitoring | All | EP-05 / US-040…043 | FR-050…054 | UC-007 | FEAT-17 | Unit(windows), Scheduler, E2E-5 |
| Notifications & PWA | P1,P2,P3 | EP-06 / US-050…056 | FR-090…102 | UC-006 | FEAT-15,16,18,19d | Scheduler, Frontend(SW), Lighthouse, E2E-4 |
| Leaflet info & TTS | P1,P3 | EP-07 / US-060…064 | FR-060…064 | UC-008,010 | FEAT-11,19a,19b | Frontend(useTTS), Int(search), E2E-6 |
| AI assistant (RAG) | All, P5 | EP-08 / US-070…074 | FR-070…077 | UC-009,010 | FEAT-19,19a | RAG eval, Int(SSE), E2E-7 |
| Dashboard | P1,P2,P3 | EP-09 / US-080…082 | FR-103 | UC-006,007 | FEAT-19c | Frontend, E2E-5 |

> Cross-cutting: **NFRs** ([non-functional-requirements.md](non-functional-requirements.md))
> govern performance, accessibility, reliability, and PWA quality across all
> capabilities; **security controls** ([security-requirements.md](security-requirements.md),
> `SEC-*`) and **KVKK** obligations apply to every flow that touches personal/health
> data.

---

## Conventions for editing these docs

- Keep requirement IDs **stable**; never renumber. New items get the next free ID.
- When adding/changing a capability, update its row in the matrix above and the
  relevant FR/US/UC/FEAT docs together.
- Keep the docs in sync with the code as it lands (NFR-053); the brief remains the
  source of truth for intent.
