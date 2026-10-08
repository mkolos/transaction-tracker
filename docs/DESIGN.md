# Transaction Tracker: Design

Status: approved 2026-10-07. Implementation started 2026-10-08 (scaffold and the categories/transactions schema are done). See [ROADMAP.md](ROADMAP.md) for schedule and testing and [COMPATIBILITY.md](COMPATIBILITY.md) for verified versions.

## 1. Key decisions (summary)
1. **Postgres job table over `@Async`/broker.** `@Async` is an in-memory pool: jobs vanish on restart, with no retry or status. A table with `FOR UPDATE SKIP LOCKED` gives durability, atomic enqueue with data, and teaches real queue mechanics, with zero extra infra.
2. **Two-stage pipeline** (parse+insert, then categorize) with **idempotent inserts**, so at-least-once delivery is safe.
3. **`NUMERIC(19,4)` money, original currency only**, no FX in MVP.
4. **Dedup** via unique hash with an occurrence index, plus a whole-file hash.
5. **Rules in DB**, first-match-wins, strategy-pattern matchers, MANUAL edits always win.
6. **Row errors are data** (`import_errors`), not exceptions. Permanent vs transient failures are classified.
7. **CSV formats: hybrid.** DB mapping profiles by default, coded `BankFormat` plugins as escape hatch.
8. RFC 7807 errors, 202 + polling, Flyway migrations, classic layered packages: `domain` (entities), `repository`, `service`, `api` (controllers), later `jobs`, `parsing`.
9. Interfaces at the seams (`BankFormat`, `FileStore`, `RuleMatcher`): each is a test seam and a v2 extension point.

### Async options considered
| Option | Pros | Cons |
|---|---|---|
| `@Async` only | Trivial | Lost on restart, no retry, no status |
| **Postgres job table (chosen)** | No new infra; enqueue atomic with upload row; teaches queue internals; inspect with SQL | ~150 lines of poller; throughput ceiling (irrelevant here) |
| RabbitMQ/Kafka | Industry standard, scales | Extra container, dual-write problem, overkill for single user |
| JobRunr / db-scheduler | Retries/dashboard free | Hides the learning; reasonable v2 swap |

## 2. Why PostgreSQL
- Exact `NUMERIC` decimals (never float/double).
- Constraints as correctness: unique indexes for dedup, CHECK/FK for integrity.
- `SKIP LOCKED` is the queue; JSONB for raw rows; `pg_trgm` for fuzzy search later.
- Tradeoff: heavier than SQLite locally. Docker Compose + Testcontainers solve it, and tests run on the real engine.

## 3. Schema (v1)

**Built incrementally: one Flyway migration per feature, only what that feature needs.** The block below is the *target* schema, not what exists today.

| Migration | Adds | Notes |
|---|---|---|
| `V1__categories_and_transactions` (done) | `categories`; `transactions` without `upload_id`, `description_norm`, `dedup_hash` | `matched_rule_id` is a plain `BIGINT` for now; the rule-engine migration adds the FK. DB `CHECK`s enforce currency format, valid `category_source`, rule-implies-RULE-source, and source-implies-category. |
| later | `csv_profiles`, `uploads`, `jobs`, `import_errors`, `categorization_rules`, `dedup_hash`/`upload_id`/`description_norm` columns | Each lands with its feature week. |

```
categories(id PK, name UNIQUE, created_at)

csv_profiles(id PK, name UNIQUE, delimiter, decimal_separator, date_format, encoding,
             skip_rows, date_col, description_col, amount_col | (debit_col, credit_col),
             currency_col | default_currency, header_signature, created_at)

uploads(id PK uuid, filename, profile_id FK NULL, format_name,
        status[PENDING|PROCESSING|COMPLETED|PARTIAL|FAILED],
        file_sha256 UNIQUE, content bytea, total_rows, imported_rows,
        skipped_rows, error_rows, created_at, finished_at)

jobs(id PK, upload_id FK, type[PARSE|CATEGORIZE], status[QUEUED|RUNNING|DONE|FAILED|DEAD],
     attempts, max_attempts, run_after timestamptz, locked_until timestamptz,
     last_error text, created_at)
  INDEX (status, run_after)

transactions(id PK uuid, upload_id FK NULL, booked_on date, amount NUMERIC(19,4) signed,
             currency CHAR(3), description text, description_norm text,
             category_id FK NULL, category_source[RULE|MANUAL|NONE], matched_rule_id FK NULL,
             dedup_hash CHAR(64) UNIQUE, created_at, updated_at)
  INDEX (booked_on), (category_id, booked_on), (currency)

categorization_rules(id PK, category_id FK, match_type[CONTAINS|EQUALS|STARTS_WITH|REGEX],
                     pattern, min_amount NULL, max_amount NULL, currency NULL,
                     priority int, enabled bool, created_at)

import_errors(id PK, upload_id FK, row_number, raw_row text, reason)
```

