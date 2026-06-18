# ECZAM — Non-Functional Requirements

> Quality attributes the system must meet (`NFR-###`). Each has a measurable target
> and a verification method.

**Status:** Draft · **Owner:** Eng · **Last updated:** 2026-06-18
**Related:** [product-requirements-document.md](product-requirements-document.md) · [functional-requirements.md](functional-requirements.md) · [system-architecture.md](system-architecture.md) · [security-requirements.md](security-requirements.md) · [test-plan.md](test-plan.md)

---

## Legend

- **Priority:** **M** Must · **S** Should · **C** Could
- Every NFR is stated as a **target** with a **verification method** (how
  [test-plan.md](test-plan.md) confirms it).

---

## 1. Performance & efficiency — *brief §13*

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-001** | Non-AI API latency | p95 **< 300 ms** under expected load | M | Load test / APM percentile measurement |
| **NFR-002** | AI assistant responsiveness | **time-to-first-token < 2 s** | M | Instrumented timing on the SSE stream |
| **NFR-003** | Vector search latency | top-k retrieval contributes < 150 ms to the request | S | Query timing with HNSW index |
| **NFR-004** | Frontend initial load | interactive in < 3 s on mid-range mobile (3G-fast) | S | Lighthouse performance audit |
| **NFR-005** | Scheduler tick | per-minute evaluation completes well within its 60 s window at target scale | M | Job duration metrics |

## 2. Accessibility — *brief §4, §13 (driven by P1)*

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-010** | Accessibility conformance | **WCAG 2.1 AA** minimum | M | axe automated scan + manual audit |
| **NFR-011** | Mobile viewport | fully functional at **375 px** width | M | Responsive QA across breakpoints |
| **NFR-012** | Font scaling | text scales with the user's browser font settings; no fixed px that block zoom; zoom never disabled | M | Manual test at 200% zoom |
| **NFR-013** | Keyboard accessibility | all interactive elements reachable & operable by keyboard with visible focus indicators; **TTS controls keyboard-operable** | M | Keyboard-only walkthrough |
| **NFR-014** | Contrast & legibility | high-contrast, large default type suitable for low-vision users | M | Contrast-ratio checks (≥ 4.5:1 normal text) |
| **NFR-015** | Minimal interaction cost | frequent actions (log a dose, check inventory, search) reachable in minimal steps | S | Task-based usability testing with target users |

## 3. Reliability & availability

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-020** | Notification reliability | due reminders delivered without duplicates; no double-send within a tick window | M | Scheduler integration tests |
| **NFR-021** | Data integrity of dose logging | dose log + inventory decrement are atomic (single transaction) | M | Concurrency/transaction tests |
| **NFR-022** | Graceful degradation | external failures (OpenFDA, embeddings, LLM, push) degrade features without crashing the app | M | Fault-injection tests |
| **NFR-023** | Offline resilience | PWA serves an offline fallback and cached static assets when network is unavailable | S | Offline simulation |

## 4. Scalability & capacity

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-030** | Stateless API | API instances are stateless and horizontally scalable behind a load balancer | S | Architecture review; multi-instance test |
| **NFR-031** | Pagination | all list endpoints use cursor-based pagination to bound payloads | M | API contract tests |
| **NFR-032** | Vector index scaling | leaflet-chunk search remains within latency budget as the corpus grows (HNSW) | S | Benchmark at growing corpus sizes |

## 5. Security & privacy — *see [security-requirements.md](security-requirements.md)*

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-040** | Transport security | TLS for all client–server and server–external traffic | M | Config review / scan |
| **NFR-041** | Credential storage | passwords hashed with bcrypt; no plaintext secrets | M | Code review |
| **NFR-042** | Query safety | parameterized statements only; **no raw string interpolation** | M | Static analysis + code review |
| **NFR-043** | Secrets management | all secrets via environment variables; none hardcoded | M | Repo scan |
| **NFR-044** | KVKK compliance | health data handled as special-category personal data | M | Compliance checklist ([security-requirements.md](security-requirements.md)) |
| **NFR-045** | Input validation | every endpoint validated; invalid input → 422 with field errors | M | API tests |

## 6. Maintainability

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-050** | Layered structure | backend follows controller/service/repository/entity/dto/mapper per domain | M | Architecture review |
| **NFR-051** | API consistency | uniform `{data, meta, error}` envelope and versioning under `/api/v1` | M | Contract tests |
| **NFR-052** | Configurability | thresholds (low-stock, expiry-warning) are user-configurable preferences, not hardcoded | M | Functional tests |
| **NFR-053** | Documentation currency | docs/ kept in sync with implemented behavior | S | Review at PR time |

## 7. Observability

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-060** | Structured logging | requests, jobs, and errors logged with correlation IDs (no sensitive data in logs) | S | Log review |
| **NFR-061** | Metrics | latency percentiles, scheduler health, notification send success exposed as metrics | S | Metrics dashboard |
| **NFR-062** | Health checks | readiness/liveness endpoints for the API and DB connectivity | S | Probe checks |

## 8. PWA & platform — *brief §4.3, §13*

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-070** | Installability | passes all Lighthouse PWA checks (manifest, service worker, installable) | M | Lighthouse PWA audit |
| **NFR-071** | Push standards | Web Push via VAPID, standards-compliant across supporting browsers | M | Cross-browser test |
| **NFR-072** | Caching strategy | cache-first for static assets, network-first for API | S | Service-worker test |

## 9. Quality & testability — *brief §13*

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-080** | Service-layer coverage | unit tests for **all** service-layer business logic | M | Coverage report in CI |
| **NFR-081** | Endpoint coverage | integration tests for **all** API endpoints | M | Coverage report in CI |
| **NFR-082** | AI grounding quality | assistant answers are grounded/cited; ungrounded questions are declined | M | RAG evaluation suite ([test-plan.md](test-plan.md)) |
| **NFR-083** | Regression safety | CI gates merges on passing tests + lint | S | CI pipeline |

## 10. Internationalization & localization

| ID | Requirement | Target | Priority | Verification |
|---|---|---|---|---|
| **NFR-090** | Locale-aware dates/times | timestamps stored as `TIMESTAMPTZ`, rendered in the user's locale | S | Functional tests |
| **NFR-091** | Multilingual AI | assistant responds in the user's input language (Turkish/English at minimum) | S | RAG language tests |
| **NFR-092** | TTS language match | TTS voice matches user system language with fallback | S | Manual TTS test |
