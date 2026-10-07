# Roadmap and Testing Strategy

Ordered by learning priorities: async, DB design, extensible architecture, API design, testing.

## Weekly plan (8 weeks)
| Wk | Focus | Deliverable |
|---|---|---|
| 1 | Setup + schema + **compat smoke test** | Gradle wrapper (9.1+), Docker Compose Postgres, Flyway V1 (via `spring-boot-starter-flyway`), entities/repos, Testcontainers base class, CI (GitHub Actions), basic `GET /transactions`. Complete the smoke test in [COMPATIBILITY.md](COMPATIBILITY.md) first. |
| 2 | Transaction CRUD + filtering | List with filters + paging, get / put category / delete, `ProblemDetail` error handling, indexes verified with `EXPLAIN` |
| 3 | CSV parsing | `BankFormat` + `MappingProfileFormat` + `csv_profiles` CRUD + preview endpoint + 1 coded format, streaming parser, normalization, row-error capture, heavy fixture-driven unit tests |
| 4 | **Async core** | `jobs` table, poller with SKIP LOCKED, lease/reaper, retry + backoff, DEAD; `POST /uploads` returns 202 + status endpoint |
| 5 | Pipeline end-to-end | PARSE job with batch idempotent insert + dedup, upload statuses (PARTIAL etc.), file-hash 409 |
| 6 | Rule engine | Rules CRUD, matchers, CATEGORIZE job, MANUAL precedence, `/rules/apply`, seed rules, 80% fixture test |
| 7 | Export + multi-currency | Streamed CSV export honoring filters, currency filter/display, failure-injection tests (kill worker mid-job) |
| 8 | Hardening + polish | OpenAPI (springdoc), Dockerfile, MDC logging with upload/job ids, optional Micrometer/Prometheus, README with architecture diagram, coverage pass |

Slack: week 8 is buffer. If behind, cut Prometheus first, then the coded `BankFormat`.
Risk: Java 25 / Spring Boot 4 are new. Pin versions in week 1 and see [COMPATIBILITY.md](COMPATIBILITY.md).

## Testing strategy
- **Unit (fast, most numerous):** matchers and engine, normalization and date/decimal parsing, dedup hash, backoff calc, exception classification. Mockito only at seams (`FileStore`); prefer real objects.
- **Integration (Testcontainers Postgres, `@ServiceConnection`):** repositories, Flyway migrations, job claiming under concurrency (2 threads, assert no double-claim), idempotent re-insert, filter queries. Real Postgres, not H2: SKIP LOCKED and NUMERIC behavior differ.
- **API (`MockMvc` / `@SpringBootTest`):** contract and `ProblemDetail` shape for every row in the status map; multipart upload. Use `@MockitoBean` (not `@MockBean`).
- **End-to-end:** upload fixture CSVs, await job completion (Awaitility), assert transactions/categories/counts, export and diff CSV round-trip.
- **Failure injection (priority #1):** malformed CSV, missing columns, poison row, exception mid-batch then retry, lease expiry (simulated crash), duplicate upload, categorizer throws and transactions are preserved.
- **Fixtures** in `src/test/resources/csv/`, per bank, including nasty cases: BOM, `;` delimiter, decimal comma, quoted newlines, blank lines.
- **Coverage:** JaCoCo (0.8.14+) gate. Treat 90% line coverage as a floor, not the goal. Exclude config/DTO boilerplate and prioritize branch coverage on parsing, rule and job-state code. PIT mutation testing is an optional stretch.

## Open items (defaults chosen)
- File storage: `bytea` (default) vs disk.
- Paging: offset (default) vs keyset.
- Currency: filter and display per currency, no FX.
