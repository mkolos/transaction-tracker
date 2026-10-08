package com.kolosh.transactiontracker.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.kolosh.transactiontracker.PostgresTestConfig;
import com.kolosh.transactiontracker.domain.Category;
import com.kolosh.transactiontracker.domain.CategorySource;
import com.kolosh.transactiontracker.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence tests against real Postgres. Each test runs in a transaction that is rolled
 * back, so tests don't leak data into each other.
 *
 * Several tests deliberately bypass the entity (raw SQL) to prove the DATABASE refuses bad
 * data, not just the Java code.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use our container
@Import(PostgresTestConfig.class)
class TransactionRepositoryIT {

    @Autowired
    TransactionRepository transactions;

    @Autowired
    CategoryRepository categories;

    @Autowired
    JdbcTemplate jdbc;

    private Transaction newTransaction() {
        return new Transaction(LocalDate.of(2026, 9, 1), new BigDecimal("-4.5000"), "USD", "Starbucks");
    }

    @Test
    void savesAndReadsBackWithExactAmount() {
        var saved = transactions.saveAndFlush(
                new Transaction(LocalDate.of(2026, 9, 1), new BigDecimal("-1234567.8901"), "EUR", "Rent"));

        var found = transactions.findById(saved.getId()).orElseThrow();

        assertThat(found.getAmount()).isEqualByComparingTo("-1234567.8901");
        assertThat(found.getCurrency()).isEqualTo("EUR");
        assertThat(found.getCategorySource()).isEqualTo(CategorySource.NONE);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void generatesTimeOrderedUuids() {
        var first = transactions.saveAndFlush(newTransaction());
        var second = transactions.saveAndFlush(newTransaction());

        // UUIDv7 puts a timestamp in the high bits, so later ids sort after earlier ones.
        assertThat(first.getId().version()).isEqualTo(7);
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @Test
    void manualCategoryAssignmentIsRecordedAsManual() {
        var food = categories.save(new Category("Food"));
        var tx = transactions.saveAndFlush(newTransaction());

        tx.assignCategoryManually(food);
        transactions.flush();

        var found = transactions.findById(tx.getId()).orElseThrow();
        assertThat(found.getCategory().getName()).isEqualTo("Food");
        assertThat(found.getCategorySource()).isEqualTo(CategorySource.MANUAL);
    }

    @Test
    void categoryNamesAreUnique() {
        categories.saveAndFlush(new Category("Food"));

        assertThatThrownBy(() -> categories.saveAndFlush(new Category("Food")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("categories_name_key");
    }

    @Test
    void databaseRejectsMalformedCurrency() {
        assertThatThrownBy(() -> insertRaw("usd", "NONE", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_currency_format");
    }

    @Test
    void databaseRejectsUnknownCategorySource() {
        assertThatThrownBy(() -> insertRaw("USD", "ROBOT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_category_source_valid");
    }

    @Test
    void databaseRejectsMatchedRuleWithoutRuleSource() {
        var food = categories.saveAndFlush(new Category("Food"));

        assertThatThrownBy(() -> insertRaw("USD", "MANUAL", food.getId(), 42L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_rule_requires_rule_source");
    }

    @Test
    void databaseRejectsCategorySourceWithoutCategory() {
        assertThatThrownBy(() -> insertRaw("USD", "MANUAL", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_source_requires_category");
    }

    @Test
    void databaseRejectsUnknownCategoryId() {
        assertThatThrownBy(() -> insertRaw("USD", "MANUAL", 999_999L, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_category_id_fkey");
    }

    private void insertRaw(String currency, String source, Long categoryId, Long ruleId) {
        jdbc.update("""
                INSERT INTO transactions (id, booked_on, amount, currency, description,
                                          category_id, category_source, matched_rule_id)
                VALUES (gen_random_uuid(), '2026-09-01', 1.00, ?, 'raw', ?, ?, ?)
                """, currency, categoryId, source, ruleId);
    }
}
