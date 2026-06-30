# Medications Dataset — Schema & RAG Ingestion Plan

> Planning document. No schema, migration, or ingestion code is written yet.
> Source: CC0-1.0 Tip-Atlası `ilaclardb` `ilac` table (`ilac.json` / `ilac.sql`),
> verified by direct inspection (see the facts table below).

## Verification of source data (done against the real files)
Both RAR5 archives were extracted and inspected directly. Confirmed / corrected
versus the going-in assumptions:

| Claim | Result |
|---|---|
| Table `ilac`, 11 cols, all `text NOT NULL` | ✅ confirmed from SQL DDL |
| 20,559 rows | ✅ exactly |
| ATC 100% populated | ✅ 0 empty — **but 4,940 rows have a single-letter ATC** (low precision) |
| 90 duplicate barcodes | ⚠️ Precisely: **30 distinct barcode values → 90 duplicate rows**; all **byte-identical** except `ID` |
| Active_Ingredient missing ~3.8% | ✅ 790 rows (789 `"Etken maddesi bilgisi bulunamadı."` + 1 empty) |
| Description placeholder is `"İkinci siteye ait içerik bulunamadı."` | ❌ **Dominant placeholder is `-` (16,976 rows)**; the given string is only 778; `"İçerik bulunamadı."` 11; empty handful |
| Real leaflets ~13.6% | ✅ 2,792 rows (13.6%) |
| Real leaflet ~2,150 chars avg | ❌ **median ~14,042, mean ~15,250, max 32,767**; ~7× larger |
| (new) truncation | ⚠️ **~50 leaflets sit at the 32,767-char ceiling** → truncated at source |
| (new) barcode format | 20,528 are 13-digit EAN; rest 12/14-digit + **8 non-numeric/odd (len 11,16)** |
| (new) categories | Real variable-depth hierarchy: Cat1=22, Cat2=112, Cat3=207, Cat4=442, Cat5=1254 distinct; deeper levels padded with `Yok`/`0` sentinels; Cat5 often duplicates Cat4 |
| (new) leaflet structure | **99% of real leaflets have ≥3 numbered sections** (`1. … NEDİR`, `2. … DİKKAT`, `4. … YAN ETKİLER`, …) → semantic chunking is viable |

## Locked decisions
- **Schema strategy:** *Rewrite V2 as greenfield* — edit
  `backend/src/main/resources/db/migration/V2__core_tables.sql` directly to add
  the new columns (valid because the DB is not yet deployed). No new V7.
- **Per-box model:** *Extend `user_medications`* — add `batch` + `serial_number`
  and change its UNIQUE constraint so one row = one physical box.

## Context
ECZAM needs a seed catalog of Turkish medicines so that a client-side GS1
DataMatrix scan (GTIN/AI 01) can be resolved to a product (name, active
ingredient, leaflet). The source is the CC0-1.0 Tip-Atlası `ilaclardb` `ilac`
table (20,559 rows). The same dataset's ~2,792 real patient leaflets
(*Kullanma Talimatı*) seed the RAG assistant. This plan defines the schema
changes, the leaflet/RAG ingestion pipeline, the import script, and the open
decisions — grounded in a direct inspection of the data above, not the original
assumptions, several of which were wrong.

Key correction driving the design: **the dominant Description placeholder is a
bare `-`, not the documented sentence, and real leaflets are ~15k chars, not
~2k** — so placeholder detection must be data-driven and chunking is mandatory.

## Guiding principle: GTIN is the join key, not the raw barcode
A scan yields a GS1 **GTIN-14** (AI 01). The dataset stores mostly **EAN-13**.
Both sides MUST be canonicalised to the same 14-digit form or lookups silently
miss. Canonical rule: strip non-digits → left-zero-pad to 14
(EAN-13 → prepend `0`, UPC-12 → prepend `00`, 14 stays). The scanner's decoded
GTIN is normalised identically before the lookup.

---

## 1. Schema design

All changes are **edits to V2** (`backend/src/main/resources/db/migration/V2__core_tables.sql`),
per the locked greenfield decision.

### 1.1 `medications` (global catalog — the scan hot path)
Add to the existing table:

