package com.kolosh.transactiontracker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Smoke test: boots the real application against a throwaway real Postgres. If Java 25, Boot 4,
 * Flyway, Hibernate schema validation and Testcontainers stop working together, this fails.
 */
@SpringBootTest
@Import(PostgresTestConfig.class)
class StackSmokeIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayRanAndRecordedItsMigration() {
        // Guards against the Boot 4 "silent Flyway" failure: if the starter is missing,
        // the app still boots but this table never gets created.
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }
}