Why:
- **Signed `amount` + `currency`, no conversion.** Filter by currency, never sum across currencies. Cost: no cross-currency totals until v2 adds rates.
- **`dedup_hash`** = sha256(booked_on | amount | currency | normalized description | occurrence_index), UNIQUE, with `INSERT ... ON CONFLICT DO NOTHING`. The occurrence index keeps two genuine identical purchases on one day. A bank reference id would be better but not all banks supply one.
- **`file_sha256`** rejects an identical file cheaply (409) before any work.
- **`category_source` + `matched_rule_id`** answer "why is this categorized so?" and guarantee rule re-runs never overwrite MANUAL edits.
- **`import_errors`** keeps bad rows for the user instead of failing the file.
- **File as `bytea`** vs disk: transactional with the job, simplest, fine for MB-sized CSVs. Hidden behind a `FileStore` interface.
- **Deferred to v2:** accounts, users, splits, budgets, FX rates, tags.
- Migrations: **Flyway** versioned SQL, `ddl-auto=none`.

## 4. API (REST, JSON, `/api/v1`)

| Endpoint | Purpose |
|---|---|
| `POST /uploads` (multipart `files[]`, optional `profileId`/`bankFormat`) | 202 + upload ids |
| `GET /uploads/{id}` | Status, counts, errors link |
| `GET /uploads/{id}/errors` | Row-level failures |
| `GET /transactions?from=&to=&category=&currency=&q=&minAmount=&maxAmount=&uncategorized=true&page=&size=&sort=` | List |
| `GET /transactions/{id}` | Detail incl. `categorySource`, `matchedRule` |
| `PUT /transactions/{id}/category` `{ "categoryId": 3 }` | Manual override (source=MANUAL) |
| `DELETE /transactions/{id}` | Hard delete |
| `GET/POST/PUT/DELETE /rules`, `GET/POST /categories` | Rule and category management |
| `POST /rules/apply` | Re-run rules over non-MANUAL rows (returns job id) |
| `GET/POST/PUT/DELETE /csv-profiles` | CSV profile management |
| `POST /csv-profiles/preview` | Upload sample, get first 5 normalized rows |
| `GET /export?<same filters>` | Streamed `text/csv` |

Upload:
```
POST /api/v1/uploads -> 202 Accepted
{ "uploads": [ { "id": "u_01H...", "filename": "chase_sep.csv", "status": "PENDING",
                 "links": { "self": "/api/v1/uploads/u_01H..." } } ] }
```
Status after partial failure:
```
GET /api/v1/uploads/u_01H...
{ "id": "u_01H...", "status": "PARTIAL", "totalRows": 120, "importedRows": 112,
  "skippedDuplicates": 5, "errorRows": 3, "categorizedRows": 98,
  "errors": "/api/v1/uploads/u_01H.../errors" }
```
Errors use RFC 7807 `application/problem+json` (Spring `ProblemDetail`):
```
400 { "type": ".../errors/csv-missing-columns", "title": "Missing required columns",
      "status": 400, "detail": "Required: date, amount. Found: Datum, Betrag",
      "missing": ["date","amount"] }
```
Status map: 202 accepted async, 400 malformed request, 404, 409 duplicate file, 413 too large, 415 not CSV, 422 semantically invalid (unknown category, no matching CSV profile).

Decisions: 202 + polling (SSE/webhooks are v2). Offset paging now, keyset later. Money as decimal string in JSON. Upload-time validation is shallow (extension, size, header sniff); row validation happens in the job.