| Column | Type | Notes |
|---|---|---|
| `gtin` | `VARCHAR(14)` **UNIQUE** | Canonical 14-digit join key. The scan hot path. `NULL` for the 8 unparseable barcodes (search-only, never scan-matchable). |
| `barcode` | `VARCHAR(50)` | Keep the original source barcode as-stored (already exists; widen/keep). Not the join key. |
| `atc_code` | `VARCHAR(16)` | As-is from source. |
| `atc_group` | `CHAR(1)` GENERATED or set on import | First letter = anatomical main group, for coarse grouping. |
| `active_ingredient` | `VARCHAR(512)` | `NULL` when source is placeholder/empty (don't store the sentinel). |
| `category_path` | `JSONB` | Cleaned ordered array of non-sentinel categories, e.g. `["Kas İskelet Sistemi","Antienflamatuar…","Non-steroid"]`. See §1.4. |
| `leaflet_raw` | `TEXT` | Full leaflet text **only when real**; `NULL` otherwise. (exists) |
| `leaflet_sections` | `JSONB` | Parsed `{what_is, before_use, how_to_use, side_effects, storage}` for fast non-RAG display. (exists) |
| `leaflet_truncated` | `BOOLEAN DEFAULT FALSE` | TRUE for the ~50 rows at the 32,767 ceiling → RAG can caveat. |
| `vector_indexed` | `BOOLEAN DEFAULT FALSE` | Embedding job progress flag. (exists) |
| `leaflet_hash` | `CHAR(64)` NULL | SHA-256 of `leaflet_raw`; lets re-embed skip unchanged text. |

Keep existing `name` (`Product_Name`), `generic_name`, `manufacturer`, `form`,
`strength`, `id` UUID PK, `created_at`.

**Indexing strategy**
- `CREATE UNIQUE INDEX … ON medications(gtin) WHERE gtin IS NOT NULL;` — partial
  unique so the 8 NULL-gtin rows don't collide. This is the lookup index; B-tree
  on a 14-char exact-match key keeps the scan resolve well under the p95<300ms
  budget.
- `idx_medications_atc` on `atc_code` for therapeutic queries.
- `GIN` on `category_path` only if category browse is built (not MVP — see §4).

### 1.2 Duplicate-barcode strategy — **dedup on canonical GTIN, keep first**
Justification from the data: all 30 duplicated barcode values have **byte-identical
rows** (same `Product_Name` and `Description`; only `ID` differs). So collapsing
to one row per GTIN is **lossless**. Import uses
`ON CONFLICT (gtin) DO NOTHING` keyed on the partial unique index, keeping the
first (lowest source `ID`).

Forward-safety: the conflict handler must **compare `name`/`leaflet_raw` on
conflict and log any divergence** (future dataset versions may have non-identical
dups). Silent keep-first is correct *today* precisely because divergence is zero;
the logging guard makes that assumption observable rather than permanent.

### 1.3 `user_medications` (per-physical-box inventory) — extend
The existing table is the inventory table but lacks per-box GS1 facts and has a
UNIQUE constraint that would merge two boxes sharing an expiry. Changes:

| Column | Nullable? | Reason |
|---|---|---|
| `batch` (lot, GS1 AI 10) | **nullable** | Not all boxes/scans carry it; manual adds have none. Per-box fact, never derived from GTIN. |
| `serial_number` (GS1 AI 21) | **nullable** | Same; many older boxes lack a serial. |
| `expiration_date` (AI 17) | nullable (exists) | Per-box; manual entry possible. |
| `quantity` (exists) | required | Decremented per logged dose (core invariant). |
| `medication_id` FK (exists) | required | The product type. |
| `user_id` FK (exists) | required | Owner. |

Replace `UNIQUE(user_id, medication_id, expiration_date)` with
`UNIQUE(user_id, medication_id, batch, serial_number, expiration_date)` so each
physical box is its own row. Because serial is per-box-unique when present, also
consider a partial unique `(user_id, serial_number) WHERE serial_number IS NOT
NULL` to stop the same box being scanned in twice. **batch/serial/expiry are
decoded fresh every scan and are NEVER looked up from `medications`.**

### 1.4 Category handling — **cleaned `JSONB` array on `medications`** (recommended)
Tradeoffs considered:
- *Flat `category_1..5` columns:* faithful but carries `Yok`/`0` sentinel noise
  and the Cat4=Cat5 leaf duplication; hard to query hierarchically. ❌
- *Normalised `therapeutic_categories(parent_id)` table:* proper hierarchy and
  dedups the 1,254 leaf values, but it's over-engineering for MVP — the brief
  lists **no** category-browse feature, and the source hierarchy is too noisy
  (sentinels, duplicated leaves) to trust as a clean dimension. ❌ for MVP.
- *Cleaned `JSONB category_path` array:* ✅ store the ordered non-sentinel path
  (drop `''`, `-`, `Yok`, `0`, and a trailing leaf identical to its parent).
  Order preserved, GIN-indexable if ever needed, zero new tables.

Recommendation: **JSONB `category_path`** as display/metadata only, with
`atc_code`/`atc_group` as the *canonical* therapeutic classifier (cleaner and
standardised). Note in code a clear path to normalise later if category browse
becomes a feature.

---

## 2. Leaflet / RAG ingestion (the ~13.6% with real `Description`)

### 2.1 Real-vs-placeholder detection (data-driven, not the one given string)
A description is **real** iff, after `trim()`:
1. it is not in `{"", "-"}`, **and**
2. it does not match `/(içerik|etken maddesi.*)?bulunamad[ıi]/i` (covers
   `İkinci siteye ait içerik bulunamadı.`, `İçerik bulunamadı.`, the AI
   sentinel), **and**
3. `length >= 150` (observed real-leaflet min was 166; guards stray short junk).
This rule classifies exactly the 2,792 real leaflets and rejects the 17,767
placeholders/`-`/empty/short rows. The same `bulunamad[ıi]` rule also nulls the
790 placeholder `active_ingredient` values.

### 2.2 Chunking — **semantic by numbered section, then size-split** (recommended)
99% of real leaflets follow the standard *Kullanma Talimatı* structure:
1. **… NEDİR ve NE İÇİN KULLANILIR** → `what_is`
2. **… KULLANMADAN ÖNCE DİKKAT EDİLMESİ GEREKENLER** → `before_use`
3. **NASIL KULLANILIR** → `how_to_use`
4. **OLASI YAN ETKİLER NELERDİR** → `side_effects`
5. **SAKLANMASI** → `storage`

Primary boundary = these numbered headings (regex on `^\s*\d\s*[.\)]` anchored to
known heading keywords; map to the 5 canonical `section_name`s). Then **sub-split
any section exceeding the embedding budget** (~512 tokens ≈ ~1,800 chars) into
sequential sub-chunks with ~15% overlap. Reasoning: section-aware chunks (a)
satisfy the CLAUDE.md guardrail that the assistant **cite which leaflet section**
an answer came from, (b) improve retrieval precision over fixed-size windows
because side-effects vs. dosage queries map cleanly to sections, and (c) sections
2 and 4 are the large ones (often >5k chars) and genuinely need sub-splitting —
fixed-size-only would shred section boundaries and lose the citation anchor. The
~1% of leaflets without parseable sections fall back to fixed-size 1,800/overlap
chunking with `section_name='unknown'`.

### 2.3 `leaflet_chunks` (pgvector) — keep & enrich existing table
Existing columns (`id`, `medication_id` FK ON DELETE CASCADE, `section_name`,
`chunk_text`, `embedding VECTOR(1536)`, `chunk_index`, `created_at`) are kept.
Add metadata for better retrieval/citation:

| Column | Purpose |
|---|---|
| `section_ordinal` `SMALLINT` | 1–5 canonical section number, for ordered display/citation. |
| `char_start` / `char_len` `INT` | Provenance back into `leaflet_raw`. |
| `source_lang` `CHAR(2) DEFAULT 'tr'` | Dataset is Turkish-only; supports the "answer in user's language" rule. |
| `token_count` `SMALLINT` | Retrieval budgeting. |

- Embedding model: **OpenAI `text-embedding-3-small` (1536-dim)** — matches the
  existing `VECTOR(1536)` column and HNSW index (`idx_leaflet_chunks_embedding
  USING hnsw (embedding vector_cosine_ops)`). (Anthropic has no embeddings API;
  Claude stays the chat model.)
- Scale: ~2,792 leaflets × ~8–12 chunks ≈ **25k–35k chunks** — trivial cost/time.

### 2.4 Fallback for the 86.4% with no real leaflet (no hallucination)
Two-gate fallback in the RAG flow:
1. **Pre-gate:** if the target medication has `vector_indexed = FALSE` / no
   `leaflet_chunks`, short-circuit before calling Claude.
2. **Retrieval gate:** even when chunks exist, if the top cosine similarity is
   below a tuned threshold, treat as "no grounded passage."
Either gate → return the guardrail response (in the user's language), e.g.
*"Bu ilaç için elimde prospektüs bilgisi bulunmuyor; lütfen eczacınıza veya
doktorunuza danışın."* — never a generated medical answer. This directly
implements the CLAUDE.md §7 / brief AI guardrails. Truncated leaflets
(`leaflet_truncated=TRUE`) get an appended caveat that the leaflet may be
incomplete.

---

## 3. Import / seed script

**Tooling decision: in-stack Java, gated by Spring profile** (not Flyway SQL).
Rationale: GTIN canonicalisation, placeholder detection, section parsing, and
embedding API calls are not expressible in SQL, and a Java
`ApplicationRunner` reuses the same JPA entities, JSON config, and embedding
client the app already ships — no second language/toolchain to maintain. (Python
is an acceptable alternative for the parsing stage if preferred; the embedding
stage should stay where the API client lives.)

**Two idempotent stages, each behind its own flag:**

- **Stage A — catalog seed** (`app.seed.catalog=true`): read `ilac.json`,
  canonicalise GTIN, dedup, clean active ingredient + `category_path`, detect
  real leaflets, parse `leaflet_sections`, set `leaflet_truncated`/`leaflet_hash`,
  upsert into `medications` via `ON CONFLICT (gtin) DO NOTHING` (logging
  divergence). Idempotent by the `gtin` unique key — re-runs add nothing.
- **Stage B — embed** (`app.seed.embeddings=true`): select medications with a
  real leaflet AND (`vector_indexed=FALSE` OR `leaflet_hash` changed); chunk →
  embed → `DELETE` existing chunks for that medication → insert new →
  set `vector_indexed=TRUE`. Resumable (only unindexed/changed rows),
  rate-limit-aware, safe to re-run after a crash.

**Seed vs sync:** this is a one-time CC0 **snapshot**, so treat it as a **seed
step** at deploy/setup. The same two jobs double as a **refresh** path for a new
dataset version (the `leaflet_hash` gate re-embeds only changed leaflets). The
source file (~6 MB compressed) should be a build/release artifact or a configured
local path — **not committed raw** (the 55 MB JSON is too big for git;
`.gitignore` already excludes the `.rar`s).

**Parsing source:** prefer `ilac.json` (clean structured parse) over the SQL dump;
they are the same table.

---

## 4. Open questions / risks to decide before implementation

1. **Embedding provider egress** — leaflets are public CC0 text (no PII), so
   sending to OpenAI `text-embedding-3-small` is low-risk; confirm OpenAI vs a
   local/TR model. Turkish quality of `text-embedding-3-small` is acceptable but
   not best-in-class — accept for MVP?
2. **~50 truncated leaflets** (32,767 ceiling) — accept truncated + flag (MVP), or
   re-scrape source later (out of scope)? Recommend accept+flag.
3. **4,940 single-letter ATC codes** — fine as coarse `atc_group` only? (Yes for
   MVP; flag low-precision.)
4. **8 non-numeric/odd-length barcodes** — store with `gtin = NULL` → search-only,
   never scan-matchable. Confirm acceptable.
5. **Categories not a core feature** — confirm storing as JSONB metadata only
   (no normalised table, no browse UI) for MVP.
6. **Dataset hosting** — where does the loader read `ilac.json` from at
   deploy/CI (release asset, git-LFS, mounted path)?
7. **Leaflet freshness / liability** — leaflets may be outdated; the assistant
   must show source + a "consult a pharmacist" disclaimer (already a guardrail).
8. **KVKK** — catalog data is non-personal; only `user_medications` (batch/
   serial/expiry tied to a user) is personal/health data and inherits existing
   retention/cascade rules.

---

## 5. Verification (how to confirm once implemented — future step)
- After Stage A: `SELECT count(*) FROM medications` → 20,469 (20,559 − 90 dups);
  `count(gtin)` ≈ 20,461 (8 NULL); spot-check a known GTIN
  (`08681030190415`) resolves to one ARMANAKS row.
- After Stage B: `SELECT count(*) FROM medications WHERE vector_indexed`
  → 2,792; `leaflet_chunks` ≈ 25k–35k; an HNSW cosine query for "yan etkiler"
  returns side-effects-section chunks.
- RAG fallback: query a no-leaflet drug → guardrail message, no generated
  medical content.
