package com.kolosh.transactiontracker.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kolosh.transactiontracker.domain.Category;
import com.kolosh.transactiontracker.domain.Transaction;
import com.kolosh.transactiontracker.service.CategoryService;
import com.kolosh.transactiontracker.service.Exceptions.DuplicateCategoryException;
import com.kolosh.transactiontracker.service.Exceptions.InvalidRequestException;
import com.kolosh.transactiontracker.service.Exceptions.NotFoundException;
import com.kolosh.transactiontracker.service.Exceptions.UnknownCategoryException;
import com.kolosh.transactiontracker.service.TransactionFilter;
import com.kolosh.transactiontracker.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract only: status codes, JSON shapes, error bodies, parameter binding.
 * Services are mocked, so no database is involved. Real query behaviour is in TransactionApiIT.
 */
@WebMvcTest({TransactionController.class, CategoryController.class})
class TransactionApiTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TransactionService transactions;

    @MockitoBean
    CategoryService categories;

    private static Category category(long id, String name) {
        var c = new Category(name);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static Transaction transaction(UUID id, Category category) {
        var t = new Transaction(LocalDate.of(2026, 9, 1), new BigDecimal("-4.5000"), "USD", "Starbucks");
        ReflectionTestUtils.setField(t, "id", id);
        if (category != null) {
            t.assignCategoryManually(category);
        }
        return t;
    }

    // ---- GET /transactions ----

    @Test
    void listReturnsPageEnvelopeWithMoneyAsString() throws Exception {
        var id = UUID.randomUUID();
        given(transactions.search(any(), any())).willReturn(
                new PageImpl<>(List.of(transaction(id, category(3, "Food"))), Pageable.ofSize(20), 41));

        mvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].amount").value("-4.5000"))
                .andExpect(jsonPath("$.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.content[0].category.id").value(3))
                .andExpect(jsonPath("$.content[0].category.name").value("Food"))
                .andExpect(jsonPath("$.content[0].categorySource").value("MANUAL"))
                .andExpect(jsonPath("$.totalElements").value(41))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listShowsNullCategoryForUncategorized() throws Exception {
        given(transactions.search(any(), any())).willReturn(
                new PageImpl<>(List.of(transaction(UUID.randomUUID(), null)), Pageable.ofSize(20), 1));

        mvc.perform(get("/api/v1/transactions"))
                .andExpect(jsonPath("$.content[0].category").doesNotExist())
                .andExpect(jsonPath("$.content[0].categorySource").value("NONE"));
    }

    @Test
    void listBindsAllFiltersAndPaging() throws Exception {
        given(transactions.search(any(), any())).willReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/transactions")
                        .param("from", "2026-09-01").param("to", "2026-09-30")
                        .param("category", "3").param("currency", "usd").param("q", "coffee")
                        .param("minAmount", "-100.50").param("maxAmount", "0")
                        .param("uncategorized", "false")
                        .param("page", "2").param("size", "50").param("sort", "amount,asc"))
                .andExpect(status().isOk());

        var filter = ArgumentCaptor.forClass(TransactionFilter.class);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).search(filter.capture(), pageable.capture());
        assertThat(filter.getValue()).isEqualTo(new TransactionFilter(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 3L, "usd", "coffee",
                new BigDecimal("-100.50"), BigDecimal.ZERO, false));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("amount").isAscending()).isTrue();
    }

    @Test
    void listCapsPageSizeAtConfiguredMaximum() throws Exception {
        given(transactions.search(any(), any())).willReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/transactions").param("size", "100000")).andExpect(status().isOk());

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).search(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void listWithBadDateIs400Problem() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
        verifyNoInteractions(transactions);
    }

    @Test
    void listWithNonNumericAmountOrCategoryIs400Problem() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("minAmount", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        mvc.perform(get("/api/v1/transactions").param("category", "food"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void listMapsInvalidFilterCombinationTo400Problem() throws Exception {
        given(transactions.search(any(), any())).willThrow(new InvalidRequestException("'from' must not be after 'to'"));

        mvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/errors/invalid-request"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("'from' must not be after 'to'"));
    }

    // ---- GET /transactions/{id} ----

    @Test
    void getReturnsDetailWithSourceAndMatchedRule() throws Exception {
        var id = UUID.randomUUID();
        given(transactions.get(id)).willReturn(transaction(id, category(3, "Food")));

        mvc.perform(get("/api/v1/transactions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.bookedOn").value("2026-09-01"))
                .andExpect(jsonPath("$.amount").value("-4.5000"))
                .andExpect(jsonPath("$.description").value("Starbucks"))
                .andExpect(jsonPath("$.categorySource").value("MANUAL"))
                .andExpect(jsonPath("$.matchedRuleId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.category.name").value("Food"));
    }

    @Test
    void getUnknownIdIs404Problem() throws Exception {
        var id = UUID.randomUUID();
        given(transactions.get(id)).willThrow(new NotFoundException("Transaction " + id + " not found"));

        mvc.perform(get("/api/v1/transactions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/errors/not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Transaction " + id + " not found"));
    }

    @Test
    void getMalformedUuidIs400Problem() throws Exception {
        mvc.perform(get("/api/v1/transactions/{id}", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    // ---- PUT /transactions/{id}/category ----

    @Test
    void putCategoryReturnsUpdatedDetail() throws Exception {
        var id = UUID.randomUUID();
        given(transactions.assignCategory(id, 3L)).willReturn(transaction(id, category(3, "Food")));

        mvc.perform(put("/api/v1/transactions/{id}/category", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.id").value(3))
                .andExpect(jsonPath("$.categorySource").value("MANUAL"));
    }

    @Test
    void putCategoryWithUnknownCategoryIs422Problem() throws Exception {
        var id = UUID.randomUUID();
        given(transactions.assignCategory(eq(id), anyLong())).willThrow(new UnknownCategoryException(99));

        mvc.perform(put("/api/v1/transactions/{id}/category", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": 99}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/errors/unknown-category"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Category 99 does not exist"));
    }

    @Test
    void putCategoryOnUnknownTransactionIs404Problem() throws Exception {
        var id = UUID.randomUUID();
        given(transactions.assignCategory(eq(id), anyLong())).willThrow(new NotFoundException("nope"));

        mvc.perform(put("/api/v1/transactions/{id}/category", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": 3}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void putCategoryWithMissingOrNullCategoryIdIs400Problem() throws Exception {
        var id = UUID.randomUUID();
        for (String body : List.of("{}", "{\"categoryId\": null}")) {
            mvc.perform(put("/api/v1/transactions/{id}/category", id)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        }
        verifyNoInteractions(transactions);
    }

    @Test
    void putCategoryWithMalformedBodyIs400Problem() throws Exception {
        var id = UUID.randomUUID();
        for (String body : List.of("not json", "{\"categoryId\": \"abc\"}")) {
            mvc.perform(put("/api/v1/transactions/{id}/category", id)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        }
    }

    // ---- DELETE /transactions/{id} ----

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/transactions/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(transactions).delete(id);
    }

    @Test
    void deleteUnknownIdIs404Problem() throws Exception {
        var id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new NotFoundException("gone")).when(transactions).delete(id);

        mvc.perform(delete("/api/v1/transactions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    // ---- categories ----

    @Test
    void listCategories() throws Exception {
        given(categories.list()).willReturn(List.of(category(1, "Food"), category(2, "Rent")));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Food"));
    }

    @Test
    void createCategoryReturns201WithLocation() throws Exception {
        given(categories.create("Food")).willReturn(category(7, "Food"));

        mvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Food\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/categories/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Food"));
    }

    @Test
    void createCategoryWithBlankOrTooLongNameIs400Problem() throws Exception {
        for (String body : List.of("{\"name\": \"  \"}", "{}", "{\"name\": \"" + "x".repeat(101) + "\"}")) {
            mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        }
        verifyNoInteractions(categories);
    }

    @Test
    void createDuplicateCategoryIs409Problem() throws Exception {
        given(categories.create("Food")).willThrow(new DuplicateCategoryException("Food"));

        mvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Food\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/errors/duplicate-category"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
