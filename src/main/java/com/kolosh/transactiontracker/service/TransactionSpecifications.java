package com.kolosh.transactiontracker.service;

import java.util.ArrayList;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.kolosh.transactiontracker.domain.Transaction;

/** Turns a {@link TransactionFilter} into one SQL WHERE clause, adding only the filters that are set. */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<Transaction> from(TransactionFilter f) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bookedOn"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("bookedOn"), f.to())); // inclusive
            }
            if (f.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.uncategorized()) {
                predicates.add(cb.isNull(root.get("category")));
            }
            if (f.currency() != null) {
                predicates.add(cb.equal(root.get("currency"), f.currency()));
            }
            if (f.q() != null) {
                predicates.add(cb.like(cb.lower(root.get("description")), containsPattern(f.q()), '\\'));
            }
            if (f.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), f.minAmount()));
            }
            if (f.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), f.maxAmount()));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }


    static String containsPattern(String q) {
        String escaped = q.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
