package com.kolosh.transactiontracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer; // Testcontainers 2.x package

/**
 * Week 1 smoke test: proves the whole stack (Java 25, Boot 4, Flyway, Postgres, Testcontainers)
 * works together. Boots the real application against a throwaway real Postgres.
 */
@SpringBootTest
@Testcontainers
class StackSmokeIT {

    // @ServiceConnection tells Spring Boot to point spring.datasource.* at this container,
    // replacing the localhost URL from application.yaml. No manual property wiring.
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

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

    @Test
    void numericRoundTripsWithoutFloatingPointError() {
        // 0.1 + 0.2 style bugs are why money is NUMERIC, never double.
        BigDecimal stored = new BigDecimal("1234567.8901");
        jdbc.update("INSERT INTO smoke_money(amount) VALUES (?)", stored);

        BigDecimal read = jdbc.queryForObject(
                "SELECT amount FROM smoke_money ORDER BY id DESC LIMIT 1", BigDecimal.class);
        assertThat(read).isEqualByComparingTo(stored);
    }
}
