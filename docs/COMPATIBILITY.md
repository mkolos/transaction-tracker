# Tech Stack Compatibility (verified 2026-10-07)

Checked against current docs/issues before scaffolding. Re-verify when bumping versions.

## Verdict
**Java 25 + Spring Boot 4.1.x + Gradle 9.x + PostgreSQL works.** Four gotchas below must be handled in Week 1.

## Versions

| Component | Choice | Notes |
|---|---|---|
| Java | 25 (Temurin 25.0.4, already installed locally) | Boot 4.1.1 supports Java 17 through 26 |
| Spring Boot | **4.1.1** (not 4.0.x) | Spring Framework 7.0.9+; Tomcat 11 / Servlet 6.1 |
| Gradle | **9.1+** (use the wrapper) | Boot needs 8.14+ or 9.x; running Gradle itself on Java 25 needs 9.1+. Gradle is **not installed locally**; install once (`brew install gradle`) to generate the wrapper, then use `./gradlew` |
| Hibernate | 7.4.5 (managed by Boot) | Don't override |
| Flyway | 12.4.0 (managed by Boot 4.1.1) | See gotcha 1 and 2 |
| PostgreSQL JDBC | 42.7.13 (managed) | |
| PostgreSQL server | 17 or 18 | See gotcha 2 |
| Mockito / ByteBuddy | 5.23 / 1.18.11 (managed) | Older Mockito/ByteBuddy fail on Java 25 class files; do not pin older versions |
| Awaitility | 4.3.0 (managed) | |
| JaCoCo | **0.8.14+** | First release supporting Java 25; not managed by Boot, pin explicitly |
| Testcontainers | 2.x | Not in the Boot BOM per the managed-coordinates page; check whether `spring-boot-testcontainers` brings its BOM, else pin explicitly. See gotcha 3 |
| Commons CSV | pin explicitly (not managed) | |
| springdoc-openapi | pin explicitly; must be a Boot 4 / Spring 7 compatible line | Verify before Week 8 |
| Docker | 29.x installed locally | OK for Testcontainers |

## Gotchas

1. **Flyway needs the starter in Boot 4 (silent failure).** Auto-configuration moved into per-technology modules. `flyway-core` alone runs nothing and logs nothing. Use `spring-boot-starter-flyway` **plus** `flyway-database-postgresql`. Add a test that asserts the `flyway_schema_history` table exists so a regression can't go unnoticed.
2. **Flyway vs. newer PostgreSQL.** Boot 4.0.2 managed Flyway 11.14.1, which rejected PostgreSQL 18.1 (`Unsupported Database`; spring-boot issue #49012). Boot 4.1.1 manages Flyway 12.4.0, which should be fine, but **prove it in Week 1** by running the migration test against the exact image tag used in Compose and CI. Fallback: `postgres:17`.
3. **Testcontainers 2.0 renamed classes/modules.** `org.testcontainers.containers.PostgreSQLContainer` is deprecated in favor of `org.testcontainers.postgresql.PostgreSQLContainer` (artifact `testcontainers-postgresql`). Use the new package from day one. Reported caveat: with `@ServiceConnection` plus the new class, an R2DBC connection factory is not discovered. Not relevant here since we use JDBC, but confirm the JDBC `@ServiceConnection` works in the first integration test.
4. **Boot 4 test API changes.** `@MockBean` is gone in favor of Spring's `@MockitoBean`. Test auto-config is also modularized, so use the `spring-boot-starter-*-test` starters for the slices you use (webmvc, data-jpa) instead of relying on one catch-all.

## Verified by running (2026-10-08)
Smoke test (`StackSmokeIT`, `MockitoJava25Test`) passes with `./gradlew build` on Temurin 25.0.4 and Gradle 9.8.1:
- Flyway 12.4.0 migrates against **PostgreSQL 18.6** (gotcha 2 is a non-issue on Boot 4.1.1; keep `postgres:18`).
- **Testcontainers 2.0.5 is managed by Boot's BOM**: no explicit version needed, only the module artifacts (`testcontainers-junit-jupiter`, `testcontainers-postgresql`). Gotcha 3's `@ServiceConnection` works for JDBC.
- Hibernate 7.4.5, Mockito 5.23.0, ByteBuddy 1.18.11 resolve; Mockito works on Java 25 with no flags.
- JaCoCo 0.8.14 generates the report.
- Starters used: `spring-boot-starter-webmvc` (Boot 4 name), `-data-jpa`, `-flyway`, plus `-test`, `-webmvc-test`, `-data-jpa-test`, `spring-boot-testcontainers`.
- Still unpinned/unverified: Commons CSV, springdoc-openapi.

## Week 1 smoke test (do before building features)
1. `./gradlew bootRun` on Java 25 starts cleanly.
2. Flyway V1 applies against `postgres:<tag>` in Compose, and the Flyway log lines appear.
3. One Testcontainers + `@ServiceConnection` integration test passes: repository save/read of a `NUMERIC(19,4)` value.
4. A Mockito-based unit test runs on Java 25 with no `-Dnet.bytebuddy.experimental` flag.
5. JaCoCo report generates.

## Sources
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot 4.1.1 managed dependency versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- [Spring Boot 4.0.0 announcement](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/)
- [Flyway on Spring Boot 4 and Java 25: a silent failure](https://blog.zakaria.lu/flyway-on-spring-boot-4-and-java-25-a-silent-failure-and-what-it-taught-me-about-modular-auto-config)
- [spring-boot #49012: Flyway vs PostgreSQL 18.1](https://github.com/spring-projects/spring-boot/issues/49012)
- [spring-boot #47639: Testcontainers 2.0](https://github.com/spring-projects/spring-boot/issues/47639)
- [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
- [JaCoCo change history](https://www.jacoco.org/jacoco/trunk/doc/changes.html)
- [Mockito Java 25 compatibility issue #3754](https://github.com/mockito/mockito/issues/3754)
