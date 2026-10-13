package com.kolosh.transactiontracker.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kolosh.transactiontracker.PostgresTestConfig;
import com.kolosh.transactiontracker.domain.Category;
import com.kolosh.transactiontracker.domain.CategorySource;
import com.kolosh.transactiontracker.domain.Transaction;
import com.kolosh.transactiontracker.repository.CategoryRepository;
import com.kolosh.transactiontracker.repository.TransactionRepository;

/**
 * Full stack: HTTP -> controller -> service -> JPA -> real Postgres. Deliberately NOT @Transactional,
 * so every request really commits and we prove that writes persist. Data is reset before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class TransactionApiIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionRepository transactions;
    @Autowired
    CategoryRepository categories;

    Category food;
    Category rent;
    Transaction coffee;
    Transaction rentPayment;
    Transaction sale;
    Transaction salary;
    Transaction coffee2;

    @BeforeEach
    void seed() {
        transactions.deleteAll();
        categories.deleteAll();
        food = categories.save(new Category("Food"));
        rent = categories.save(new Category("Rent"));

        coffee = tx("2026-09-01", "-4.50", "USD", "Starbucks Coffee");
        coffee.assignCategoryManually(food);
        rentPayment = tx("2026-09-05", "-1200.00", "EUR", "Monthly RENT");
        rentPayment.assignCategoryManually(rent);
        sale = tx("2026-09-10", "-50.00", "USD", "50% off sale_item");
        salary = tx("2026-09-15", "2500.00", "USD", "Salary");
        coffee2 = tx("2026-10-01", "-4.50", "USD", "starbucks");
        coffee2.assignCategoryManually(food);
        transactions.saveAll(java.util.List.of(coffee, rentPayment, sale, salary, coffee2));
    }

    private static Transaction tx(String date, String amount, String currency, String description) {
        return new Transaction(LocalDate.parse(date), new BigDecimal(amount), currency, description);
    }

    // ---- filters ----

    @Test
    void noFiltersReturnsEverythingNewestFirst() throws Exception {
        mvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].description",
                        contains("starbucks", "Salary", "50% off sale_item", "Monthly RENT", "Starbucks Coffee")));
    }

    @Test
    void dateRangeIsInclusiveOnBothEnds() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("from", "2026-09-05").param("to", "2026-09-15"))
                .andExpect(jsonPath("$.content[*].description",
                        containsInAnyOrder("Monthly RENT", "50% off sale_item", "Salary")));
    }

    @Test
    void fromOnlyAndToOnlyWork() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("from", "2026-09-15"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/transactions").param("to", "2026-09-01"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filtersByCategoryId() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("category", food.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].category.name", contains("Food", "Food")));
    }

    @Test
    void categoryFilterWithNoMatchesReturnsEmptyPage() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("category", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void uncategorizedReturnsOnlyRowsWithoutCategory() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("uncategorized", "true"))
                .andExpect(jsonPath("$.content[*].description", containsInAnyOrder("Salary", "50% off sale_item")));
    }

    @Test
    void currencyFilterIsCaseInsensitive() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("currency", "eur"))
                .andExpect(jsonPath("$.content[*].description", contains("Monthly RENT")));
    }

    @Test
    void textSearchIsCaseInsensitiveSubstring() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("q", "STARBUCKS"))
                .andExpect(jsonPath("$.content[*].description", containsInAnyOrder("Starbucks Coffee", "starbucks")));
        mvc.perform(get("/api/v1/transactions").param("q", "rent"))
                .andExpect(jsonPath("$.content[*].description", contains("Monthly RENT")));
    }

    @Test
    void textSearchTreatsLikeWildcardsLiterally() throws Exception {
        // "%" alone must not match everything; "50%" must match only the literal text.
        mvc.perform(get("/api/v1/transactions").param("q", "%"))
                .andExpect(jsonPath("$.content[*].description", contains("50% off sale_item")));
        mvc.perform(get("/api/v1/transactions").param("q", "_"))
                .andExpect(jsonPath("$.content[*].description", contains("50% off sale_item")));
        mvc.perform(get("/api/v1/transactions").param("q", "sale_item"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/transactions").param("q", "sale-item"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void blankSearchIsIgnored() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("q", "  "))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void amountRangeIsSignedAndInclusive() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("minAmount", "-50").param("maxAmount", "-4.5"))
                .andExpect(jsonPath("$.content[*].description",
                        containsInAnyOrder("Starbucks Coffee", "starbucks", "50% off sale_item")));
        mvc.perform(get("/api/v1/transactions").param("minAmount", "0"))
                .andExpect(jsonPath("$.content[*].description", contains("Salary")));
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        mvc.perform(get("/api/v1/transactions")
                        .param("category", food.getId().toString()).param("from", "2026-09-15").param("currency", "USD"))
                .andExpect(jsonPath("$.content[*].description", contains("starbucks")));
    }

    // ---- sorting and paging ----

    @Test
    void sortsByAmountAscending() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("sort", "amount,asc").param("size", "1"))
                .andExpect(jsonPath("$.content[0].description").value("Monthly RENT"));
    }

    @Test
    void pagingWithTiedSortKeysNeverRepeatsOrSkipsRows() throws Exception {
        // coffee and coffee2 have the same amount; the id tie-breaker keeps page boundaries stable.
        var seen = new HashSet<String>();
        for (int page = 0; page < 5; page++) {
            var body = mvc.perform(get("/api/v1/transactions")
                            .param("sort", "amount,asc").param("size", "1").param("page", String.valueOf(page)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var id = com.jayway.jsonpath.JsonPath.<String>read(body, "$.content[0].id");
            assertThat(seen.add(id)).as("page %d repeated an id", page).isTrue();
        }
        assertThat(seen).hasSize(5);
    }

    @Test
    void pageBeyondTheEndIsEmptyNotAnError() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("page", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    // ---- query errors against the real service ----

    @Test
    void invalidFilterCombinationsAre400() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("from", "2026-10-01").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/errors/invalid-request"));
        mvc.perform(get("/api/v1/transactions").param("minAmount", "10").param("maxAmount", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions").param("uncategorized", "true").param("category", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions").param("currency", "US"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortingByUnknownOrUnsafeFieldIs400() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("sort", "nonsense,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Allowed")));
        mvc.perform(get("/api/v1/transactions").param("sort", "matchedRuleId"))
                .andExpect(status().isBadRequest());
    }

    // ---- detail / update / delete ----

    @Test
    void getReturnsDetailIncludingSource() throws Exception {
        mvc.perform(get("/api/v1/transactions/{id}", coffee.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("-4.5000"))
                .andExpect(jsonPath("$.categorySource").value("MANUAL"))
                .andExpect(jsonPath("$.category.name").value("Food"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void getUnknownIdIs404() throws Exception {
        mvc.perform(get("/api/v1/transactions/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void putCategoryPersistsAsManual() throws Exception {
        mvc.perform(put("/api/v1/transactions/{id}/category", salary.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": " + rent.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.name").value("Rent"))
                .andExpect(jsonPath("$.categorySource").value("MANUAL"));

        var reloaded = transactions.findWithCategoryById(salary.getId()).orElseThrow();
        assertThat(reloaded.getCategory().getId()).isEqualTo(rent.getId());
        assertThat(reloaded.getCategorySource()).isEqualTo(CategorySource.MANUAL);
        assertThat(reloaded.getUpdatedAt()).isAfter(reloaded.getCreatedAt());
    }

    @Test
    void putCategoryWithUnknownCategoryIs422AndChangesNothing() throws Exception {
        mvc.perform(put("/api/v1/transactions/{id}/category", salary.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": 999999}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("/errors/unknown-category"));

        assertThat(transactions.findWithCategoryById(salary.getId()).orElseThrow().getCategory()).isNull();
    }

    @Test
    void putCategoryOnUnknownTransactionIs404() throws Exception {
        mvc.perform(put("/api/v1/transactions/{id}/category", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": " + food.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesTheRow() throws Exception {
        mvc.perform(delete("/api/v1/transactions/{id}", sale.getId())).andExpect(status().isNoContent());

        assertThat(transactions.existsById(sale.getId())).isFalse();
        mvc.perform(delete("/api/v1/transactions/{id}", sale.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/transactions/{id}", sale.getId())).andExpect(status().isNotFound());
    }

    // ---- categories ----

    @Test
    void categoriesCanBeCreatedAndListedByName() throws Exception {
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  Travel \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Travel"));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$[*].name", contains("Food", "Rent", "Travel")));
    }

    @Test
    void duplicateCategoryNameIs409EvenWithDifferentCase() throws Exception {
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"food\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/errors/duplicate-category"));
        assertThat(categories.count()).isEqualTo(2);
    }
}
