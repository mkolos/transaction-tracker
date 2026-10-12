package com.kolosh.transactiontracker.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.kolosh.transactiontracker.domain.Category;
import com.kolosh.transactiontracker.domain.CategorySource;
import com.kolosh.transactiontracker.domain.Transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class Dtos {

    private Dtos() {
    }

    public record TransactionSummary(UUID id, LocalDate bookedOn, String amount, String currency,
                                     String description, CategoryRef category, CategorySource categorySource) {
        static TransactionSummary of(Transaction t) {
            return new TransactionSummary(t.getId(), t.getBookedOn(), t.getAmount().toPlainString(), t.getCurrency(),
                    t.getDescription(), CategoryRef.of(t.getCategory()), t.getCategorySource());
        }
    }

    public record TransactionDetail(UUID id, LocalDate bookedOn, String amount, String currency, String description,
                                    CategoryRef category, CategorySource categorySource, Long matchedRuleId,
                                    Instant createdAt, Instant updatedAt) {
        static TransactionDetail of(Transaction t) {
            return new TransactionDetail(t.getId(), t.getBookedOn(), t.getAmount().toPlainString(), t.getCurrency(),
                    t.getDescription(), CategoryRef.of(t.getCategory()), t.getCategorySource(), t.getMatchedRuleId(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record CategoryRef(Long id, String name) {
        static CategoryRef of(Category c) {
            return c == null ? null : new CategoryRef(c.getId(), c.getName());
        }
    }

    public record CategoryResponse(Long id, String name, Instant createdAt) {
        static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getCreatedAt());
        }
    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record AssignCategoryRequest(@NotNull Long categoryId) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }
}
