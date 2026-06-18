# ECZAM — Security & Compliance Requirements

> The security posture and compliance obligations for ECZAM. Because ECZAM processes
> **health data**, the primary regulatory framework is **KVKK** (Turkey). This doc
> covers KVKK compliance, authentication/authorization, data protection, API
> security, AI safety, and a threat model.

**Status:** Draft · **Owner:** Eng/Security · **Last updated:** 2026-06-18
**Related:** [system-architecture.md](system-architecture.md) · [api-specification.md](api-specification.md) · [database-design.md](database-design.md) · [non-functional-requirements.md](non-functional-requirements.md) · [test-plan.md](test-plan.md)

---

## 1. Data classification

| Class | Examples | Handling |
|---|---|---|
| **Special-category (health) personal data** | inventory (`user_medications`), schedules, dose logs — reveal a person's medications/conditions | Highest protection; KVKK Art. 6 rules apply |
| **Identifying personal data** | email, display name, push endpoints, user agent | Standard personal-data protection |
| **Secrets** | password hashes, JWT secret, VAPID/private keys, API keys | Never exposed; env-managed |
| **Shared/non-personal** | `medications` catalog, `leaflet_chunks` | Lower sensitivity (no user linkage) |

> A user's list of medications is **özel nitelikli kişisel veri** (special-category
> personal data) under KVKK because it reveals health information. This drives the
> consent, encryption, access-control, and retention requirements below.

## 2. KVKK compliance (primary framework)

KVKK = *Kişisel Verilerin Korunması Kanunu* (Law No. 6698), enforced by the KVKK
Authority (KVKK Kurumu). Key obligations for ECZAM:

