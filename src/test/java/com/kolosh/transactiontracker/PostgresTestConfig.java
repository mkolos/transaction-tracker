package com.kolosh.transactiontracker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One place that defines the test database. Spring starts the container as a bean and
 * {@code @ServiceConnection} points spring.datasource.* at it. Test classes that share this
 * config share one cached application context, hence one container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // Keep this tag identical to compose.yaml.
        return new PostgreSQLContainer("postgres:18");
    }
}