## 5. Async architecture

```
Client --POST /uploads--> API --+- tx: insert uploads(PENDING) + jobs(PARSE,QUEUED) --> 202
                                |
                     poller @Scheduled (~1s)
                                v
   claim: SELECT ... FROM jobs WHERE status=QUEUED AND run_after<=now()
          ORDER BY id FOR UPDATE SKIP LOCKED LIMIT n
          -> RUNNING, locked_until = now()+5m
                                v
   PARSE job
     choose/detect BankFormat -> stream rows -> NormalizedRow(date, amount, currency, desc)
     invalid row -> import_errors
     batch (500) INSERT ... ON CONFLICT DO NOTHING
     enqueue CATEGORIZE job (same tx)
                                v
   CATEGORIZE job
     load enabled rules (cached per job), ordered by priority
     for txs with category_source != MANUAL: first match wins -> category/rule/source
     no match -> NONE (not an error)
                                v
   upload.status = COMPLETED | PARTIAL | FAILED

 failure -> attempts++; run_after = now() + 2^attempts * 30s; attempts >= max -> DEAD
 crash   -> locked_until expires -> reaper resets RUNNING -> QUEUED (at-least-once)
```

**One job vs. stages.** One big job is simpler, but a categorization bug would force re-parsing and re-categorizing later is impossible. Two stages allow independent retry and let `POST /rules/apply` reuse CATEGORIZE. Splitting further (normalize vs create rows) needs a staging table for little value.

**Partial failures**
- *Bad row*: recorded in `import_errors`, file continues, upload ends `PARTIAL`.
- *Structural failure* (no header, missing required columns, unreadable encoding): `FAILED`, **no retry** (deterministic). `PermanentException` vs transient exceptions drive this.
- *Categorization fails*: transactions are already committed and remain uncategorized. The categorize job is retried. Data is never lost to a downstream stage.
- *DB error mid-parse*: each batch is its own transaction. Inserts are idempotent, so retrying from the start is safe. Idempotency is what makes at-least-once safe.
- *Dead job*: `DEAD` is the dead-letter queue, exposed via `GET /uploads/{id}`.

## 6. CSV format handling (hybrid)
Options considered: (1) hard-coded per bank, (2) DB column-mapping profiles, (3) profiles + transform expressions (mini-DSL, too costly and risky for 8 weeks), (4) hybrid. **Chosen: 4.**

- `BankFormat` interface: `detect(headers) -> score`, `parse(row) -> NormalizedRow`. The pipeline only knows this interface.
- **`MappingProfileFormat`** (default): driven by a `csv_profiles` row (delimiter, decimal separator, date format, encoding, skip rows, column names, optional debit/credit pair, currency column or default). New banks need no deploy.
- **Coded `BankFormat` beans** for banks a profile can't express (currency embedded in amount, multi-line records). Start with one to prove the escape hatch.
- **Selection:** explicit `profileId`/`bankFormat`, else auto-detect via header signature. None or ambiguous gives 422 listing the headers found.
- `POST /csv-profiles/preview` makes profile authoring debuggable.
- Streaming parser (Commons CSV or OpenCSV); BOM, delimiter and decimal-comma handling live in the profile layer.

## 7. Categorization rule engine
- **Rules in DB, not code/config.** Users add rules at runtime (in scope). Code/YAML is versioned but needs a deploy. Seed defaults via Flyway.
- **`RuleMatcher` strategy interface** (`CONTAINS, EQUALS, STARTS_WITH, REGEX`) resolved from a registry by `match_type`. A new matcher is one class. Optional amount-range and currency conditions on the rule.
- **Semantics:** normalize description (lowercase, strip accents, collapse whitespace), evaluate by `priority` ascending, **first match wins**. Scoring/ML is v2.
- **Safety:** validate REGEX on save and cap length (ReDoS).
- **Performance:** load rules once per job; O(rows x rules) is fine here.
- **Precedence:** MANUAL never overwritten. Deleting a rule does not un-categorize existing transactions.
- **80% target:** asserted by a fixture-CSV test with seed rules; `categorizedRows` exposed on upload status.