| # | Obligation | Implementation in ECZAM |
|---|---|---|
| **SEC-K01** | **Lawful basis & explicit consent** for processing special-category (health) data (Art. 6) | Obtain **explicit, separate consent** at registration for storing medication/health data; record consent (timestamp + version). Do not pre-tick. |
| **SEC-K02** | **Clear disclosure (aydınlatma)** — inform the data subject of purpose, scope, recipients, retention | Present a privacy notice (*aydınlatma metni*) at signup and in-app; plain-language, accessible (large text). |
| **SEC-K03** | **Data minimization & purpose limitation** | Collect only what the features need (no demographics/identifiers beyond email + optional display name). |
| **SEC-K04** | **Data-subject rights** (Art. 11): access, rectification, erasure, learn purpose, object | Provide profile edit (rectification) and **account deletion** that cascades and erases personal data (see [database-design.md](database-design.md) §8); support data export (access). |
| **SEC-K05** | **Storage limitation / retention** | Define retention; delete or anonymize personal data when no longer needed or on account deletion; logs needed for adherence history are deleted with the account. |
| **SEC-K06** | **Security measures (Art. 12)** — technical & administrative | Encryption in transit & at rest, access control, logging, the controls in §3–§6 below. |
| **SEC-K07** | **Cross-border transfer constraints** | Health data transfer abroad needs an appropriate basis. The LLM/embedding providers are **non-TR**; obtain specific consent for AI processing, **minimize what is sent** (only leaflet passages + the question — never identifiers), and prefer regional/local options where feasible (a local embedding model satisfies this for embeddings). |
| **SEC-K08** | **Breach notification** | Process to notify the KVKK Authority and affected users "as soon as possible" after a breach; maintain an incident runbook. |
| **SEC-K09** | **Registry awareness (VERBİS)** | If thresholds require it, register with **VERBİS** (the data controllers' registry) and maintain processing records. |
| **SEC-K10** | **Processor agreements** | Where third parties (LLM, embeddings, email, push, hosting) process personal data, ensure contractual safeguards (DPA-equivalent). |

> ECZAM is the **data controller** (veri sorumlusu); external AI/email/push/hosting
> providers are **processors** (veri işleyen).

## 3. Authentication & authorization

| # | Control | Detail |
|---|---|---|
| **SEC-A01** | Password hashing | **bcrypt** with an appropriate work factor; never store plaintext. (FR-001, NFR-041) |
| **SEC-A02** | JWT sessions | Signed JWT access tokens (HS/RS), short-ish expiry, refresh flow; secret from `JWT_SECRET` env var. |
| **SEC-A03** | Token handling | Validate signature/expiry on every protected request via the Spring Security filter chain; reject tampered/expired → 401. |
| **SEC-A04** | Authorization scoping | Every `user_medications`/schedule/log/subscription access is checked against the authenticated user id — **no cross-user access** (IDOR prevention). |
| **SEC-A05** | Password reset | Single-use, time-limited, high-entropy reset tokens; non-enumerating responses (always 204 on request). |
| **SEC-A06** | Credential responses | Login failures return a generic message (no account enumeration). |
| **SEC-A07** | Brute-force protection | Rate limiting + lockout/backoff on auth endpoints (→ 429). |

## 4. Data protection

| # | Control | Detail |
|---|---|---|
| **SEC-D01** | Encryption in transit | **TLS** for all client–server and server–external traffic (NFR-040). |
| **SEC-D02** | Encryption at rest | Database and backups encrypted at rest (disk/managed-DB encryption). |
| **SEC-D03** | Query safety | **Parameterized statements only** (JPA/prepared); no string interpolation (NFR-042) — prevents SQL injection. |
| **SEC-D04** | Secrets management | All secrets via **environment variables** (brief §11); none hardcoded or committed (NFR-043); rotate on exposure. |
| **SEC-D05** | Least-privilege DB | Application DB user has only needed privileges. |
| **SEC-D06** | PII in logs | No passwords, tokens, or health data in logs; structured logs use ids/correlation, not content (NFR-060). |
| **SEC-D07** | Backups | Encrypted, access-controlled backups; deletion propagates per retention policy. |

## 5. API & application security

| # | Control | Detail |
|---|---|---|
| **SEC-P01** | Input validation | Validate every endpoint; invalid → 422 with field errors (FR/NFR-045). |
| **SEC-P02** | CORS | Restrict origins to `FRONTEND_URL`. |
| **SEC-P03** | Security headers | HSTS, `X-Content-Type-Options`, `Referrer-Policy`, a strict **Content-Security-Policy** (constrained by camera/Web Speech/SW needs), frame-ancestors deny. |
| **SEC-P04** | CSRF posture | Token (Bearer) auth in headers (not cookies) avoids classic CSRF; if any cookie auth is added, enable CSRF protection. |
| **SEC-P05** | Rate limiting | Applied to auth and AI endpoints; abuse/cost protection on the LLM path. |
| **SEC-P06** | Dependency hygiene | Dependency scanning (e.g. OWASP Dependency-Check) in CI; keep Spring/React deps patched. |
| **SEC-P07** | Error hygiene | Errors return safe messages via the envelope; no stack traces or internals to clients. |
| **SEC-P08** | File/camera scope | Camera access used only for scanning; images never uploaded/stored. |

## 6. AI assistant safety

| # | Control | Detail |
|---|---|---|
| **SEC-AI01** | Strict grounding | Answer **only** from retrieved leaflet passages; no general medical advice (FR-072). |
| **SEC-AI02** | Decline-and-refer | When ungrounded, decline and suggest consulting a pharmacist/physician (FR-073). |
| **SEC-AI03** | Citations | Cite the leaflet section used (FR-074) for verifiability/trust (P5). |
| **SEC-AI04** | Prompt-injection resistance | Treat retrieved leaflet text and user input as untrusted; keep the system prompt authoritative; don't let retrieved content override guardrails. |
| **SEC-AI05** | Data minimization to LLM | Send only the question + retrieved passages (+ minimal history) — **no user identifiers, email, or inventory metadata** (supports SEC-K07). |
| **SEC-AI06** | Logging | Do not persist full prompts/answers with personal data; if storing chat history, treat it as health data. |

## 7. Threat model (STRIDE summary)

| Threat | Example | Mitigation |
|---|---|---|
| **Spoofing** | Forged/stolen token | Signed JWT, expiry/refresh, TLS (SEC-A02/03) |
| **Tampering** | Modifying another user's inventory | Per-user authorization checks (SEC-A04); parameterized queries (SEC-D03) |
| **Repudiation** | Disputing a logged dose | Immutable `medication_logs` + correlation logging |
| **Information disclosure** | Leaking health data | TLS, encryption at rest, no PII in logs, minimization to LLM (SEC-D, SEC-AI05) |
| **Denial of service** | Auth/AI endpoint abuse | Rate limiting (SEC-A07, SEC-P05), graceful external-failure handling |
| **Elevation of privilege** | IDOR to other accounts | Strict ownership checks on every resource (SEC-A04) |

## 8. OWASP alignment

The controls above map to the **OWASP Top 10** and **OWASP ASVS** (auth, access
control, validation, cryptography, logging, configuration). Verification is in
[test-plan.md](test-plan.md) §Security (authz/IDOR, injection, validation, rate
limiting) plus dependency scanning and a security review (`/security-review`) before
release.

## 9. Compliance checklist (release gate)

- [ ] Explicit consent + privacy notice (aydınlatma) shown and recorded at signup
      (SEC-K01/02)
- [ ] Account deletion cascades and erases personal data; data export available
      (SEC-K04)
- [ ] Retention policy defined and enforced (SEC-K05)
- [ ] TLS everywhere; DB + backups encrypted at rest (SEC-D01/02)
- [ ] All secrets in env vars; none in the repo (SEC-D04)
- [ ] Per-user authorization verified (no IDOR) (SEC-A04)
- [ ] Rate limiting on auth + AI (SEC-A07/P05)
- [ ] LLM receives no user identifiers; AI consent obtained (SEC-K07, SEC-AI05)
- [ ] Breach runbook + KVKK notification path documented (SEC-K08)
- [ ] VERBİS registration assessed (SEC-K09)
- [ ] Processor agreements in place for third parties (SEC-K10)
- [ ] Dependency scan clean; security review completed (SEC-P06)
